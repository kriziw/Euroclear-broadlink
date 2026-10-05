package io.github.kriziw.bl3372setup.ui.appliance

import android.annotation.SuppressLint
import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kriziw.bl3372setup.R
import io.github.kriziw.bl3372setup.SystemScreens
import io.github.kriziw.bl3372setup.app
import io.github.kriziw.bl3372setup.appliance.Brand
import io.github.kriziw.bl3372setup.appliance.judo.JudoDriver
import io.github.kriziw.bl3372setup.appliance.syr.SyrWifiProvisioner
import io.github.kriziw.bl3372setup.network.LocalHttp
import io.github.kriziw.bl3372setup.network.NetworkError
import io.github.kriziw.bl3372setup.network.NetworkTcpConnector
import io.github.kriziw.bl3372setup.network.WifiLink
import io.github.kriziw.bl3372setup.ui.common.AppScaffold
import io.github.kriziw.bl3372setup.ui.common.BackButton
import io.github.kriziw.bl3372setup.ui.common.Banner
import io.github.kriziw.bl3372setup.ui.common.Hint
import io.github.kriziw.bl3372setup.ui.common.SectionCard
import io.github.kriziw.bl3372setup.ui.common.StatusKind
import io.github.kriziw.bl3372setup.ui.common.StatusLine
import io.github.kriziw.bl3372setup.ui.common.networkErrorText
import io.github.kriziw.bl3372setup.ui.setup.NumberedSteps
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** How each brand joins a Wi-Fi network. */
private enum class WifiSetupKind {
    /** On the softener's own touchscreen (BWT Perla). */
    DEVICE_MENU,

    /** On the device's own setup page, served on its access point (JUDO, Grünbeck). */
    DEVICE_PAGE,

    /** Through the device's local API on its access point, sent by WaterCare (SYR NeoSoft). */
    APP,
}

private val Brand.wifiSetup: WifiSetupKind
    get() = when (this) {
        Brand.BWT_PERLA -> WifiSetupKind.DEVICE_MENU
        Brand.JUDO, Brand.GRUENBECK -> WifiSetupKind.DEVICE_PAGE
        Brand.SYR_NEOSOFT -> WifiSetupKind.APP
    }

/** Setup page addresses on the devices' own access points, as in their manuals. Allowed as plain HTTP in network_security_config. */
private val Brand.setupPage: String?
    get() = when (this) {
        Brand.JUDO -> "http://192.168.4.1/"
        Brand.GRUENBECK -> "http://192.168.0.1/"
        else -> null
    }

data class WifiSetupState(
    val link: WifiLink? = null,
    /** The user confirmed the phone is on the device's access point; cleared when the network changes. */
    val confirmedNetwork: Network? = null,
    val sending: Boolean = false,
    val result: SyrWifiProvisioner.Result? = null,
    val error: NetworkError? = null,
) {
    val confirmed: Boolean get() = link != null && confirmedNetwork == link.network
}

class ApplianceWifiSetupViewModel(application: Application) : AndroidViewModel(application) {
    private val monitor = application.app.wifiMonitor
    private val _state = MutableStateFlow(WifiSetupState())
    val state: StateFlow<WifiSetupState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch { monitor.link.collect { link -> _state.update { it.copy(link = link) } } }
    }

    fun confirm(on: Boolean) = _state.update { it.copy(confirmedNetwork = if (on) it.link?.network else null) }

    /** SYR: sends the home Wi-Fi to the softener over its access point (the phone's gateway). */
    fun provisionSyr(ssid: String, password: String) {
        val link = _state.value.link ?: return
        val gateway = link.gateway?.hostAddress ?: run {
            _state.update { it.copy(error = NetworkError.NotConnected) }
            return
        }
        job?.cancel()
        _state.update { it.copy(sending = true, result = null, error = null) }
        job = viewModelScope.launch {
            val outcome = try {
                SyrWifiProvisioner(LocalHttp(NetworkTcpConnector(link.network)), gateway).provision(ssid, password)
            } catch (e: IOException) {
                _state.update { it.copy(sending = false, error = NetworkError.of(e)) }
                return@launch
            }
            _state.update { it.copy(sending = false, result = outcome) }
        }
    }
}

@Composable
fun ApplianceWifiSetupRoute(brand: Brand, onBack: () -> Unit, onDone: (host: String?) -> Unit, viewModel: ApplianceWifiSetupViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pageOpen by rememberSaveable { mutableStateOf(false) }
    val page = brand.setupPage
    val link = state.link
    if (pageOpen && page != null && link != null) {
        DevicePage(brand, page, link.network, onClose = { pageOpen = false })
        return
    }

    AppScaffold(
        title = stringResource(R.string.appliance_wifi_setup),
        subtitle = { Hint(brand.displayName) },
        navigation = { BackButton(onBack) },
    ) {
        SectionCard {
            NumberedSteps(
                stringResource(
                    when (brand) {
                        Brand.JUDO -> R.string.judo_wifi_steps
                        Brand.GRUENBECK -> R.string.gruenbeck_wifi_steps
                        Brand.BWT_PERLA -> R.string.bwt_wifi_steps
                        Brand.SYR_NEOSOFT -> R.string.syr_wifi_steps
                    },
                ),
            )
            if (brand.wifiSetup != WifiSetupKind.DEVICE_MENU) {
                FilledTonalButton(onClick = { SystemScreens.openWifiPicker(context) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(R.drawable.ic_wifi), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_open_wifi))
                }
                Hint(
                    link?.let { (it.ssid ?: stringResource(R.string.setup_current_network)) + (it.gateway?.let { g -> " · ${g.hostAddress}" } ?: "") }
                        ?: stringResource(R.string.setup_no_wifi),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().toggleable(state.confirmed, enabled = link != null, role = Role.Checkbox, onValueChange = viewModel::confirm),
                ) {
                    Checkbox(checked = state.confirmed, onCheckedChange = null, enabled = link != null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.appliance_on_device_wifi), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        when (brand.wifiSetup) {
            WifiSetupKind.DEVICE_MENU -> Button(onClick = { onDone(null) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.action_done))
            }
            WifiSetupKind.DEVICE_PAGE -> {
                Button(onClick = { pageOpen = true }, enabled = state.confirmed, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(R.string.appliance_open_setup_page))
                }
                Hint(stringResource(R.string.appliance_setup_page_hint))
                Hint(stringResource(R.string.appliance_wifi_after))
                FilledTonalButton(onClick = { onDone(null) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_done)) }
            }
            WifiSetupKind.APP -> SyrForm(state, viewModel, onDone)
        }
    }
}

