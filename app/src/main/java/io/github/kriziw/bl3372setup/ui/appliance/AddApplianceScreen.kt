package io.github.kriziw.bl3372setup.ui.appliance

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kriziw.bl3372setup.Permissions
import io.github.kriziw.bl3372setup.R
import io.github.kriziw.bl3372setup.SystemScreens
import io.github.kriziw.bl3372setup.app
import io.github.kriziw.bl3372setup.appliance.ApplianceAddress
import io.github.kriziw.bl3372setup.appliance.ApplianceDrivers
import io.github.kriziw.bl3372setup.appliance.ApplianceScanner
import io.github.kriziw.bl3372setup.appliance.ApplianceState
import io.github.kriziw.bl3372setup.appliance.Brand
import io.github.kriziw.bl3372setup.appliance.CredentialKind
import io.github.kriziw.bl3372setup.appliance.judo.JudoDriver
import io.github.kriziw.bl3372setup.broadlink.Ipv4
import io.github.kriziw.bl3372setup.devices.SavedDevice
import io.github.kriziw.bl3372setup.network.LocalHttp
import io.github.kriziw.bl3372setup.network.NetworkError
import io.github.kriziw.bl3372setup.network.NetworkTcpConnector
import io.github.kriziw.bl3372setup.ui.common.AppScaffold
import io.github.kriziw.bl3372setup.ui.common.BackButton
import io.github.kriziw.bl3372setup.ui.common.Banner
import io.github.kriziw.bl3372setup.ui.common.Hint
import io.github.kriziw.bl3372setup.ui.common.OptionCard
import io.github.kriziw.bl3372setup.ui.common.PrivacyFooter
import io.github.kriziw.bl3372setup.ui.common.SectionCard
import io.github.kriziw.bl3372setup.ui.common.StatusKind
import io.github.kriziw.bl3372setup.ui.common.networkErrorText
import io.github.kriziw.bl3372setup.ui.setup.LocalNetworkCard
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID

/** An appliance answered at [host] during a network search. */
data class FoundAppliance(val host: String, val model: String)

data class AddApplianceState(
    val testing: Boolean = false,
    val found: ApplianceState? = null,
    val error: NetworkError? = null,
    val localNetworkGranted: Boolean = true,
    val scanning: Boolean = false,
    val scanned: Int = 0,
    val scanTotal: Int = 0,
    val scanResults: List<FoundAppliance>? = null,
)

class AddApplianceViewModel(application: Application) : AndroidViewModel(application) {
    private val monitor = application.app.wifiMonitor
    private val store = application.app.deviceStore
    private val _state = MutableStateFlow(AddApplianceState(localNetworkGranted = Permissions.hasLocalNetwork(application)))
    val state: StateFlow<AddApplianceState> = _state.asStateFlow()
    private var testJob: Job? = null
    private var scanJob: Job? = null

    fun refreshPermission() = _state.update { it.copy(localNetworkGranted = Permissions.hasLocalNetwork(getApplication())) }

    /** Forgets the last connection test (the form changed). A running search continues. */
    fun reset() {
        testJob?.cancel()
        _state.update { it.copy(testing = false, found = null, error = null) }
    }

    /** Forgets everything, e.g. when another brand is chosen. */
    fun clear() {
        reset()
        scanJob?.cancel()
        _state.update { it.copy(scanning = false, scanResults = null) }
    }

    /** Reads the appliance until it has identified itself; slow devices answer with a partial state first. */
    fun test(brand: Brand, address: ApplianceAddress) {
        testJob?.cancel()
        _state.update { it.copy(testing = true, found = null, error = null) }
        testJob = viewModelScope.launch {
            val link = monitor.link.value
            if (link == null) {
                _state.update { it.copy(testing = false, error = NetworkError.NotConnected) }
                return@launch
            }
            try {
                val found = identify(LocalHttp(NetworkTcpConnector(link.network)), brand, address, TEST_TIMEOUT_MILLIS)
                _state.update { it.copy(testing = false, found = found) }
            } catch (e: IOException) {
                _state.update { it.copy(testing = false, error = NetworkError.of(e)) }
            } catch (_: TimeoutCancellationException) {
                _state.update { it.copy(testing = false, error = NetworkError.Timeout) }
            }
        }
    }

