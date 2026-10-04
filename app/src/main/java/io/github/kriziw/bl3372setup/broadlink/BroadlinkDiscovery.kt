package io.github.kriziw.bl3372setup.broadlink

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
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
}

/**
 * BroadLink LAN discovery, mirroring python-broadlink's `scan()`: broadcast a hello packet every
 * [resendIntervalMillis] until [durationMillis] has elapsed and collect the unicast replies.
 */
class BroadlinkDiscovery(
    private val sockets: SocketFactory,
    private val clock: () -> HelloTime = HelloTime::now,
    private val durationMillis: Long = 10_000,
    private val resendIntervalMillis: Long = 1_000,
) {
    /**
     * @param onFound called (on an IO thread) once per newly seen device.
     * @throws java.io.IOException if a hello packet cannot be sent at all.
     */
    suspend fun discover(
        destinations: List<InetSocketAddress>,
        localAddress: Inet4Address?,
        onFound: (DiscoveredDevice) -> Unit = {},
    ): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        require(destinations.isNotEmpty()) { "No destinations" }
        sockets.open().use { socket ->
            socket.broadcast = true
            val found = LinkedHashMap<String, DiscoveredDevice>()
            val buffer = ByteArray(1024)
            val start = System.nanoTime()
            fun elapsedMillis() = (System.nanoTime() - start) / 1_000_000L

            while (elapsedMillis() < durationMillis) {
                currentCoroutineContext().ensureActive()
                val hello = BroadlinkPackets.buildHelloPacket(clock(), localAddress, socket.localPort)
                socket.sendToAll(hello, destinations)

                val windowEnd = minOf(elapsedMillis() + resendIntervalMillis, durationMillis)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val remaining = windowEnd - elapsedMillis()
                    if (remaining <= 0) break
                    socket.soTimeout = remaining.toInt()
                    val datagram = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(datagram)
                    } catch (_: SocketTimeoutException) {
                        break
                    }
                    val response = BroadlinkPackets.parseHelloResponse(datagram.data, datagram.length)
                        ?: continue
                    val key = "${datagram.address.hostAddress}/${response.mac}/${response.deviceType}"
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
                }
            }
            found.values.toList()
        }
    }
}
