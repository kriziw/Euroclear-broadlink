package io.github.kriziw.bl3372setup.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kriziw.bl3372setup.Permissions
import io.github.kriziw.bl3372setup.R
import io.github.kriziw.bl3372setup.SystemScreens
import io.github.kriziw.bl3372setup.broadlink.BroadlinkPackets
import io.github.kriziw.bl3372setup.broadlink.BroadlinkProvisioner.Outcome
import io.github.kriziw.bl3372setup.broadlink.CredentialProblem
import io.github.kriziw.bl3372setup.broadlink.DiscoveredDevice
import io.github.kriziw.bl3372setup.broadlink.SecurityMode
import io.github.kriziw.bl3372setup.network.WifiLink
import io.github.kriziw.bl3372setup.ui.theme.BL3372Theme
import io.github.kriziw.bl3372setup.ui.theme.SuccessGreen
import io.github.kriziw.bl3372setup.ui.theme.WarningAmber

private const val AP_NAME = "WiFi-BL3372"

/** Callbacks from the screen. Kept as one class so previews can pass no-ops. */
class MainActions(
    val openWifiPicker: () -> Unit = {},
    val allowWifiNameCheck: () -> Unit = {},
    val openLocationSettings: () -> Unit = {},
    val openAppSettings: () -> Unit = {},
    val requestLocalNetwork: () -> Unit = {},
    val setManualConfirmation: (Boolean) -> Unit = {},
    val updateForm: ((CredentialsForm) -> CredentialsForm) -> Unit = {},
    val configure: () -> Unit = {},
    val discover: () -> Unit = {},
)

@Composable
fun MainRoute(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val localNetworkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onLocalNetworkPermissionResult(granted)
        val action = pendingAction
        pendingAction = null
        if (granted) action?.invoke()
    }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refreshPermissions() }

    // Returning from the Wi-Fi picker or Settings: re-check permissions and the Location toggle.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose {}
    }

    fun withLocalNetwork(action: () -> Unit) {
        if (state.localNetworkGranted) {
            action()
        } else {
            pendingAction = action
            localNetworkLauncher.launch(Permissions.ACCESS_LOCAL_NETWORK)
        }
    }

    MainScreen(
        state = state,
        actions = MainActions(
            openWifiPicker = { SystemScreens.openWifiPicker(context) },
            allowWifiNameCheck = { locationLauncher.launch(Permissions.LOCATION) },
            openLocationSettings = { SystemScreens.openLocationSettings(context) },
            openAppSettings = { SystemScreens.openAppSettings(context) },
            requestLocalNetwork = { localNetworkLauncher.launch(Permissions.ACCESS_LOCAL_NETWORK) },
            setManualConfirmation = viewModel::setManualConfirmation,
            updateForm = viewModel::updateForm,
            configure = { withLocalNetwork(viewModel::configure) },
            discover = { withLocalNetwork { viewModel.discover() } },
        ),
    )
}

@Composable
fun MainScreen(state: UiState, actions: MainActions) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.app_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header()
            if (Permissions.localNetworkRequired && !state.localNetworkGranted) {
                LocalNetworkCard(state, actions)
            }
            ConnectCard(state, actions)
            CredentialsCard(state, actions)
            ConfigureCard(state, actions)
            DiscoveryCard(state, actions)
            Text(
                text = "Everything stays on this phone: no account, no cloud, no Internet, nothing stored.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Image(
            painter = painterResource(R.drawable.app_logo),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "BL3372 Wi-Fi Setup",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "Local Wi-Fi provisioning for BroadLink BL3372 modules",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LocalNetworkCard(state: UiState, actions: MainActions) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusLine(
                StatusKind.INFO,
                "Android 17 needs the “Nearby devices” permission before an app can talk to devices " +
                    "on the local network, including the module's setup network.",
            )
            if (state.localNetworkDenied) {
                Text(
                    "The permission was declined. Allow it under App info → Permissions → Nearby devices.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = actions.openAppSettings) { Text("Open app settings") }
            } else {
                Button(onClick = actions.requestLocalNetwork) { Text("Allow local network access") }
            }
        }
    }
}

