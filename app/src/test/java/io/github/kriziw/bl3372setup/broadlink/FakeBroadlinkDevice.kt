package io.github.kriziw.bl3372setup.broadlink

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A stand-in for a BroadLink module listening on a loopback UDP port. It records every datagram
 * and answers with whatever [reply] returns (nothing for null).
 */
class FakeBroadlinkDevice(
    private val reply: (request: ByteArray) -> List<ByteArray> = { emptyList() },
) : AutoCloseable {
    private val socket = DatagramSocket(0, InetAddress.getLoopbackAddress())
    val received = CopyOnWriteArrayList<Pair<ByteArray, InetSocketAddress>>()
    val address = InetSocketAddress(InetAddress.getLoopbackAddress(), socket.localPort)

    private val thread = Thread {
        val buffer = ByteArray(2048)
        while (!socket.isClosed) {
            val datagram = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(datagram)
            } catch (_: SocketException) {
                return@Thread
            }
            val data = datagram.data.copyOf(datagram.length)
            val sender = datagram.socketAddress as InetSocketAddress
            received += data to sender
            for (response in reply(data)) {
                socket.send(DatagramPacket(response, response.size, sender))
            }
        }
    }.apply {
        isDaemon = true
        start()
    }

    override fun close() {
        socket.close()
        thread.join(1000)
    }

    companion object {
        /** The 48-byte join acknowledgement documented by waringer/broadlink. */
        val ACK: ByteArray = hex("0000000000000000000000000000000000000000000000000000000000000000c4be0000000015000000000000000000")

        fun helloResponse(deviceType: Int, macBytesReversed: ByteArray, name: String): ByteArray {
            val response = ByteArray(0x88)
            response[0x26] = 0x07
            response[0x34] = deviceType.toByte()
            response[0x35] = (deviceType shr 8).toByte()
            macBytesReversed.copyInto(response, 0x3A)
            name.toByteArray(Charsets.UTF_8).copyInto(response, 0x40)
            return response
        }
    }
}

/** Loopback sockets: the JVM stand-in for `Network.bindSocket` on Android. */
val loopbackSockets = SocketFactory { DatagramSocket(0, InetAddress.getLoopbackAddress()) }
