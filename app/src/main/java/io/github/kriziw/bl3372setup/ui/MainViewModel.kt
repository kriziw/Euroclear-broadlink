package io.github.kriziw.bl3372setup.ui

import android.app.Application
import android.net.Network
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.kriziw.bl3372setup.Permissions
import io.github.kriziw.bl3372setup.broadlink.BroadlinkDiscovery
import io.github.kriziw.bl3372setup.broadlink.BroadlinkPackets
import io.github.kriziw.bl3372setup.broadlink.BroadlinkProvisioner
import io.github.kriziw.bl3372setup.broadlink.CredentialProblem
import io.github.kriziw.bl3372setup.broadlink.DiscoveredDevice
import io.github.kriziw.bl3372setup.broadlink.Ipv4
import io.github.kriziw.bl3372setup.broadlink.SecurityMode
import io.github.kriziw.bl3372setup.network.NetworkBoundSockets
import io.github.kriziw.bl3372setup.network.WifiLink
import io.github.kriziw.bl3372setup.network.WifiNetworkMonitor
import io.github.kriziw.bl3372setup.network.isSetupApSsid
import io.github.kriziw.bl3372setup.network.withMulticastLock
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * The target network's credentials. Held only in memory (never in saved state, never persisted);
 * [toString] is redacted so the password cannot end up in a log or crash report by accident.
 */
data class CredentialsForm(
    val ssid: String = "",
    val password: String = "",
    val security: SecurityMode = SecurityMode.WPA2,
) {
    val problem: CredentialProblem? get() = BroadlinkPackets.validate(ssid, password, security)
    val isDeviceApName: Boolean get() = isSetupApSsid(ssid.trim())

    override fun toString() = "CredentialsForm(ssid=<redacted>, password=<redacted>, security=$security)"
}

/** Whether the phone is verifiably on the module's provisioning access point. */
enum class ApCheck {
    NO_WIFI,
    CONFIRMED_BY_NAME,
    WRONG_NETWORK_NAME,
    WRONG_NETWORK_HAS_INTERNET,
    NEEDS_MANUAL_CONFIRMATION,
    CONFIRMED_MANUALLY;

    val isConfirmed: Boolean get() = this == CONFIRMED_BY_NAME || this == CONFIRMED_MANUALLY
}

sealed interface NetworkError {
    /** EPERM/EACCES: Android 17 local-network permission missing, or a VPN blocking LAN access. */
    data object LocalNetworkBlocked : NetworkError
    data object NotConnected : NetworkError
    data class Other(val detail: String) : NetworkError
}

sealed interface ApWatch {
    data object Watching : ApWatch
    data class Gone(val afterSeconds: Long) : ApWatch
    data object StillConnected : ApWatch
}

sealed interface ProvisioningState {
    data object Idle : ProvisioningState
    data class Sending(val round: Int, val maxRounds: Int) : ProvisioningState
    data class Sent(
        val outcome: BroadlinkProvisioner.Outcome,
        val targetSsid: String,
        val apWatch: ApWatch,
    ) : ProvisioningState

    data class Failed(val error: NetworkError) : ProvisioningState
}

sealed interface DiscoveryState {
    data object Idle : DiscoveryState
    data class Searching(val found: List<DiscoveredDevice>, val seconds: Int) : DiscoveryState
    data class Done(val found: List<DiscoveredDevice>) : DiscoveryState
    data class Failed(val error: NetworkError) : DiscoveryState
}

