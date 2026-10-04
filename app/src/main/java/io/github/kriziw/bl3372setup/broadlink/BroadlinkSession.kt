package io.github.kriziw.bl3372setup.broadlink

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlin.random.Random

/** Where a BroadLink device lives and how it identified itself in its hello reply. */
data class BroadlinkEndpoint(
    val address: InetAddress,
    val deviceType: Int,
    val mac: ByteArray,
    val port: Int = BroadlinkPackets.PORT,
) {
    override fun equals(other: Any?) = other is BroadlinkEndpoint && address == other.address &&
        deviceType == other.deviceType && mac.contentEquals(other.mac) && port == other.port

    override fun hashCode() = 31 * address.hashCode() + mac.contentHashCode()
}

/**
 * An authenticated BroadLink session, equivalent to python-broadlink's `Device.auth()` followed by
 * `send_packet()` calls. One request is in flight at a time.
 */
class BroadlinkSession(
    private val sockets: SocketFactory,
    val endpoint: BroadlinkEndpoint,
    private val timeoutMillis: Long = 5_000,
    private val retryIntervalMillis: Long = 1_000,
    random: Random = Random.Default,
) {
    private val lock = Mutex()
    private var count = random.nextInt(0x8000, 0x10000)
    private var sessionId = 0
    private var key = BroadlinkCommand.INITIAL_KEY

    var isAuthenticated = false
        private set

    /** Logs in with the default key and switches to the per-session key the device returns. */
    suspend fun authenticate() = lock.withLock {
        sessionId = 0
        key = BroadlinkCommand.INITIAL_KEY
        isAuthenticated = false
        val reply = exchange(BroadlinkCommand.TYPE_AUTH, BroadlinkCommand.authPayload(), retransmit = true)
        val payload = reply.payload
        if (payload.size < 0x14) throw BroadlinkException(BroadlinkException.MALFORMED, "short auth reply")
        sessionId = (payload[0].toInt() and 0xFF) or ((payload[1].toInt() and 0xFF) shl 8) or
            ((payload[2].toInt() and 0xFF) shl 16) or ((payload[3].toInt() and 0xFF) shl 24)
        key = payload.copyOfRange(0x04, 0x14)
        isAuthenticated = true
    }

    /**
     * Sends one 0x6A command and returns the decrypted reply payload.
     *
     * @param retransmit resend the identical packet every [retryIntervalMillis] until a reply arrives
     *   (python-broadlink behaviour, fine for reads). False sends exactly once, for commands that
     *   must not be duplicated.
     */
    suspend fun command(payload: ByteArray, retransmit: Boolean): ByteArray = lock.withLock {
        check(isAuthenticated) { "Not authenticated" }
        exchange(BroadlinkCommand.TYPE_COMMAND, payload, retransmit).payload
    }

    fun invalidate() {
        isAuthenticated = false
    }

    private suspend fun exchange(packetType: Int, payload: ByteArray, retransmit: Boolean): BroadlinkReply =
        withContext(Dispatchers.IO) {
            count = BroadlinkCommand.nextCount(count)
            val packet = BroadlinkCommand.buildPacket(
                packetType, count, endpoint.deviceType, endpoint.mac, sessionId, payload, key,
            )
            val destination = InetSocketAddress(endpoint.address, endpoint.port)
            val buffer = ByteArray(2048)
            sockets.open().use { socket ->
                val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
                var nextSend = System.nanoTime()
                var sent = false
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val now = System.nanoTime()
                    if (now >= deadline) throw SocketTimeoutException("No reply within $timeoutMillis ms")
                    if (!sent || (retransmit && now >= nextSend)) {
                        socket.send(DatagramPacket(packet, packet.size, destination))
                        sent = true
                        nextSend = now + retryIntervalMillis * 1_000_000L
                    }
                    val waitUntil = if (retransmit) minOf(nextSend, deadline) else deadline
                    socket.soTimeout = ((waitUntil - System.nanoTime()) / 1_000_000L).coerceAtLeast(1).toInt()
                    val datagram = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(datagram)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    if (datagram.address != endpoint.address) continue
                    if (!BroadlinkCommand.isWellFormedReply(datagram.data, datagram.length)) continue
                    return@withContext BroadlinkCommand.parseReply(datagram.data, datagram.length, key)
                }
                @Suppress("UNREACHABLE_CODE")
                error("unreachable")
            }
        }
}
