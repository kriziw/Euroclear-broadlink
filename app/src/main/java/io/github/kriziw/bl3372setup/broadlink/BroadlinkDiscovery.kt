package io.github.kriziw.bl3372setup.broadlink

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

data class DiscoveredDevice(
    val address: InetAddress,
    val deviceType: Int,
    val mac: String,
    val name: String,
    val isLocked: Boolean,
) {
    val isRunxinBl3372: Boolean get() = deviceType == BroadlinkPackets.DEVTYPE_RUNXIN_BL3372

    /** MAC as six bytes in display order, as needed for an authenticated session. */
    val macBytes: ByteArray get() = ByteArray(6) { mac.substring(3 * it, 3 * it + 2).toInt(16).toByte() }

    fun endpoint() = BroadlinkEndpoint(address, deviceType, macBytes)
}

/**
 * BroadLink LAN discovery, mirroring python-broadlink's `scan()`: send a hello packet every
 * [resendIntervalMillis] until [durationMillis] has elapsed and collect the unicast replies.
 *
 * Destinations can be broadcast addresses (same network only) or any number of unicast addresses,
 * which is how devices on another VLAN/subnet are reached: routers forward unicast but not
 * broadcast. Long destination lists are sent in paced batches of [batchSize].
 */
class BroadlinkDiscovery(
    private val sockets: SocketFactory,
    private val clock: () -> HelloTime = HelloTime::now,
    private val durationMillis: Long = 10_000,
    private val resendIntervalMillis: Long = 1_000,
    private val batchSize: Int = 32,
    private val batchPauseMillis: Long = 25,
) {
    /**
     * @param stopWhen ends the search early once a device matching it has been found.
     * @param onFound called (on an IO thread) once per newly seen device.
     * @throws java.io.IOException if a hello packet cannot be sent at all.
     */
    suspend fun discover(
        destinations: List<InetSocketAddress>,
        localAddress: Inet4Address?,
        stopWhen: (DiscoveredDevice) -> Boolean = { false },
        onFound: (DiscoveredDevice) -> Unit = {},
    ): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        require(destinations.isNotEmpty()) { "No destinations" }
        sockets.open().use { socket ->
            socket.broadcast = true
            val search = Search(socket, stopWhen, onFound)
            val start = System.nanoTime()
            fun elapsedMillis() = (System.nanoTime() - start) / 1_000_000L

            rounds@ while (elapsedMillis() < durationMillis) {
                val roundEnd = minOf(elapsedMillis() + resendIntervalMillis, durationMillis)
                val hello = BroadlinkPackets.buildHelloPacket(clock(), localAddress, socket.localPort)
                val batches = destinations.chunked(batchSize)
                for ((index, batch) in batches.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    socket.sendToAll(hello, batch)
                    if (index < batches.lastIndex) {
                        val pauseEnd = minOf(roundEnd, elapsedMillis() + batchPauseMillis)
                        if (search.receiveUntil { elapsedMillis() >= pauseEnd }) break@rounds
                    }
                }
                if (search.receiveUntil { elapsedMillis() >= roundEnd }) break
            }
            search.found.values.toList()
        }
    }

    private class Search(
        private val socket: DatagramSocket,
        private val stopWhen: (DiscoveredDevice) -> Boolean,
        private val onFound: (DiscoveredDevice) -> Unit,
    ) {
        val found = LinkedHashMap<String, DiscoveredDevice>()
        private val buffer = ByteArray(1024)

        /** Collects replies until [done]; returns true as soon as one satisfies stopWhen. */
        suspend fun receiveUntil(done: () -> Boolean): Boolean {
            while (!done()) {
                currentCoroutineContext().ensureActive()
                socket.soTimeout = POLL_MILLIS
                val datagram = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(datagram)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val response = BroadlinkPackets.parseHelloResponse(datagram.data, datagram.length) ?: continue
                // One entry per device: the same MAC can answer from several addresses (NAT, multi-homing).
                val key = "${response.mac}/${response.deviceType}"
                if (key in found) continue
                val device = DiscoveredDevice(
                    address = datagram.address,
                    deviceType = response.deviceType,
                    mac = response.mac,
                    name = response.name,
                    isLocked = response.isLocked,
                )
                found[key] = device
                onFound(device)
                if (stopWhen(device)) return true
            }
            return false
        }

        private companion object {
            const val POLL_MILLIS = 25
        }
    }
}