data class UiState(
    val link: WifiLink? = null,
    val preciseLocationGranted: Boolean = false,
    val locationEnabled: Boolean = true,
    val localNetworkGranted: Boolean = true,
    /** Set after the user declined the local-network permission; offers the app settings. */
    val localNetworkDenied: Boolean = false,
    /** The network the user manually confirmed as WiFi-BL3372 (when the SSID is hidden). */
    val manuallyConfirmedNetwork: Network? = null,
    val form: CredentialsForm = CredentialsForm(),
    val provisioning: ProvisioningState = ProvisioningState.Idle,
    val discovery: DiscoveryState = DiscoveryState.Idle,
) {
    val apCheck: ApCheck
        get() {
            val link = link ?: return ApCheck.NO_WIFI
            val ssid = link.ssid
            return when {
                ssid != null && isSetupApSsid(ssid) -> ApCheck.CONFIRMED_BY_NAME
                ssid != null -> ApCheck.WRONG_NETWORK_NAME
                link.hasValidatedInternet -> ApCheck.WRONG_NETWORK_HAS_INTERNET
                manuallyConfirmedNetwork == link.network -> ApCheck.CONFIRMED_MANUALLY
                else -> ApCheck.NEEDS_MANUAL_CONFIRMATION
            }
        }

    val isSending: Boolean get() = provisioning is ProvisioningState.Sending
    val isSearching: Boolean get() = discovery is DiscoveryState.Searching

    val canConfigure: Boolean
        get() = apCheck.isConfirmed && form.problem == null && !form.isDeviceApName && !isSending

    /** Discovery is pointless on the provisioning AP itself: modules in AP mode don't answer. */
    val canDiscover: Boolean
        get() = link != null && !link.isSetupAp && apCheck != ApCheck.CONFIRMED_MANUALLY && !isSearching
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val monitor = WifiNetworkMonitor(application)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var provisionJob: Job? = null
    private var watchJob: Job? = null
    private var discoveryJob: Job? = null

    init {
        monitor.start()
        viewModelScope.launch { monitor.link.collect { link -> _state.update { it.copy(link = link) } } }
        refreshPermissions()
    }

    /** Call when returning to the app: permissions or the Location toggle may have changed. */
    fun refreshPermissions() {
        val app = getApplication<Application>()
        _state.update {
            val localNetwork = Permissions.hasLocalNetwork(app)
            it.copy(
                preciseLocationGranted = Permissions.hasPreciseLocation(app),
                locationEnabled = Permissions.isLocationEnabled(app),
                localNetworkGranted = localNetwork,
                localNetworkDenied = it.localNetworkDenied && !localNetwork,
            )
        }
        monitor.refresh()
    }

    fun onLocalNetworkPermissionResult(granted: Boolean) {
        refreshPermissions()
        _state.update { it.copy(localNetworkDenied = !granted) }
    }

    fun setManualConfirmation(confirmed: Boolean) {
        _state.update { it.copy(manuallyConfirmedNetwork = if (confirmed) it.link?.network else null) }
    }

    fun updateForm(transform: (CredentialsForm) -> CredentialsForm) {
        _state.update { it.copy(form = transform(it.form)) }
    }

    fun configure() {
        val snapshot = _state.value
        val link = snapshot.link
        if (!snapshot.canConfigure || link == null) return
        val form = snapshot.form

        provisionJob?.cancel()
        watchJob?.cancel()
        provisionJob = viewModelScope.launch {
            _state.update { it.copy(provisioning = ProvisioningState.Sending(0, MAX_ROUNDS)) }
            val packet = BroadlinkPackets.buildSetupPacket(form.ssid, form.password, form.security)
            val provisioner = BroadlinkProvisioner(NetworkBoundSockets(link.network), maxAttempts = MAX_ROUNDS)
            // The SoftAP's gateway is the module itself, so it is also addressed directly.
            val destinations = Ipv4.destinations(link.subnetBroadcast, unicast = link.gateway)
            val sentAt = SystemClock.elapsedRealtime()
            try {
                val outcome = getApplication<Application>().withMulticastLock {
                    provisioner.send(packet, destinations) { round, max ->
                        _state.update { it.copy(provisioning = ProvisioningState.Sending(round, max)) }
                    }
                }
                val apWatch = if (outcome is BroadlinkProvisioner.Outcome.LinkLostAfterSend) {
                    ApWatch.Gone(secondsSince(sentAt))
                } else {
                    ApWatch.Watching
                }
                _state.update {
                    it.copy(provisioning = ProvisioningState.Sent(outcome, form.ssid, apWatch))
                }
                watchAccessPoint(link.network, sentAt, alreadyGone = apWatch is ApWatch.Gone)
            } catch (e: IOException) {
                _state.update { it.copy(provisioning = ProvisioningState.Failed(classify(e))) }
            } finally {
                packet.fill(0)
            }
        }
    }

    /**
     * After sending, a module that accepted the credentials reboots and its AP disappears. Report
     * that, then wait for the phone to land on another Wi-Fi network and search for the module.
     */
    private fun watchAccessPoint(apNetwork: Network, sentAt: Long, alreadyGone: Boolean) {
        watchJob = viewModelScope.launch {
            val gone = alreadyGone || withTimeoutOrNull(AP_WATCH_MILLIS) {
                monitor.link.first { it?.network != apNetwork }
                true
            } == true
            val watch = if (gone) ApWatch.Gone(secondsSince(sentAt)) else ApWatch.StillConnected
            _state.update { s ->
                val p = s.provisioning
                if (p is ProvisioningState.Sent) s.copy(provisioning = p.copy(apWatch = watch)) else s
            }
            if (!gone) return@launch

            val homeLink = withTimeoutOrNull(RECONNECT_WAIT_MILLIS) {
                monitor.link.first { it != null && it.network != apNetwork && !it.isSetupAp }
            }
            val s = _state.value
            if (homeLink != null && s.localNetworkGranted && s.discovery !is DiscoveryState.Searching) {
                discover(durationMillis = AUTO_DISCOVERY_MILLIS)
            }
        }
    }

    fun discover(durationMillis: Long = MANUAL_DISCOVERY_MILLIS) {
        val link = _state.value.link ?: return
        if (!_state.value.canDiscover) return

        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            val totalSeconds = (durationMillis / 1000).toInt()
            _state.update { it.copy(discovery = DiscoveryState.Searching(emptyList(), totalSeconds)) }
            val discovery = BroadlinkDiscovery(NetworkBoundSockets(link.network), durationMillis = durationMillis)
            try {
                val devices = getApplication<Application>().withMulticastLock {
                    discovery.discover(Ipv4.destinations(link.subnetBroadcast), link.address) { device ->
                        _state.update { s ->
                            val d = s.discovery
                            if (d is DiscoveryState.Searching) s.copy(discovery = d.copy(found = d.found + device)) else s
                        }
                    }
                }
                _state.update { it.copy(discovery = DiscoveryState.Done(devices)) }
            } catch (e: IOException) {
                _state.update { it.copy(discovery = DiscoveryState.Failed(classify(e))) }
            }
        }
    }

    override fun onCleared() {
        monitor.stop()
    }

    private fun secondsSince(elapsedRealtime: Long) = (SystemClock.elapsedRealtime() - elapsedRealtime) / 1000

    private fun classify(e: IOException): NetworkError {
        val text = generateSequence<Throwable>(e) { it.cause }.mapNotNull { it.message }.joinToString(" ")
        return when {
            listOf("EPERM", "EACCES", "Operation not permitted", "Permission denied").any { it in text } ->
                NetworkError.LocalNetworkBlocked
            listOf("ENONET", "ENETUNREACH", "not on the network", "unreachable").any { it in text } ->
                NetworkError.NotConnected
            else -> NetworkError.Other("${e.javaClass.simpleName}: ${e.message.orEmpty()}")
        }
    }

    private companion object {
        const val MAX_ROUNDS = 3
        const val AP_WATCH_MILLIS = 90_000L
        const val RECONNECT_WAIT_MILLIS = 5 * 60_000L
        const val AUTO_DISCOVERY_MILLIS = 30_000L
        const val MANUAL_DISCOVERY_MILLIS = 10_000L
    }
}