    /**
     * Searches the phone's /24 for the brand's port, then asks each open host to identify itself
     * with [login] (JUDO and BWT need it). Nothing is sent to hosts whose port is closed.
     */
    fun scan(brand: Brand, port: Int, login: ApplianceAddress) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val link = monitor.link.value
            val own = link?.address
            if (link == null || own == null) {
                _state.update { it.copy(error = NetworkError.NotConnected) }
                return@launch
            }
            val hosts = Ipv4.surrounding24(own).hosts().filter { it != own }.mapNotNull { it.hostAddress }
            _state.update { it.copy(scanning = true, scanned = 0, scanTotal = hosts.size, scanResults = emptyList(), error = null, found = null) }
            val connector = NetworkTcpConnector(link.network)
            val open = ApplianceScanner.openHosts(connector, hosts, port) { done -> _state.update { it.copy(scanned = done) } }
            val http = LocalHttp(connector)
            open.forEach { host ->
                val state = try {
                    identify(http, brand, login.copy(host = host, port = port), SCAN_IDENTIFY_MILLIS)
                } catch (_: IOException) {
                    null
                } catch (_: TimeoutCancellationException) {
                    null
                }
                state?.model?.let { model -> _state.update { it.copy(scanResults = it.scanResults.orEmpty() + FoundAppliance(host, model)) } }
            }
            _state.update { it.copy(scanning = false) }
        }
    }

    private suspend fun identify(http: LocalHttp, brand: Brand, address: ApplianceAddress, timeoutMillis: Long): ApplianceState =
        coroutineScope {
            val driver = ApplianceDrivers.create(brand, http, address)
            val identified = CompletableDeferred<ApplianceState>()
            val reader = launch {
                try {
                    identified.complete(driver.read { if (it.model != null) identified.complete(it) })
                } catch (e: IOException) {
                    identified.completeExceptionally(e)
                }
            }
            try {
                withTimeout(timeoutMillis) { identified.await() }
            } finally {
                reader.cancel()
            }
        }

    /** Saves the identified appliance and returns its id. */
    fun save(brand: Brand, address: ApplianceAddress): String? {
        val found = _state.value.found ?: return null
        val id = "${brand.id}:${UUID.randomUUID()}"
        store.save(
            SavedDevice(
                mac = id,
                name = found.model ?: brand.displayName,
                lastIp = address.host,
                deviceType = found.modelCode ?: 0,
                brand = brand.id,
                port = address.port,
                user = address.user,
                secret = address.secret,
                model = found.model,
            ),
        )
        return id
    }

    private companion object {
        const val TEST_TIMEOUT_MILLIS = 45_000L
        const val SCAN_IDENTIFY_MILLIS = 15_000L
    }
}

