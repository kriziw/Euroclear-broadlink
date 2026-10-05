package io.github.kriziw.bl3372setup.network

import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64

/** Opens a TCP connection; the Android implementation binds it to the Wi-Fi network. */
fun interface TcpConnector {
    fun connect(host: String, port: Int, timeoutMillis: Int): Socket
}

/** TCP sockets on one specific [Network], resolving names with that network's DNS (see [NetworkBoundSockets]). */
class NetworkTcpConnector(private val network: Network) : TcpConnector {
    override fun connect(host: String, port: Int, timeoutMillis: Int): Socket {
        val addresses = network.getAllByName(host)
        val address = addresses.firstOrNull { it is Inet4Address } ?: addresses.first()
        val socket = network.socketFactory.createSocket()
        try {
            socket.connect(InetSocketAddress(address, port), timeoutMillis)
        } catch (e: IOException) {
            socket.close()
            throw e
        }
        return socket
    }
}

data class HttpResponse(val status: Int, val body: String)

/** HTTP 401/403, or a device-specific "wrong code" reply: the stored login is not accepted. */
class LoginRejectedException(message: String) : IOException(message)

/**
 * A minimal HTTP/1.1 client for appliances on the local network. Appliance web servers only speak
 * plain HTTP; requests never leave the LAN. One request per connection (`Connection: close`),
 * with Content-Length, chunked and read-to-end bodies.
 */
class LocalHttp(
    private val connector: TcpConnector,
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 15_000,
) {
    suspend fun get(host: String, port: Int, path: String, auth: BasicAuth? = null): HttpResponse =
        request("GET", host, port, path, auth, body = null, contentType = null)

    suspend fun post(host: String, port: Int, path: String, body: String, contentType: String, auth: BasicAuth? = null): HttpResponse =
        request("POST", host, port, path, auth, body, contentType)

    private suspend fun request(
        method: String,
        host: String,
        port: Int,
        path: String,
        auth: BasicAuth?,
        body: String?,
        contentType: String?,
    ): HttpResponse = withContext(Dispatchers.IO) {
        connector.connect(host, port, connectTimeoutMillis).use { socket ->
            socket.soTimeout = readTimeoutMillis
            val payload = body?.toByteArray(Charsets.UTF_8)
            val head = buildString {
                append("$method $path HTTP/1.1\r\n")
                append("Host: ").append(if (port == 80) host else "$host:$port").append("\r\n")
                append("User-Agent: WaterCare\r\n")
                append("Accept: */*\r\n")
                append("Connection: close\r\n")
                auth?.let { append("Authorization: ").append(it.header).append("\r\n") }
                if (payload != null) {
                    append("Content-Type: ").append(contentType).append("\r\n")
                    append("Content-Length: ").append(payload.size).append("\r\n")
                }
                append("\r\n")
            }
            socket.getOutputStream().apply {
                write(head.toByteArray(Charsets.ISO_8859_1))
                payload?.let(::write)
                flush()
            }
            readResponse(socket.getInputStream())
        }
    }

    data class BasicAuth(val user: String, val password: String) {
        val header: String
            get() = "Basic " + Base64.getEncoder().encodeToString("$user:$password".toByteArray(Charsets.UTF_8))

        override fun toString() = "BasicAuth(user=$user, password=***)"
    }

    internal companion object {
        fun readResponse(input: InputStream): HttpResponse {
            val statusLine = readLine(input) ?: throw IOException("Empty HTTP response")
            val status = statusLine.split(' ').getOrNull(1)?.toIntOrNull()
                ?: throw IOException("Malformed HTTP status line")
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
            val bytes = when {
                headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true -> readChunked(input)
                headers["content-length"]?.toIntOrNull() != null -> readExactly(input, headers.getValue("content-length").toInt())
                else -> input.readBytes()
            }
            return HttpResponse(status, String(bytes, Charsets.UTF_8))
        }

        private fun readLine(input: InputStream): String? {
            val line = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b == -1) return if (line.size() == 0) null else line.toString(Charsets.ISO_8859_1.name())
                if (b == '\n'.code) return line.toString(Charsets.ISO_8859_1.name()).trimEnd('\r')
                line.write(b)
                if (line.size() > MAX_LINE) throw IOException("HTTP header line too long")
            }
        }

        private fun readExactly(input: InputStream, length: Int): ByteArray {
            if (length > MAX_BODY) throw IOException("HTTP body too large")
            val out = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(out, read, length - read)
                if (n == -1) break // Embedded servers sometimes overstate the length.
                read += n
            }
            return out.copyOf(read)
        }

        private fun readChunked(input: InputStream): ByteArray {
            val out = ByteArrayOutputStream()
            while (true) {
                val sizeLine = readLine(input) ?: break
                val size = sizeLine.substringBefore(';').trim().toIntOrNull(16) ?: throw IOException("Malformed chunk size")
                if (size == 0) break
                out.write(readExactly(input, size))
                readLine(input) // CRLF after each chunk
                if (out.size() > MAX_BODY) throw IOException("HTTP body too large")
            }
            return out.toByteArray()
        }

        private const val MAX_LINE = 8 * 1024
        private const val MAX_BODY = 1024 * 1024
    }
}
