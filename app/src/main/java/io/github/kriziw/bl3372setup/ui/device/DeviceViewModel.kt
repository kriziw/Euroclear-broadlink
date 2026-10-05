package io.github.kriziw.bl3372setup.ui.device

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.kriziw.bl3372setup.Permissions
import io.github.kriziw.bl3372setup.app
import io.github.kriziw.bl3372setup.broadlink.BroadlinkDiscovery
import io.github.kriziw.bl3372setup.broadlink.BroadlinkPackets
import io.github.kriziw.bl3372setup.broadlink.BroadlinkSession
import io.github.kriziw.bl3372setup.broadlink.DiscoveredDevice
import io.github.kriziw.bl3372setup.broadlink.Ipv4
import io.github.kriziw.bl3372setup.broadlink.SocketFactory
import io.github.kriziw.bl3372setup.devices.SavedDevice
import io.github.kriziw.bl3372setup.network.NetworkBoundSockets
import io.github.kriziw.bl3372setup.network.NetworkError
import io.github.kriziw.bl3372setup.network.WifiLink
import io.github.kriziw.bl3372setup.network.withMulticastLock
import io.github.kriziw.bl3372setup.runxin.Bl3372Transport
import io.github.kriziw.bl3372setup.runxin.ControllerProfiles
import io.github.kriziw.bl3372setup.runxin.VolumeUnit
import io.github.kriziw.bl3372setup.runxin.SoftenerClient
import io.github.kriziw.bl3372setup.runxin.SoftenerSetting
import io.github.kriziw.bl3372setup.runxin.SoftenerState
import io.github.kriziw.bl3372setup.runxin.Station
import io.github.kriziw.bl3372setup.runxin.WriteResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress

sealed interface Connection {
    data object Starting : Connection
    data object NoWifi : Connection
    data object NeedsLocalNetworkPermission : Connection
    /** Looking for the device; [crossSubnet] when sweeping another subnet by unicast. */
    data class Locating(val crossSubnet: Boolean) : Connection
    data object Connecting : Connection
    data object Live : Connection
    data class NotFound(val lastIp: String) : Connection
    data class Failed(val error: NetworkError) : Connection
    data class UnsupportedModule(val deviceType: Int) : Connection
}

/** Outcome of the most recent write, shown once in a snackbar. */
data class WriteOutcome(val setting: SoftenerSetting, val result: WriteResult?, val error: NetworkError? = null)

data class DeviceUiState(
    val device: SavedDevice? = null,
    val link: WifiLink? = null,
    val connection: Connection = Connection.Starting,
    val state: SoftenerState? = null,
    val updatedAt: Long? = null,
    val pendingWrite: SoftenerSetting? = null,
    val lastWrite: WriteOutcome? = null,
) {
    val profile get() = ControllerProfiles.resolve(device?.deviceType, state?.deviceModel)

    val isVerifiedModel: Boolean
        get() = profile != null

    val experimentalUnlocked: Boolean
        get() = ControllerProfiles.experimentalAllowed(
            device?.deviceType, state?.deviceModel, device?.controlsUnlocked == true, device?.unlockedModelCode,
        )

    val controlsEnabled: Boolean
        get() = connection == Connection.Live && pendingWrite == null &&
            (isVerifiedModel || experimentalUnlocked)

    /** Forced regeneration only starts from normal service, never from vacation or a closed valve. */
    val canRegenerate: Boolean
        get() = controlsEnabled && state?.let { it.station == Station.IN_SERVICE && it.vacationFlag != true } == true

    /**
     * Vacation mode is offered for experimental controller profiles only: on the verified F79D
     * (model 9) the field-49 write is known to be acknowledged without effect.
     */
    val offersVacation: Boolean
        get() = state != null && profile == null && ControllerProfiles.supportsTransport(device?.deviceType)

    /** Like the controller's own button: vacation starts only from normal service. */
    val canStartVacation: Boolean
        get() = offersVacation && controlsEnabled &&
            state?.let { it.station == Station.IN_SERVICE && it.vacationFlag == false } == true

    /** It ends only from the stable vacation pause (pause 2), not while still preparing. */
    val canEndVacation: Boolean
        get() = offersVacation && controlsEnabled &&
            state?.let { it.vacationFlag == true && it.station == Station.PAUSE_2 } == true
}

class DeviceViewModel(application: Application, private val mac: String) : AndroidViewModel(application) {
    private val monitor = application.app.wifiMonitor
    private val store = application.app.deviceStore
    private val _state = MutableStateFlow(DeviceUiState(device = store.get(mac)))
    val state: StateFlow<DeviceUiState> = _state.asStateFlow()

    private var client: SoftenerClient? = null
    private var pollJob: Job? = null

    /** Sockets bound to whatever Wi-Fi network is current when each socket is opened. */
    private val sockets = SocketFactory {
        val network = monitor.link.value?.network ?: throw IOException("ENONET: no Wi-Fi network")
        NetworkBoundSockets(network).open()
    }

    init {
        viewModelScope.launch {
            combine(store.devices, monitor.link) { devices, link -> devices.firstOrNull { it.mac == mac } to link }
                .collect { (device, link) -> _state.update { it.copy(device = device, link = link) } }
        }
    }

    /** Starts polling while the screen is visible. */
    fun start() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch { pollLoop() }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    /** Retry immediately, e.g. after the user granted a permission or changed the IP. */
    fun reconnect() {
        stop()
        client = null
        start()
    }

    fun rename(name: String) {
        if (name.isNotBlank()) store.update(mac) { it.copy(name = name.trim()) }
    }