@Composable
fun AddApplianceRoute(
    initialBrand: String?,
    initialHost: String?,
    onBack: () -> Unit,
    onWifiSetup: (Brand) -> Unit,
    onSaved: (String) -> Unit,
    viewModel: AddApplianceViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshPermission() }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermission()
        onPauseOrDispose {}
    }

    var brandId by rememberSaveable { mutableStateOf(initialBrand) }
    val brand = Brand.of(brandId)
    var host by rememberSaveable { mutableStateOf(initialHost.orEmpty()) }
    var port by rememberSaveable(brandId) { mutableStateOf(brand?.defaultPort?.toString().orEmpty()) }
    var user by rememberSaveable(brandId) { mutableStateOf(if (brand == Brand.JUDO) JudoDriver.DEFAULT_USER else "") }
    // Never saved across process death; the user re-enters it if Android recreates the screen.
    var secret by remember(brandId) { mutableStateOf(if (brand == Brand.JUDO) JudoDriver.DEFAULT_PASSWORD else "") }
    val portValue = port.trim().toIntOrNull()?.takeIf { it in 1..65535 }
    val needsSecret = brand != null && brand.credential != CredentialKind.NONE
    val login = if (brand != null && portValue != null && (!needsSecret || secret.isNotEmpty())) {
        ApplianceAddress(
            host = host.trim(),
            port = portValue,
            user = user.trim().ifEmpty { null }.takeIf { brand.credential == CredentialKind.USER_PASSWORD },
            secret = secret.takeIf { needsSecret },
        )
    } else {
        null
    }
    val address = login?.takeIf { it.host.isNotBlank() }

    AppScaffold(title = stringResource(R.string.home_add_other), navigation = { BackButton(onBack) }) {
        if (!state.localNetworkGranted && Permissions.localNetworkRequired) {
            LocalNetworkCard(denied = false, request = { launcher.launch(Permissions.ACCESS_LOCAL_NETWORK) }, openAppSettings = { SystemScreens.openAppSettings(context) })
        }
        Text(stringResource(R.string.appliance_choose_brand), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Brand.entries.forEach { option ->
                val selected = option == brand
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton) {
                        if (option.id != brandId) {
                            brandId = option.id
                            host = ""
                            viewModel.clear()
                        }
                    },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(option.displayName, style = MaterialTheme.typography.titleMedium)
                            Hint(brandHint(option))
                        }
                    }
                }
            }
        }

        if (brand != null) {
            OptionCard(
                icon = R.drawable.ic_wifi,
                title = stringResource(R.string.appliance_wifi_setup),
                description = stringResource(R.string.appliance_wifi_setup_hint),
                onClick = { onWifiSetup(brand) },
            )
            SectionCard(title = stringResource(R.string.appliance_on_network_title)) {
                Hint(brandSetupHint(brand))
                LoginFields(brand, user, { user = it; viewModel.reset() }, secret, { secret = it; viewModel.reset() })
                FilledTonalButton(
                    onClick = { if (login != null && portValue != null) viewModel.scan(brand, portValue, login) },
                    enabled = login != null && !state.scanning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.scanning) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.appliance_searching, state.scanned, state.scanTotal))
                    } else {
                        Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_search_network))
                    }
                }
                state.scanResults?.forEach { result ->
                    Card(
                        onClick = {
                            host = result.host
                            login?.let { viewModel.test(brand, it.copy(host = result.host)) }
                        },
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(result.model, style = MaterialTheme.typography.titleSmall)
                                Hint(result.host)
                            }
                            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
                        }
                    }
                }
                if (state.scanResults?.isEmpty() == true && !state.scanning) {
                    Hint(stringResource(R.string.appliance_search_none))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Hint(stringResource(R.string.appliance_or_address))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it; viewModel.reset() },
                    label = { Text(stringResource(R.string.setup_address_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it; viewModel.reset() },
                    label = { Text(stringResource(R.string.appliance_port)) },
                    singleLine = true,
                    isError = portValue == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { address?.let { viewModel.test(brand, it) } },
                    enabled = address != null && !state.testing,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (state.testing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(if (brand == Brand.JUDO) R.string.appliance_reading_slow else R.string.device_connecting))
                    } else {
                        Text(stringResource(R.string.action_connect))
                    }
                }
                Hint(stringResource(R.string.appliance_plain_http))
            }
            state.error?.let { Banner(StatusKind.ERROR, networkErrorText(it)) }
            state.found?.let { found ->
                Banner(StatusKind.SUCCESS, stringResource(R.string.appliance_found, found.model ?: brand.displayName))
                Button(
                    onClick = { address?.let { viewModel.save(brand, it) }?.let(onSaved) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(R.string.action_save_and_open)) }
            }
        }
        PrivacyFooter()
    }
}
