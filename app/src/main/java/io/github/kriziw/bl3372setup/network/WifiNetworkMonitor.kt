package io.github.kriziw.bl3372setup.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import io.github.kriziw.bl3372setup.broadlink.Ipv4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address

/** Prefixes of known BroadLink provisioning access points. */
private val SETUP_AP_PREFIXES = listOf("WiFi-BL", "BroadlinkProv", "BroadLink_")

fun isSetupApSsid(ssid: String): Boolean =
    SETUP_AP_PREFIXES.any { ssid.startsWith(it, ignoreCase = true) }

/** A snapshot of one connected Wi-Fi network. */
data class WifiLink(
    val network: Network,
    /** SSID without quotes, or null when Android withholds it (no precise location / Location off). */
    val ssid: String?,
    /** True if Android validated Internet access on this network (a BroadLink AP never has it). */
    val hasValidatedInternet: Boolean,
    val address: Inet4Address?,
    val prefixLength: Int,
    val gateway: Inet4Address?,
) {
    val subnetBroadcast: Inet4Address? get() = address?.let { Ipv4.broadcastAddress(it, prefixLength) }
    val isSetupAp: Boolean get() = ssid?.let(::isSetupApSsid) == true
}

/**
 * Tracks connected Wi-Fi networks through [ConnectivityManager.registerNetworkCallback].
 *
 * Networks without Internet still match because `NET_CAPABILITY_INTERNET` is removed from the
 * request. The result is exposed as [link], the Wi-Fi network the app should use.
 */
class WifiNetworkMonitor(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)

    private class Entry(val order: Long) {
        var capabilities: NetworkCapabilities? = null
        var linkProperties: LinkProperties? = null
    }

    private val lock = Any()
    private val entries = HashMap<Network, Entry>()
    private var nextOrder = 0L
    private var callback: ConnectivityManager.NetworkCallback? = null

    private val _link = MutableStateFlow<WifiLink?>(null)
    val link: StateFlow<WifiLink?> = _link.asStateFlow()

    fun start() {
        synchronized(lock) { if (callback != null) return }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val cb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Without this flag Android 12+ redacts the SSID even when location is granted.
            Callback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO)
        } else {
            Callback()
        }
        synchronized(lock) { callback = cb }
        connectivity.registerNetworkCallback(request, cb)
    }

    fun stop() {
        val cb = synchronized(lock) {
            val current = callback
            callback = null
            entries.clear()
            _link.value = null
            current
        }
        cb?.let { connectivity.unregisterNetworkCallback(it) }
    }

    /** Re-evaluates the current link, e.g. after location permission or the Location toggle changed. */
    fun refresh() {
        synchronized(lock) { publish() }
    }

    private inner class Callback : ConnectivityManager.NetworkCallback {
        constructor() : super()

        @RequiresApi(Build.VERSION_CODES.S)
        constructor(flags: Int) : super(flags)

        override fun onAvailable(network: Network) = update(network) {}

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            update(network) { it.capabilities = capabilities }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) =
            update(network) { it.linkProperties = linkProperties }

        override fun onLost(network: Network) = synchronized(lock) {
            entries.remove(network)
            publish()
        }
    }

    private inline fun update(network: Network, change: (Entry) -> Unit) = synchronized(lock) {
        change(entries.getOrPut(network) { Entry(nextOrder++) })
        publish()
    }

    private fun publish() {
        val links = entries.entries
            .filter { it.value.capabilities != null }
            .sortedByDescending { it.value.order }
            .map { (network, entry) -> toLink(network, entry, singleWifi = entries.size == 1) }
        // Prefer a recognised provisioning AP, otherwise the most recently connected network.
        _link.value = links.firstOrNull { it.isSetupAp } ?: links.firstOrNull()
    }

    private fun toLink(network: Network, entry: Entry, singleWifi: Boolean): WifiLink {
        val caps = entry.capabilities!!
        val lp = entry.linkProperties
        val ipv4 = lp?.linkAddresses?.firstOrNull { it.address is Inet4Address }
        val gateway = lp?.routes
            ?.firstOrNull { it.isDefaultRoute && it.hasGateway() && it.gateway is Inet4Address }
            ?.gateway as Inet4Address?
        return WifiLink(
            network = network,
            ssid = readSsid(caps, singleWifi),
            hasValidatedInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            address = ipv4?.address as Inet4Address?,
            prefixLength = ipv4?.prefixLength ?: 32,
            gateway = gateway,
        )
    }

    private fun readSsid(caps: NetworkCapabilities, singleWifi: Boolean): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            normalizeSsid((caps.transportInfo as? WifiInfo)?.ssid)?.let { return it }
        }
        // Android 10/11, or location permission granted after the callback was registered.
        // getConnectionInfo() describes the primary Wi-Fi only, so use it only if there is one.
        if (!singleWifi) return null
        @Suppress("DEPRECATION")
        return normalizeSsid(wifiManager.connectionInfo?.ssid)
    }

    private fun normalizeSsid(raw: String?): String? {
        if (raw.isNullOrEmpty() || raw == WifiManager.UNKNOWN_SSID) return null
        return if (raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"')) {
            raw.substring(1, raw.length - 1)
        } else {
            raw // Not valid UTF-8: Android returns hex digits.
        }
    }
}