    fun setAddress(ip: Inet4Address) {
        store.update(mac) { it.copy(lastIp = ip.hostAddress!!) }
        reconnect()
    }

    fun unlockControls() {
        val current = _state.value
        val model = current.state?.deviceModel ?: return
        if (current.connection != Connection.Live || !ControllerProfiles.supportsTransport(current.device?.deviceType)) return
        store.update(mac) { it.copy(controlsUnlocked = true, unlockedModelCode = model) }
    }

    fun lockControls() = store.update(mac) { it.copy(controlsUnlocked = false, unlockedModelCode = null) }

    fun remove() {
        stop()
        store.remove(mac)
    }

    fun consumeWriteOutcome() = _state.update { it.copy(lastWrite = null) }

    fun apply(setting: SoftenerSetting) {
        val c = client ?: return
        if (!_state.value.controlsEnabled) return
        if (setting is SoftenerSetting.FlowShutoff && _state.value.state?.volumeUnit != VolumeUnit.CUBIC_METRES) return
        if (setting is SoftenerSetting.Regenerate && !_state.value.canRegenerate) return
        if (setting is SoftenerSetting.Vacation &&
            !(if (setting.on) _state.value.canStartVacation else _state.value.canEndVacation)
        ) return
        _state.update { it.copy(pendingWrite = setting) }
        viewModelScope.launch {
            val outcome = try {
                val result = c.write(setting)
                when (result) {
                    is WriteResult.Confirmed -> publish(result.state)
                    is WriteResult.NotConfirmed -> result.lastState?.let(::publish)
                }
                WriteOutcome(setting, result)
            } catch (e: IOException) {
                WriteOutcome(setting, null, NetworkError.of(e))
            }
            _state.update { it.copy(pendingWrite = null, lastWrite = outcome) }
        }
    }

    private suspend fun pollLoop() {
        while (true) {
            val link = monitor.link.value
            if (link == null) {
                setConnection(Connection.NoWifi)
                monitor.link.first { it != null }
                continue
            }
            if (!Permissions.hasLocalNetwork(getApplication())) {
                setConnection(Connection.NeedsLocalNetworkPermission)
                return // The screen calls reconnect() once the permission is granted.
            }
            try {
                val c = client ?: connect(link)
                if (c == null) {
                    delay(RETRY_MILLIS)
                    continue
                }
                val s = c.readState()
                publish(s)
                setConnection(Connection.Live)
                delay(if (s.isActive) ACTIVE_POLL_MILLIS else IDLE_POLL_MILLIS)
            } catch (e: IOException) {
                client = null
                setConnection(Connection.Failed(NetworkError.of(e)))
                delay(RETRY_MILLIS)
            }
        }
    }

    /**
     * Finds the device by MAC, preferring its last known address. This works across VLANs because
     * the hello is unicast; broadcast is only used when the device was on the phone's own subnet.
     * If its address changed on another subnet, the surrounding /24 is swept with unicast hellos.
     */
    private suspend fun connect(link: WifiLink): SoftenerClient? {
        val device = store.get(mac) ?: return null
        val lastIp = Ipv4.parse(device.lastIp)
        val sameSubnet = lastIp != null && link.address != null && Ipv4.sameSubnet(lastIp, link.address, link.prefixLength)

        setConnection(Connection.Locating(crossSubnet = false))
        var found = lastIp?.let { find(listOf(InetSocketAddress(it, BroadlinkPackets.PORT)), link, LOOKUP_MILLIS) }
        if (found == null) {
            val broadcast = Ipv4.destinations(link.subnetBroadcast)
            val sweep = if (lastIp != null && !sameSubnet) Ipv4.surrounding24(lastIp).hosts() else emptyList()
            setConnection(Connection.Locating(crossSubnet = sweep.isNotEmpty()))
            found = find(broadcast + sweep.map { InetSocketAddress(it, BroadlinkPackets.PORT) }, link, SEARCH_MILLIS)
        }
        if (found == null) {
            setConnection(Connection.NotFound(device.lastIp))
            return null
        }
        store.update(mac) { it.copy(lastIp = found.address.hostAddress!!, deviceType = found.deviceType) }

        if (!ControllerProfiles.supportsTransport(found.deviceType)) {
            _state.update { it.copy(state = null, updatedAt = null) }
            setConnection(Connection.UnsupportedModule(found.deviceType))
            return null
        }

        setConnection(Connection.Connecting)
        val session = BroadlinkSession(sockets, found.endpoint())
        return SoftenerClient(Bl3372Transport(session), moduleType = found.deviceType).also { client = it }
    }

    private suspend fun find(
        destinations: List<InetSocketAddress>,
        link: WifiLink,
        durationMillis: Long,
    ): DiscoveredDevice? = getApplication<Application>().withMulticastLock {
        BroadlinkDiscovery(sockets, durationMillis = durationMillis)
            .discover(destinations, link.address, stopWhen = { it.mac == mac })
            .firstOrNull { it.mac == mac }
    }

    private fun publish(s: SoftenerState) = _state.update { it.copy(state = s, updatedAt = System.currentTimeMillis()) }

    private fun setConnection(c: Connection) = _state.update { it.copy(connection = c) }

    private companion object {
        const val LOOKUP_MILLIS = 3_000L
        const val SEARCH_MILLIS = 6_000L
        const val RETRY_MILLIS = 10_000L
        const val ACTIVE_POLL_MILLIS = 5_000L
        const val IDLE_POLL_MILLIS = 15_000L
    }
}