@Composable
private fun SyrForm(state: WifiSetupState, viewModel: ApplianceWifiSetupViewModel, onDone: (String?) -> Unit) {
    var ssid by rememberSaveable { mutableStateOf("") }
    // The password is kept only in memory while this screen is open.
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val ssidBytes = ssid.toByteArray(Charsets.UTF_8).size
    val passwordBytes = password.toByteArray(Charsets.UTF_8).size
    val valid = ssidBytes in 1..32 && (passwordBytes == 0 || passwordBytes in 8..63)
    SectionCard(title = stringResource(R.string.setup_step2_title)) {
        OutlinedTextField(
            value = ssid,
            onValueChange = { ssid = it },
            label = { Text(stringResource(R.string.setup_ssid_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.setup_password_label)) },
            singleLine = true,
            isError = passwordBytes in 1..7 || passwordBytes > 63,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        painterResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                        contentDescription = stringResource(if (visible) R.string.cd_hide_password else R.string.cd_show_password),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Hint(stringResource(R.string.appliance_band_hint))
        if (SyrWifiProvisioner.needsEncoding(ssid) || SyrWifiProvisioner.needsEncoding(password)) {
            StatusLine(StatusKind.WARNING, stringResource(R.string.syr_encoding_warning))
        }
        Button(
            onClick = { viewModel.provisionSyr(ssid, password) },
            enabled = valid && state.confirmed && !state.sending,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (state.sending) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.syr_sending))
            } else {
                Text(stringResource(R.string.syr_send))
            }
        }
    }
    state.error?.let { Banner(StatusKind.ERROR, networkErrorText(it)) }
    when (val result = state.result) {
        is SyrWifiProvisioner.Result.Connected -> {
            Banner(
                StatusKind.SUCCESS,
                result.address?.let { stringResource(R.string.syr_connected, it) } ?: stringResource(R.string.syr_connected_no_address),
            )
            Hint(stringResource(R.string.appliance_wifi_after))
            Button(onClick = { onDone(result.address) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(if (result.address != null) R.string.action_use_address else R.string.action_done))
            }
        }
        SyrWifiProvisioner.Result.LinkLost -> {
            Banner(StatusKind.INFO, stringResource(R.string.syr_link_lost))
            Hint(stringResource(R.string.appliance_wifi_after))
            Button(onClick = { onDone(null) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_done)) }
        }
        SyrWifiProvisioner.Result.NotConnected -> Banner(StatusKind.WARNING, stringResource(R.string.syr_not_connected))
        null -> Unit
    }
}

/**
 * The device's own setup page, shown in-app because its access point has no Internet: while it
 * is open the app's traffic is bound to that Wi-Fi network, so the page does not go out over
 * mobile data. What the user types there goes to the device, never to WaterCare.
 */
@SuppressLint("SetJavaScriptEnabled") // The devices' own setup pages need JavaScript; only their address is loaded.
@Composable
private fun DevicePage(brand: Brand, url: String, network: Network, onClose: () -> Unit) {
    val context = LocalContext.current
    val connectivity = remember { context.getSystemService(ConnectivityManager::class.java) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(network) {
        connectivity.bindProcessToNetwork(network)
        onDispose {
            connectivity.bindProcessToNetwork(null)
            webView?.apply {
                clearCache(true)
                clearFormData()
                clearHistory()
                destroy()
            }
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(end = 16.dp)) {
            IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.cd_close)) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.appliance_open_setup_page), style = MaterialTheme.typography.titleMedium)
                Hint(stringResource(if (brand == Brand.JUDO) R.string.judo_page_hint else R.string.gruenbeck_page_hint))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            private var authAttempts = 0

                            /** Stay on the device: links elsewhere are not opened. */
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                request.url.host != url.toUri().host

                            /** The JUDO module's default web login; a changed login is entered by the user on the next prompt. */
                            override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
                                if (brand == Brand.JUDO && authAttempts++ == 0) {
                                    handler.proceed(JudoDriver.DEFAULT_USER, JudoDriver.DEFAULT_PASSWORD)
                                } else {
                                    handler.cancel()
                                }
                            }
                        }
                        loadUrl(url)
                        webView = this
                    }
                },
            )
        }
    }
}
