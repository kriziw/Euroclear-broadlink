package io.github.kriziw.bl3372setup.network

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/** One request as the fake server saw it. */
data class FakeRequest(val method: String, val path: String, val headers: Map<String, String>, val body: String)

/**
 * A loopback HTTP server for driver tests. [handler] returns the complete raw response, so tests
 * can exercise Content-Length, chunked and read-to-end bodies.
 */
class FakeHttpServer(private val handler: (FakeRequest) -> String) : Closeable {
    private val server = ServerSocket(0, 16, InetAddress.getLoopbackAddress())
    val port: Int get() = server.localPort
    val requests = CopyOnWriteArrayList<FakeRequest>()

    /** Connects to this server whatever host name the client asks for. */
    val connector = TcpConnector { _, port, timeout ->
        Socket().apply { connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), timeout) }
    }

    init {
        thread(isDaemon = true, name = "fake-http") {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                socket.use { serve(it) }
            }
        }
    }

    private fun serve(socket: Socket) {
        val input = BufferedInputStream(socket.getInputStream())
        val requestLine = readLine(input) ?: return
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            headers[line.substringBefore(':').trim().lowercase()] = line.substringAfter(':').trim()
        }
        val length = headers["content-length"]?.toInt() ?: 0
        val body = ByteArray(length).also { var read = 0; while (read < length) read += input.read(it, read, length - read) }
        val (method, path) = requestLine.split(' ').let { it[0] to it[1] }
        val request = FakeRequest(method, path, headers, String(body, Charsets.UTF_8))
        requests += request
        socket.getOutputStream().apply {
            write(handler(request).toByteArray(Charsets.UTF_8))
            flush()
        }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (out.size() == 0) null else out.toString(Charsets.UTF_8.name())
            if (b == '\n'.code) return out.toString(Charsets.UTF_8.name()).trimEnd('\r')
            out.write(b)
        }
    }

    override fun close() = server.close()

    companion object {
        fun ok(body: String, status: String = "200 OK") =
            "HTTP/1.1 $status\r\nContent-Length: ${body.toByteArray(Charsets.UTF_8).size}\r\nConnection: close\r\n\r\n$body"

        fun chunked(vararg chunks: String) = buildString {
            append("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n")
            chunks.forEach { append(Integer.toHexString(it.toByteArray(Charsets.UTF_8).size)).append("\r\n").append(it).append("\r\n") }
            append("0\r\n\r\n")
        }

        /** No length header: the body ends when the server closes the connection. */
        fun untilClose(body: String) = "HTTP/1.0 200 OK\r\n\r\n$body"
    }
}
