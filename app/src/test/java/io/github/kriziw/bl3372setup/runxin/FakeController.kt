package io.github.kriziw.bl3372setup.runxin

import io.github.kriziw.bl3372setup.broadlink.BroadlinkCommand
import io.github.kriziw.bl3372setup.broadlink.BroadlinkEndpoint
import io.github.kriziw.bl3372setup.broadlink.BroadlinkPackets
import io.github.kriziw.bl3372setup.broadlink.hex
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A simulated BL3372 + Runxin F79D on a loopback UDP port: it authenticates, decrypts 0x6A
 * commands, answers 0x09 queries from [fields] and applies 0x19 writes to them.
 */
class FakeController(initialFields: Map<Int, Pair<Int, Int>>) : AutoCloseable {
    val fields = ConcurrentHashMap(initialFields)
    val mac = hex("1cd1d7123456")
    private val socket = DatagramSocket(0, InetAddress.getLoopbackAddress())
    val endpoint = BroadlinkEndpoint(InetAddress.getLoopbackAddress(), 0x520F, mac, socket.localPort)

    private val sessionKey = ByteArray(16) { (0xA0 + it).toByte() }
    val authCount = AtomicInteger()
    val writesReceived = AtomicInteger()

    /** Reply to the next N commands with this BroadLink error code instead. */
    @Volatile var failNextCommandsWith: Pair<Int, Int>? = null
    /** Apply writes but never acknowledge them (lost reply). */
    @Volatile var dropWriteReplies = false
    /** Acknowledge writes without applying them (controller ignores the field). */
    @Volatile var ignoreWrites = false
    /** Fields to change when a write to field 34 = 1 arrives (valve starts moving). */
    @Volatile var onRegenerate: Map<Int, Pair<Int, Int>> = mapOf(34 to (1 to 0))

    private val thread = Thread {
        val buffer = ByteArray(2048)
        while (!socket.isClosed) {
            val datagram = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(datagram)
            } catch (_: SocketException) {
                return@Thread
            }
            handle(datagram.data.copyOf(datagram.length))?.let {
                socket.send(DatagramPacket(it, it.size, datagram.socketAddress))
            }
        }
    }.apply { isDaemon = true; start() }

    private fun handle(packet: ByteArray): ByteArray? {
        val type = (packet[0x26].toInt() and 0xFF) or ((packet[0x27].toInt() and 0xFF) shl 8)
        val body = packet.copyOfRange(0x38, packet.size)
        if (type == BroadlinkCommand.TYPE_AUTH) {
            authCount.incrementAndGet()
            aes(Cipher.DECRYPT_MODE, BroadlinkCommand.INITIAL_KEY, body)
            val reply = byteArrayOf(0x44, 0x33, 0x22, 0x11) + sessionKey + ByteArray(12)
            return reply(0x3E9, 0, reply, BroadlinkCommand.INITIAL_KEY)
        }
        val session = (packet[0x30].toInt() and 0xFF) or ((packet[0x31].toInt() and 0xFF) shl 8)
        if (session != 0x3344) return reply(0x3EE, -7, ByteArray(16), sessionKey)
        failNextCommandsWith?.let { (code, left) ->
            failNextCommandsWith = if (left > 1) code to left - 1 else null
            return reply(0x3EE, code, ByteArray(16), sessionKey)
        }

        val plain = aes(Cipher.DECRYPT_MODE, sessionKey, body)
        val frame = Bl3372Transport.unpackTfb(plain)
        val inner = frame.copyOfRange(17, frame.size - 2).map { it.toInt() and 0xFF }
        val payload = inner.subList(4, inner.size - 2)
        val response = when (inner[3]) {
            RunxinFrames.QUERY -> RunxinFrames.build(
                RunxinFrames.QUERY_RESPONSE,
                payload.flatMap { id -> fields[id]?.let { listOf(id, it.first, it.second) } ?: emptyList() },
            )
            RunxinFrames.WRITE -> {
                writesReceived.incrementAndGet()
                if (!ignoreWrites) {
                    for ((id, a, b) in payload.chunked(3)) {
                        if (id == 34 && a == 1) fields.putAll(onRegenerate) else fields[id] = a to b
                    }
                }
                if (dropWriteReplies) return null
                RunxinFrames.build(RunxinFrames.WRITE_RESPONSE, payload)
            }
            else -> return null
        }
        val tfb = byteArrayOf(response.size.toByte(), (response.size shr 8).toByte()) + response
        return reply(0x3EE, 0, tfb, sessionKey)
    }

    private fun reply(type: Int, error: Int, plain: ByteArray, key: ByteArray): ByteArray {
        val padded = plain.copyOf(plain.size + (16 - plain.size % 16) % 16)
        val packet = ByteArray(0x38) + aes(Cipher.ENCRYPT_MODE, key, padded)
        hex("5aa5aa555aa5aa55").copyInto(packet)
        packet[0x22] = error.toByte()
        packet[0x23] = (error shr 8).toByte()
        packet[0x24] = 0x0F
        packet[0x25] = 0x52
        packet[0x26] = type.toByte()
        packet[0x27] = (type shr 8).toByte()
        val sum = BroadlinkPackets.checksum(packet)
        packet[0x20] = sum.toByte()
        packet[0x21] = (sum shr 8).toByte()
        return packet
    }

    private fun aes(mode: Int, key: ByteArray, data: ByteArray): ByteArray =
        Cipher.getInstance("AES/CBC/NoPadding").run {
            init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(hex("562e17996d093d28ddb3ba695a2e6f58")))
            doFinal(data)
        }

    override fun close() {
        socket.close()
        thread.join(1000)
    }
}
