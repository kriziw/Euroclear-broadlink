package io.github.kriziw.bl3372setup.ui.appliance

import android.app.Application
import android.net.Network
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.kriziw.bl3372setup.Permissions
import io.github.kriziw.bl3372setup.app
import io.github.kriziw.bl3372setup.appliance.ApplianceAddress
import io.github.kriziw.bl3372setup.appliance.ApplianceChange
import io.github.kriziw.bl3372setup.appliance.ApplianceDriver
import io.github.kriziw.bl3372setup.appliance.ApplianceDrivers
import io.github.kriziw.bl3372setup.appliance.ApplianceState
import io.github.kriziw.bl3372setup.appliance.Brand
import io.github.kriziw.bl3372setup.appliance.ChangeResult
import io.github.kriziw.bl3372setup.devices.SavedDevice
import io.github.kriziw.bl3372setup.network.LocalHttp
import io.github.kriziw.bl3372setup.network.NetworkError
import io.github.kriziw.bl3372setup.network.NetworkTcpConnector
import io.github.kriziw.bl3372setup.network.WifiLink
import io.github.kriziw.bl3372setup.ui.device.Connection
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

/** Outcome of the most recent change, shown once in a snackbar. */
data class ApplianceOutcome(val change: ApplianceChange, val result: ChangeResult?, val error: NetworkError? = null)

data class ApplianceUiState(
    val device: SavedDevice? = null,
    val link: WifiLink? = null,
    val connection: Connection = Connection.Starting,
    val appliance: ApplianceState? = null,
    val updatedAt: Long? = null,
    val pending: ApplianceChange? = null,
    val lastOutcome: ApplianceOutcome? = null,
) {
    val brand: Brand? get() = Brand.of(device?.brand)

    /** Every other brand is experimental: the opt-in is scoped to the identity the device reported. */
    val unlocked: Boolean
        get() = device?.controlsUnlocked == true && device.unlockedModelCode == appliance?.modelCode

    val controlsEnabled: Boolean
        get() = connection == Connection.Live && pending == null && unlocked && appliance?.complete == true
}

class ApplianceViewModel(application: Application, private val id: String) : AndroidViewModel(application) {
    private val monitor = application.app.wifiMonitor
    private val store = application.app.deviceStore
    private val _state = MutableStateFlow(ApplianceUiState(device = store.get(id)))
    val state: StateFlow<ApplianceUiState> = _state.asStateFlow()

    private var driver: ApplianceDriver? = null
    private var driverKey: Pair<Network, ApplianceAddress>? = null
    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            combine(store.devices, monitor.link) { devices, link -> devices.firstOrNull { it.mac == id } to link }
                .collect { (device, link) -> _state.update { it.copy(device = device, link = link) } }
        }
    }

    fun start() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch { pollLoop() }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    fun reconnect() {
        stop()
        driver = null
        start()
    }

    fun rename(name: String) {
        if (name.isNotBlank()) store.update(id) { it.copy(name = name.trim()) }
    }

    fun setAddress(host: String, port: Int) {
        store.update(id) { it.copy(lastIp = host.trim(), port = port) }
        reconnect()
    }

    fun setLogin(user: String?, secret: String) {
        store.update(id) { it.copy(user = user?.trim()?.ifEmpty { null }, secret = secret) }
        reconnect()
    }

    fun unlockControls() {
        val current = _state.value
        if (current.connection != Connection.Live || current.appliance == null) return
        store.update(id) { it.copy(controlsUnlocked = true, unlockedModelCode = current.appliance.modelCode) }
    }

    fun lockControls() = store.update(id) { it.copy(controlsUnlocked = false, unlockedModelCode = null) }

    fun remove() {
        stop()
        store.remove(id)
    }

    fun consumeOutcome() = _state.update { it.copy(lastOutcome = null) }

    fun apply(change: ApplianceChange) {
        val d = driver ?: return
        if (!_state.value.controlsEnabled) return
        _state.update { it.copy(pending = change) }
        viewModelScope.launch {
            val outcome = try {
                val result = d.apply(change)
                result.state()?.let(::publish)
                ApplianceOutcome(change, result)
            } catch (e: IOException) {
                ApplianceOutcome(change, null, NetworkError.of(e))
            }
            _state.update { it.copy(pending = null, lastOutcome = outcome) }
        }
    }

    private fun ChangeResult.state(): ApplianceState? = when (this) {
        is ChangeResult.Confirmed -> state
        is ChangeResult.NotConfirmed -> state
        is ChangeResult.Accepted -> state
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
            val device = store.get(id) ?: return
            val brand = Brand.of(device.brand) ?: return
            val d = driverFor(link.network, brand, device)
            try {
                if (_state.value.connection !is Connection.Live) setConnection(Connection.Connecting)
                val read = d.read { partial -> publish(partial) }
                publish(read)
                setConnection(Connection.Live)
                if (read.model != null && read.model != device.model) store.update(id) { it.copy(model = read.model) }
                delay(d.pollIntervalMillis)
            } catch (e: IOException) {
                val error = NetworkError.of(e)
                setConnection(Connection.Failed(error))
                // A wrong login will not fix itself; retry slowly until it is changed.
                delay(if (error == NetworkError.LoginRejected) LOGIN_RETRY_MILLIS else RETRY_MILLIS)
            }
        }
    }

    private fun driverFor(network: Network, brand: Brand, device: SavedDevice): ApplianceDriver {
        val address = ApplianceAddress(device.lastIp, device.port ?: brand.defaultPort, device.user, device.secret)
        val key = network to address
        driver?.let { if (driverKey == key) return it }
        return ApplianceDrivers.create(brand, LocalHttp(NetworkTcpConnector(network)), address).also {
            driver = it
            driverKey = key
        }
    }

    private fun publish(s: ApplianceState) = _state.update { it.copy(appliance = s, updatedAt = System.currentTimeMillis()) }

    private fun setConnection(c: Connection) = _state.update { it.copy(connection = c) }

    private companion object {
        const val RETRY_MILLIS = 15_000L
        const val LOGIN_RETRY_MILLIS = 120_000L
    }
}
