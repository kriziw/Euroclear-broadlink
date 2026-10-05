package io.github.kriziw.bl3372setup.appliance.syr

import io.github.kriziw.bl3372setup.network.LocalHttp
import kotlinx.coroutines.delay
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Joins a SYR NeoSoft Connect to a Wi-Fi network through its local API while the phone is on the
 * softener's own access point, as SYR documents it: `set/wfk/<key>` first, then `set/wfc/<ssid>`
 * (which starts the connection), then `get/wfs` until 2 (connected) and `get/wip` for its address.
 *
 * The Wi-Fi name and password go only to the softener, over its access point. They are never
 * stored or logged.
 */
class SyrWifiProvisioner(
    private val http: LocalHttp,
    private val host: String,
    private val port: Int = 5333,
    private val pollMillis: Long = 2_000,
    private val timeoutMillis: Long = 60_000,
) {
    sealed interface Result {
        /** The softener reports a connection; [address] is its address on that network, if it said. */
        data class Connected(val address: String?) : Result

        /** The settings were accepted but no connection was reported in time (wrong password?). */
        data object NotConnected : Result

        /** The softener's access point went away after the settings were sent, as when it switches networks. */
        data object LinkLost : Result
    }

    /** Sends the settings once each; throws if the softener rejects them. */
    suspend fun provision(ssid: String, password: String): Result {
        set("wfk", password)
        set("wfc", ssid)
        var waited = 0L
        while (waited < timeoutMillis) {
            delay(pollMillis)
            waited += pollMillis
            val status = try {
                get("wfs")
            } catch (_: IOException) {
                return Result.LinkLost
            }
            if (status?.toIntOrNull() == CONNECTED) {
                val ip = try { get("wip") } catch (_: IOException) { null }
                return Result.Connected(ip?.takeIf { it.isNotBlank() && it != "0.0.0.0" })
            }
        }
        return Result.NotConnected
    }

    private suspend fun set(key: String, value: String) {
        val response = http.get(host, port, "${SyrNeoSoftProtocol.BASE}/set/$key/${pathValue(value)}")
        if (response.status != 200 || !SyrNeoSoftProtocol.accepted(response.body)) {
            throw IOException("SYR rejected $key")
        }
    }

    private suspend fun get(key: String): String? {
        val response = http.get(host, port, "${SyrNeoSoftProtocol.BASE}/get/$key")
        if (response.status != 200) throw IOException("SYR HTTP ${response.status}")
        return try {
            val json = JSONObject(response.body)
            json.keys().asSequence().firstOrNull { it.equals("get$key", ignoreCase = true) }?.let { json.get(it).toString() }
        } catch (_: JSONException) {
            null
        }
    }

    companion object {
        private const val CONNECTED = 2

        /**
         * SYR firmware takes values literally in the path and some versions reject percent-encoding
         * (`02:30`, not `02%3A30`). Only what cannot appear in a request path is encoded.
         */
        fun pathValue(value: String): String = buildString {
            value.toByteArray(Charsets.UTF_8).forEach { byte ->
                val b = byte.toInt() and 0xFF
                if (b <= 0x20 || b >= 0x7F || b.toChar() in "%/?#") append("%%%02X".format(b)) else append(b.toChar())
            }
        }

        /** True when [value] needs encoding, which older firmware may not accept. */
        fun needsEncoding(value: String): Boolean = pathValue(value) != value
    }
}
