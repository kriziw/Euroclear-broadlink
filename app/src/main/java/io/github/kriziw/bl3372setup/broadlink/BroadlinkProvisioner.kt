package io.github.kriziw.bl3372setup.broadlink

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/**
 * Transmits an already-built setup packet and waits for the optional 0x15 acknowledgement.
 *
 * python-broadlink sends the packet exactly once and never listens. Here the packet is sent up to
 * [maxAttempts] times, [ackTimeoutMillis] apart, stopping early on an acknowledgement or when the
 * link disappears (the module drops its AP once it accepts the credentials). An unacknowledged
 * send is reported as such, not as a failure.
 */
class BroadlinkProvisioner(
    private val sockets: SocketFactory,
    private val maxAttempts: Int = 3,
    private val ackTimeoutMillis: Int = 2_000,
) {
    sealed interface Outcome {
        /** Number of rounds that were transmitted to at least one destination. */
        val roundsSent: Int

        data class Acknowledged(val from: InetAddress, override val roundsSent: Int) : Outcome
        data class NotAcknowledged(override val roundsSent: Int) : Outcome

        /** A later round could not be sent: typically the module rebooted and its AP vanished. */
        data class LinkLostAfterSend(override val roundsSent: Int) : Outcome
    }

    /**
     * @param onRoundSent called (on an IO thread) after each transmitted round.
     * @throws IOException if not even the first round could be sent.
     */
    suspend fun send(
        packet: ByteArray,
        destinations: List<InetSocketAddress>,
        onRoundSent: (round: Int, maxRounds: Int) -> Unit = { _, _ -> },
    ): Outcome = withContext(Dispatchers.IO) {
        require(destinations.isNotEmpty()) { "No destinations" }
        sockets.open().use { socket ->
            socket.broadcast = true
            val buffer = ByteArray(512)
            var roundsSent = 0
            for (round in 1..maxAttempts) {
                currentCoroutineContext().ensureActive()
                try {
                    socket.sendToAll(packet, destinations)
                    roundsSent = round
                    onRoundSent(round, maxAttempts)
                    awaitAck(socket, buffer)?.let { return@withContext Outcome.Acknowledged(it, round) }
                } catch (e: IOException) {
                    if (roundsSent == 0) throw e
                    return@withContext Outcome.LinkLostAfterSend(roundsSent)
                }
            }
            Outcome.NotAcknowledged(roundsSent)
        }
    }

    private suspend fun awaitAck(socket: DatagramSocket, buffer: ByteArray): InetAddress? {
        val deadline = System.nanoTime() + ackTimeoutMillis * 1_000_000L
        while (true) {
            currentCoroutineContext().ensureActive()
            val remainingMillis = (deadline - System.nanoTime()) / 1_000_000L
            if (remainingMillis <= 0) return null
            socket.soTimeout = remainingMillis.toInt()
            val datagram = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(datagram)
            } catch (_: SocketTimeoutException) {
                return null
            }
            if (BroadlinkPackets.isSetupAck(datagram.data, datagram.length)) return datagram.address
        }
    }
}