@Composable
private fun ConnectCard(state: UiState, actions: MainActions) {
    StepCard(1, "Connect to the device") {
        val link = state.link
        when (state.apCheck) {
            ApCheck.NO_WIFI -> {
                StatusLine(StatusKind.WARNING, "This phone is not connected to a Wi-Fi network.")
                JoinInstructions()
                OpenWifiButton(actions)
            }
            ApCheck.CONFIRMED_BY_NAME -> {
                StatusLine(StatusKind.SUCCESS, "Connected to “${link?.ssid}”.")
                link?.let { NetworkDetails(it) }
            }
            ApCheck.WRONG_NETWORK_NAME -> {
                StatusLine(StatusKind.ERROR, "Connected to “${link?.ssid}”, not to $AP_NAME.")
                JoinInstructions()
                OpenWifiButton(actions)
            }
            ApCheck.WRONG_NETWORK_HAS_INTERNET -> {
                StatusLine(
                    StatusKind.ERROR,
                    "This Wi-Fi network has working Internet access, so it is not $AP_NAME " +
                        "(the module's setup network never does).",
                )
                JoinInstructions()
                OpenWifiButton(actions)
            }
            ApCheck.NEEDS_MANUAL_CONFIRMATION, ApCheck.CONFIRMED_MANUALLY -> {
                StatusLine(StatusKind.INFO, "Connected to a Wi-Fi network without Internet access.")
                link?.let { NetworkDetails(it) }
                WifiNameHelp(state, actions)
                val confirmed = state.apCheck == ApCheck.CONFIRMED_MANUALLY
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(confirmed, role = Role.Checkbox, onValueChange = actions.setManualConfirmation),
                ) {
                    Checkbox(checked = confirmed, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "I checked in Android's Wi-Fi settings that this phone is connected to $AP_NAME.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TextButton(onClick = actions.openWifiPicker) { Text("Open Wi-Fi networks") }
            }
        }
    }
}

@Composable
private fun WifiNameHelp(state: UiState, actions: MainActions) {
    when {
        !state.preciseLocationGranted -> {
            Hint(
                "Android only tells apps the Wi-Fi name if they have precise Location permission. " +
                    "This app uses it for nothing else and never reads your location. " +
                    "You can also skip this and confirm below.",
            )
            OutlinedButton(onClick = actions.allowWifiNameCheck) { Text("Check Wi-Fi name") }
        }
        !state.locationEnabled -> {
            Hint("Turn on Location so Android can show this app the Wi-Fi name, or confirm below.")
            OutlinedButton(onClick = actions.openLocationSettings) { Text("Location settings") }
        }
        else -> Hint("Android did not reveal the network name. Confirm below if it is $AP_NAME.")
    }
}

@Composable
private fun JoinInstructions() {
    Hint(
        "1. Put the water-treatment controller into Wi-Fi setup mode.\n" +
            "2. Open the Wi-Fi networks and join “$AP_NAME”.\n" +
            "3. If Android says the network has no Internet access, choose to stay connected.\n" +
            "4. Come back to this app.",
    )
}

@Composable
private fun OpenWifiButton(actions: MainActions) {
    Button(onClick = actions.openWifiPicker) {
        Icon(painterResource(R.drawable.ic_wifi), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Open Wi-Fi networks")
    }
}

@Composable
private fun NetworkDetails(link: WifiLink) {
    val parts = buildList {
        link.address?.let { add("Phone ${it.hostAddress}") }
        link.gateway?.let { add("Gateway ${it.hostAddress}") }
        add(if (link.hasValidatedInternet) "Internet available" else "No Internet")
    }
    Text(
        parts.joinToString("  ·  "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CredentialsCard(state: UiState, actions: MainActions) {
    val form = state.form
    var passwordVisible by remember { mutableStateOf(false) }
    StepCard(2, "Your home Wi-Fi") {
        val ssidBytes = form.ssid.toByteArray(Charsets.UTF_8).size
        val ssidError = when {
            form.isDeviceApName -> "That is the module's own setup network. Enter your home Wi-Fi name."
            form.problem is CredentialProblem.SsidTooLong -> "Too long: $ssidBytes bytes (max ${BroadlinkPackets.MAX_SSID_BYTES})."
            else -> null
        }
        OutlinedTextField(
            value = form.ssid,
            onValueChange = { value -> actions.updateForm { it.copy(ssid = value) } },
            label = { Text("Wi-Fi name (SSID)") },
            singleLine = true,
            isError = ssidError != null,
            supportingText = { Text(ssidError ?: "$ssidBytes / ${BroadlinkPackets.MAX_SSID_BYTES} bytes · case-sensitive") },
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        val passwordBytes = form.password.toByteArray(Charsets.UTF_8).size
        val passwordError = when (val problem = form.problem) {
            is CredentialProblem.PasswordTooLong ->
                "Too long: ${problem.bytes} bytes. The BroadLink setup packet carries at most " +
                    "${BroadlinkPackets.MAX_PASSWORD_BYTES} (the official app has the same limit)."
            is CredentialProblem.PasswordTooShort ->
                if (form.password.isEmpty()) null else "WPA passwords have at least ${BroadlinkPackets.MIN_WPA_PASSWORD_BYTES} characters."
            else -> null
        }
        OutlinedTextField(
            value = if (form.security.usesPassword) form.password else "",
            onValueChange = { value -> actions.updateForm { it.copy(password = value) } },
            label = { Text(if (form.security.usesPassword) "Password" else "Password (not used for open networks)") },
            enabled = form.security.usesPassword,
            singleLine = true,
            isError = passwordError != null,
            supportingText = {
                Text(passwordError ?: "$passwordBytes / ${BroadlinkPackets.MAX_PASSWORD_BYTES} bytes · case-sensitive")
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        painterResource(if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Security", style = MaterialTheme.typography.labelLarge)
        val modes = listOf(
            SecurityMode.OPEN to "Open",
            SecurityMode.WPA to "WPA",
            SecurityMode.WPA2 to "WPA2",
            SecurityMode.WPA_WPA2 to "WPA/WPA2",
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = form.security == mode,
                    onClick = { actions.updateForm { it.copy(security = mode) } },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    icon = {},
                    label = { Text(label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }
        Hint("WPA2 suits almost every router. If the module does not join, try WPA/WPA2.")

        Hint("The BL3372 only supports 2.4 GHz Wi-Fi. Use your router's 2.4 GHz network name; 5 GHz-only and WPA3-only networks will not work.")
        if (form.ssid != form.ssid.trim()) {
            StatusLine(StatusKind.WARNING, "The name starts or ends with a space. Check that this is intended.")
        }
        if (form.ssid.any { it.code > 0x7F }) {
            StatusLine(StatusKind.INFO, "The name contains non-ASCII characters; it is sent as UTF-8.")
        }
        if (form.security.usesPassword && form.password.any { it.code !in 0x20..0x7E }) {
            StatusLine(StatusKind.WARNING, "The password contains non-ASCII characters. WPA passwords are normally plain ASCII; the module may reject it.")
        }
    }
}

@Composable
private fun ConfigureCard(state: UiState, actions: MainActions) {
    StepCard(3, "Configure the device") {
        Hint(
            "Sends the Wi-Fi name and password straight to the module over the $AP_NAME connection. " +
                "The BroadLink setup protocol is not encrypted, so do this close to the device and finish promptly.",
        )
        if (!state.canConfigure && !state.isSending) {
            val reason = when {
                !state.apCheck.isConfirmed -> "Connect to $AP_NAME first (step 1)."
                state.form.isDeviceApName || state.form.problem != null -> "Fill in your home Wi-Fi details (step 2)."
                else -> null
            }
            reason?.let { StatusLine(StatusKind.INFO, it) }
        }
        Button(
            onClick = actions.configure,
            enabled = state.canConfigure,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isSending) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(10.dp))
                Text("Sending configuration…")
            } else {
                Text(if (state.provisioning is ProvisioningState.Sent) "Send again" else "Configure Device")
            }
        }
        ProvisioningResult(state.provisioning, actions)
    }
}

@Composable
private fun ProvisioningResult(provisioning: ProvisioningState, actions: MainActions) {
    when (provisioning) {
        ProvisioningState.Idle -> Unit
        is ProvisioningState.Sending -> StatusLine(
            StatusKind.PROGRESS,
            if (provisioning.round == 0) {
                "Sending configuration…"
            } else {
                "Sending configuration… (round ${provisioning.round} of ${provisioning.maxRounds})"
            },
        )
        is ProvisioningState.Sent -> {
            HorizontalDivider()
            when (val outcome = provisioning.outcome) {
                is Outcome.Acknowledged -> StatusLine(
                    StatusKind.SUCCESS,
                    "The module acknowledged the settings (reply from ${outcome.from.hostAddress}).",
                )
                is Outcome.NotAcknowledged -> StatusLine(
                    StatusKind.INFO,
                    "Settings sent ${outcome.roundsSent}×. No acknowledgement came back, which many modules never " +
                        "send, so this alone does not mean failure.",
                )
                is Outcome.LinkLostAfterSend -> StatusLine(
                    StatusKind.SUCCESS,
                    "Settings sent, and the $AP_NAME connection dropped right after.",
                )
            }
            val target = provisioning.targetSsid
            when (val watch = provisioning.apWatch) {
                ApWatch.Watching -> StatusLine(
                    StatusKind.PROGRESS,
                    "Watching $AP_NAME… Once the module accepts the settings it reboots, its setup network " +
                        "disappears and it joins “$target”.",
                )
                is ApWatch.Gone -> StatusLine(
                    StatusKind.SUCCESS,
                    "$AP_NAME disappeared ${watch.afterSeconds} s after sending. This most likely means the module " +
                        "rebooted and is now joining “$target”. Reconnect this phone to “$target” to find it (step 4).",
                )
                ApWatch.StillConnected -> {
                    StatusLine(
                        StatusKind.WARNING,
                        "This phone is still connected to $AP_NAME after 90 s. The module may not have accepted the settings.",
                    )
                    Hint(
                        "• Check the Wi-Fi name and password (both are case-sensitive).\n" +
                            "• Make sure the router offers 2.4 GHz and is not WPA3-only.\n" +
                            "• Try the security setting WPA/WPA2.\n" +
                            "• Some BroadLink firmware fails with special characters in the password; a guest " +
                            "network with a letters-and-digits password can help.\n" +
                            "• Put the controller back into Wi-Fi setup mode, reconnect and send again.",
                    )
                }
            }
        }
        is ProvisioningState.Failed -> NetworkErrorLine("Sending failed.", provisioning.error, actions)
    }
}

@Composable
private fun DiscoveryCard(state: UiState, actions: MainActions) {
    StepCard(4, "Find the device on your network") {
        Hint(
            "After the module has joined your Wi-Fi, connect this phone to the same network. The app then " +
                "looks for it with BroadLink's local discovery broadcast.",
        )
        val link = state.link
        when {
            link == null -> {
                StatusLine(StatusKind.INFO, "Connect this phone to your home Wi-Fi.")
                OpenWifiButton(actions)
            }
            link.isSetupAp || state.apCheck == ApCheck.CONFIRMED_MANUALLY -> {
                StatusLine(StatusKind.INFO, "This phone is still on the setup network. Switch to your home Wi-Fi first.")
                TextButton(onClick = actions.openWifiPicker) { Text("Open Wi-Fi networks") }
            }
            else -> Text(
                "Searching on “${link.ssid ?: "the current Wi-Fi network"}”" +
                    (link.address?.let { " (${it.hostAddress})" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = actions.discover, enabled = state.canDiscover, modifier = Modifier.fillMaxWidth()) {
            if (state.isSearching) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Searching…")
            } else {
                Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Search for device")
            }
        }
        when (val discovery = state.discovery) {
            DiscoveryState.Idle -> Unit
            is DiscoveryState.Searching -> {
                StatusLine(StatusKind.PROGRESS, "Searching… (up to ${discovery.seconds} s)")
                discovery.found.forEach { DeviceRow(it) }
            }
            is DiscoveryState.Done -> {
                if (discovery.found.isEmpty()) {
                    StatusLine(
                        StatusKind.WARNING,
                        "No BroadLink device answered. The module can take a minute or two to join; search again. " +
                            "Your router's list of connected clients is another place to look.",
                    )
                } else {
                    discovery.found.forEach { DeviceRow(it) }
                }
            }
            is DiscoveryState.Failed -> NetworkErrorLine("Search failed.", discovery.error, actions)
        }
    }
}

@Composable
private fun DeviceRow(device: DiscoveredDevice) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        StatusLine(StatusKind.SUCCESS, "Device found at ${device.address.hostAddress}")
        val type = "0x%04X".format(device.deviceType)
        Text(
            if (device.isRunxinBl3372) "Runxin controller with BroadLink BL3372 (type $type)" else "BroadLink device (type $type)",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "MAC ${device.mac}" + if (device.name.isNotBlank()) "  ·  “${device.name}”" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (device.isLocked) {
            Text(
                "Reports itself as locked (local control disabled by the vendor app).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NetworkErrorLine(prefix: String, error: NetworkError, actions: MainActions) {
    when (error) {
        NetworkError.LocalNetworkBlocked -> {
            StatusLine(
                StatusKind.ERROR,
                "$prefix Android blocked local network access. Allow “Nearby devices” for this app and turn off " +
                    "any VPN, then try again.",
            )
            OutlinedButton(onClick = actions.openAppSettings) { Text("Open app settings") }
        }
        NetworkError.NotConnected ->
            StatusLine(StatusKind.ERROR, "$prefix The Wi-Fi connection is no longer available. Reconnect and try again.")
        is NetworkError.Other -> StatusLine(StatusKind.ERROR, "$prefix ${error.detail}")
    }
}

@Composable
private fun StepCard(number: Int, title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                ) {
                    Text("$number", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

private enum class StatusKind(@param:DrawableRes val icon: Int?) {
    SUCCESS(R.drawable.ic_check_circle),
    WARNING(R.drawable.ic_warning),
    ERROR(R.drawable.ic_error),
    INFO(R.drawable.ic_info),
    PROGRESS(null),
}

@Composable
private fun StatusLine(kind: StatusKind, text: String) {
    val tint = when (kind) {
        StatusKind.SUCCESS -> SuccessGreen
        StatusKind.WARNING -> WarningAmber
        StatusKind.ERROR -> MaterialTheme.colorScheme.error
        StatusKind.INFO, StatusKind.PROGRESS -> MaterialTheme.colorScheme.primary
    }
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (kind.icon != null) {
                Icon(painterResource(kind.icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            } else {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = tint)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun MainScreenPreview() {
    BL3372Theme {
        MainScreen(
            state = UiState(form = CredentialsForm(ssid = "MyHomeWiFi", password = "correct-horse-42")),
            actions = MainActions(),
        )
    }
}
