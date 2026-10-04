package io.github.kriziw.bl3372setup.ui.setup

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import io.github.kriziw.bl3372setup.broadlink.Ipv4
import io.github.kriziw.bl3372setup.broadlink.SecurityMode
import io.github.kriziw.bl3372setup.network.WifiLink
import io.github.kriziw.bl3372setup.ui.common.AppBackground
import io.github.kriziw.bl3372setup.ui.common.Hint
import io.github.kriziw.bl3372setup.ui.common.PrivacyFooter
import io.github.kriziw.bl3372setup.ui.common.SectionCard
import io.github.kriziw.bl3372setup.ui.common.StatusKind
import io.github.kriziw.bl3372setup.ui.common.StatusLine
import io.github.kriziw.bl3372setup.ui.common.networkErrorText

private const val AP_NAME = "WiFi-BL3372"

/** Callbacks from the setup screen. Kept as one class so previews can pass no-ops. */
class SetupActions(
    val openWifiPicker: () -> Unit = {},
    val allowWifiNameCheck: () -> Unit = {},
    val openLocationSettings: () -> Unit = {},
    val openAppSettings: () -> Unit = {},
    val requestLocalNetwork: () -> Unit = {},
    val setManualConfirmation: (Boolean) -> Unit = {},
    val updateForm: ((CredentialsForm) -> CredentialsForm) -> Unit = {},
    val configure: () -> Unit = {},
    val searchThisNetwork: () -> Unit = {},
    val connectToAddress: (String) -> Unit = {},
    val scanSubnet: (String) -> Unit = {},
    val openDevice: (DiscoveredDevice) -> Unit = {},
)

/** The wizard pages. The process is unchanged; each step just gets its own screen. */
enum class SetupStep { CONNECT, WIFI, CONFIGURE, FIND }

@Composable
fun SetupRoute(
    initialStep: SetupStep,
    savedMacs: Set<String>,
    onOpenDevice: (DiscoveredDevice) -> Unit,
    onExit: () -> Unit,
    viewModel: SetupViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableStateOf(initialStep) }
    BackHandler(enabled = step != initialStep && step.ordinal > 0) { step = SetupStep.entries[step.ordinal - 1] }

    // Once the module's access point disappears, the next useful thing is finding the device.
    val apGone = (state.provisioning as? ProvisioningState.Sent)?.apWatch is ApWatch.Gone
    LaunchedEffect(apGone) { if (apGone) step = SetupStep.FIND }
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val localNetworkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onLocalNetworkPermissionResult(granted)
        val action = pendingAction
        pendingAction = null
        if (granted) action?.invoke()
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.refreshPermissions()
    }

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

    SetupScreen(
        state = state,
        step = step,
        standaloneFind = initialStep == SetupStep.FIND,
        savedMacs = savedMacs,
        onStepChange = { step = it },
        onExit = onExit,
        actions = SetupActions(
            openWifiPicker = { SystemScreens.openWifiPicker(context) },
            allowWifiNameCheck = { locationLauncher.launch(Permissions.LOCATION) },
            openLocationSettings = { SystemScreens.openLocationSettings(context) },
            openAppSettings = { SystemScreens.openAppSettings(context) },
            requestLocalNetwork = { localNetworkLauncher.launch(Permissions.ACCESS_LOCAL_NETWORK) },
            setManualConfirmation = viewModel::setManualConfirmation,
            updateForm = viewModel::updateForm,
            configure = { withLocalNetwork(viewModel::configure) },
            searchThisNetwork = { withLocalNetwork { viewModel.searchThisNetwork() } },
            connectToAddress = { text -> withLocalNetwork { viewModel.connectToAddress(text) } },
            scanSubnet = { text -> withLocalNetwork { viewModel.scanSubnet(text) } },
            openDevice = onOpenDevice,
        ),
    )
}

@Composable
fun SetupScreen(
    state: SetupUiState,
    step: SetupStep,
    savedMacs: Set<String>,
    actions: SetupActions,
    standaloneFind: Boolean = false,
    onStepChange: (SetupStep) -> Unit = {},
    onExit: () -> Unit = {},
) {
    // "Add a device already on my network" only needs the find page, without wizard chrome.
    val standalone = standaloneFind && step == SetupStep.FIND
    AppBackground(
        topBar = { SetupTopBar(step, standalone, onExit) },
        bottomBar = { SetupNavigation(state, step, standalone, onStepChange, onExit) },
    ) {
        if (step == SetupStep.CONNECT) Header()
        if (Permissions.localNetworkRequired && !state.localNetworkGranted &&
            (step == SetupStep.CONFIGURE || step == SetupStep.FIND)
        ) {
            LocalNetworkCard(state.localNetworkDenied, actions.requestLocalNetwork, actions.openAppSettings)
        }
        AnimatedContent(targetState = step, label = "setup-step") { current ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (current) {
                    SetupStep.CONNECT -> ConnectCard(state, actions)
                    SetupStep.WIFI -> CredentialsCard(state, actions)
                    SetupStep.CONFIGURE -> ConfigureCard(state, actions)
                    SetupStep.FIND -> DiscoveryCard(state, savedMacs, actions)
                }
            }
        }
        PrivacyFooter()
    }
}

@Composable
private fun SetupTopBar(step: SetupStep, standalone: Boolean, onExit: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.cd_back)) }
            Text(
                if (standalone) stringResource(R.string.home_add_title)
                else stringResource(R.string.setup_step_of, step.ordinal + 1, SetupStep.entries.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        if (!standalone) {
            LinearProgressIndicator(
                progress = { (step.ordinal + 1f) / SetupStep.entries.size },
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp),
            )
        }
    }
}

/** Back / Next. "Next" only unlocks when the current step is actually complete. */
@Composable
private fun SetupNavigation(
    state: SetupUiState,
    step: SetupStep,
    standalone: Boolean,
    onStepChange: (SetupStep) -> Unit,
    onExit: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (standalone) {
            // Nothing to go back to inside this flow; the top bar returns home.
        } else if (step.ordinal > 0) {
            TextButton(onClick = { onStepChange(SetupStep.entries[step.ordinal - 1]) }) { Text(stringResource(R.string.action_back)) }
        } else {
            TextButton(onClick = { onStepChange(SetupStep.FIND) }) { Text(stringResource(R.string.setup_skip_to_find)) }
        }
        Spacer(Modifier.weight(1f))
        when (step) {
            SetupStep.CONNECT -> Button(onClick = { onStepChange(SetupStep.WIFI) }, enabled = state.apCheck.isConfirmed) {
                Text(stringResource(R.string.action_next))
            }
            SetupStep.WIFI -> Button(
                onClick = { onStepChange(SetupStep.CONFIGURE) },
                enabled = state.form.problem == null && !state.form.isDeviceApName,
            ) { Text(stringResource(R.string.action_next)) }
            SetupStep.CONFIGURE -> Button(
                onClick = { onStepChange(SetupStep.FIND) },
                enabled = state.provisioning is ProvisioningState.Sent,
            ) { Text(stringResource(R.string.action_next)) }
            SetupStep.FIND -> Button(onClick = onExit) { Text(stringResource(R.string.action_done)) }
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Image(painter = painterResource(R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(64.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(R.string.setup_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Android 17+: the runtime permission needed for any local-network traffic. */
@Composable
fun LocalNetworkCard(denied: Boolean, request: () -> Unit, openAppSettings: () -> Unit) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusLine(StatusKind.INFO, stringResource(R.string.local_network_explanation))
            if (denied) {
                Hint(stringResource(R.string.local_network_declined))
                OutlinedButton(onClick = openAppSettings) { Text(stringResource(R.string.action_open_app_settings)) }
            } else {
                Button(onClick = request) { Text(stringResource(R.string.action_allow_local_network)) }
            }
        }
    }
}

@Composable
private fun ConnectCard(state: SetupUiState, actions: SetupActions) {
    SectionCard(stringResource(R.string.setup_step1_title), number = 1) {
        val link = state.link
        when (state.apCheck) {
            ApCheck.NO_WIFI -> {
                StatusLine(StatusKind.WARNING, stringResource(R.string.setup_no_wifi))
                JoinInstructions()
                OpenWifiButton(actions)
            }
            ApCheck.CONFIRMED_BY_NAME -> {
                StatusLine(StatusKind.SUCCESS, stringResource(R.string.setup_connected_to, link?.ssid.orEmpty()))
                link?.let { NetworkDetails(it) }
            }
            ApCheck.WRONG_NETWORK_NAME -> {
                StatusLine(StatusKind.ERROR, stringResource(R.string.setup_wrong_network, link?.ssid.orEmpty(), AP_NAME))
                JoinInstructions()
                OpenWifiButton(actions)
            }
            ApCheck.WRONG_NETWORK_HAS_INTERNET -> {
                StatusLine(StatusKind.ERROR, stringResource(R.string.setup_network_has_internet, AP_NAME))
                JoinInstructions()
                OpenWifiButton(actions)
            }
            ApCheck.NEEDS_MANUAL_CONFIRMATION, ApCheck.CONFIRMED_MANUALLY -> {
                StatusLine(StatusKind.INFO, stringResource(R.string.setup_network_without_internet))
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
                    Text(stringResource(R.string.setup_manual_confirmation, AP_NAME), style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = actions.openWifiPicker) { Text(stringResource(R.string.action_open_wifi)) }
            }
        }
    }
}

@Composable
private fun WifiNameHelp(state: SetupUiState, actions: SetupActions) {
    when {
        !state.preciseLocationGranted -> {
            Hint(stringResource(R.string.setup_location_explanation))
            OutlinedButton(onClick = actions.allowWifiNameCheck) { Text(stringResource(R.string.action_check_wifi_name)) }
        }
        !state.locationEnabled -> {
            Hint(stringResource(R.string.setup_location_off))
            OutlinedButton(onClick = actions.openLocationSettings) { Text(stringResource(R.string.action_location_settings)) }
        }
        else -> Hint(stringResource(R.string.setup_ssid_hidden, AP_NAME))
    }
}

@Composable
private fun JoinInstructions() {
    Hint(stringResource(R.string.setup_join_instructions, AP_NAME))
}

@Composable
private fun OpenWifiButton(actions: SetupActions) {
    Button(onClick = actions.openWifiPicker) {
        Icon(painterResource(R.drawable.ic_wifi), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.action_open_wifi))
    }
}

@Composable
private fun NetworkDetails(link: WifiLink) {
    val parts = buildList {
        link.address?.let { add(stringResource(R.string.setup_detail_phone, it.hostAddress.orEmpty())) }
        link.gateway?.let { add(stringResource(R.string.setup_detail_gateway, it.hostAddress.orEmpty())) }
        add(stringResource(if (link.hasValidatedInternet) R.string.setup_detail_internet else R.string.setup_detail_no_internet))
    }
    Text(parts.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CredentialsCard(state: SetupUiState, actions: SetupActions) {
    val form = state.form
    var passwordVisible by remember { mutableStateOf(false) }
    SectionCard(stringResource(R.string.setup_step2_title), number = 2) {
        val ssidBytes = form.ssid.toByteArray(Charsets.UTF_8).size
        val ssidError = when {
            form.isDeviceApName -> stringResource(R.string.setup_ssid_is_device_ap)
            form.problem is CredentialProblem.SsidTooLong -> stringResource(R.string.setup_too_long, ssidBytes, BroadlinkPackets.MAX_SSID_BYTES)
            else -> null
        }
        OutlinedTextField(
            value = form.ssid,
            onValueChange = { value -> actions.updateForm { it.copy(ssid = value) } },
            label = { Text(stringResource(R.string.setup_ssid_label)) },
            singleLine = true,
            isError = ssidError != null,
            supportingText = { Text(ssidError ?: stringResource(R.string.setup_bytes_counter, ssidBytes, BroadlinkPackets.MAX_SSID_BYTES)) },
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        val passwordBytes = form.password.toByteArray(Charsets.UTF_8).size
        val passwordError = when (val problem = form.problem) {
            is CredentialProblem.PasswordTooLong ->
                stringResource(R.string.setup_password_too_long, problem.bytes, BroadlinkPackets.MAX_PASSWORD_BYTES)
            is CredentialProblem.PasswordTooShort ->
                if (form.password.isEmpty()) null else stringResource(R.string.setup_password_too_short, BroadlinkPackets.MIN_WPA_PASSWORD_BYTES)
            else -> null
        }
        OutlinedTextField(
            value = if (form.security.usesPassword) form.password else "",
            onValueChange = { value -> actions.updateForm { it.copy(password = value) } },
            label = { Text(stringResource(if (form.security.usesPassword) R.string.setup_password_label else R.string.setup_password_unused)) },
            enabled = form.security.usesPassword,
            singleLine = true,
            isError = passwordError != null,
            supportingText = {
                Text(passwordError ?: stringResource(R.string.setup_bytes_counter, passwordBytes, BroadlinkPackets.MAX_PASSWORD_BYTES))
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        painterResource(if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                        contentDescription = stringResource(if (passwordVisible) R.string.cd_hide_password else R.string.cd_show_password),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(stringResource(R.string.setup_security_label), style = MaterialTheme.typography.labelLarge)
        val modes = listOf(
            SecurityMode.OPEN to stringResource(R.string.setup_security_open),
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
        Hint(stringResource(R.string.setup_security_hint))
        Hint(stringResource(R.string.setup_band_hint))
        if (form.ssid != form.ssid.trim()) StatusLine(StatusKind.WARNING, stringResource(R.string.setup_ssid_spaces))
        if (form.ssid.any { it.code > 0x7F }) StatusLine(StatusKind.INFO, stringResource(R.string.setup_ssid_non_ascii))
        if (form.security.usesPassword && form.password.any { it.code !in 0x20..0x7E }) {
            StatusLine(StatusKind.WARNING, stringResource(R.string.setup_password_non_ascii))
        }
    }
}

@Composable
private fun ConfigureCard(state: SetupUiState, actions: SetupActions) {
    SectionCard(stringResource(R.string.setup_step3_title), number = 3) {
        Hint(stringResource(R.string.setup_configure_explanation, AP_NAME))
        if (!state.canConfigure && !state.isSending) {
            val reason = when {
                !state.apCheck.isConfirmed -> stringResource(R.string.setup_reason_connect_first, AP_NAME)
                state.form.isDeviceApName || state.form.problem != null -> stringResource(R.string.setup_reason_fill_details)
                else -> null
            }
            reason?.let { StatusLine(StatusKind.INFO, it) }
        }
        Button(onClick = actions.configure, enabled = state.canConfigure, modifier = Modifier.fillMaxWidth()) {
            if (state.isSending) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.setup_sending))
            } else {
                Text(stringResource(if (state.provisioning is ProvisioningState.Sent) R.string.action_send_again else R.string.action_configure))
            }
        }
        ProvisioningResult(state.provisioning, actions)
    }
}

@Composable
private fun ProvisioningResult(provisioning: ProvisioningState, actions: SetupActions) {
    when (provisioning) {
        ProvisioningState.Idle -> Unit
        is ProvisioningState.Sending -> StatusLine(
            StatusKind.PROGRESS,
            if (provisioning.round == 0) {
                stringResource(R.string.setup_sending)
            } else {
                stringResource(R.string.setup_sending_round, provisioning.round, provisioning.maxRounds)
            },
        )
        is ProvisioningState.Sent -> {
            HorizontalDivider()
            when (val outcome = provisioning.outcome) {
                is Outcome.Acknowledged -> StatusLine(StatusKind.SUCCESS, stringResource(R.string.setup_acknowledged, outcome.from.hostAddress.orEmpty()))
                is Outcome.NotAcknowledged -> StatusLine(StatusKind.INFO, stringResource(R.string.setup_not_acknowledged, outcome.roundsSent))
                is Outcome.LinkLostAfterSend -> StatusLine(StatusKind.SUCCESS, stringResource(R.string.setup_link_lost_after_send, AP_NAME))
            }
            val target = provisioning.targetSsid
            when (val watch = provisioning.apWatch) {
                ApWatch.Watching -> StatusLine(StatusKind.PROGRESS, stringResource(R.string.setup_watching_ap, AP_NAME, target))
                is ApWatch.Gone -> StatusLine(StatusKind.SUCCESS, stringResource(R.string.setup_ap_gone, AP_NAME, watch.afterSeconds, target))
                ApWatch.StillConnected -> {
                    StatusLine(StatusKind.WARNING, stringResource(R.string.setup_ap_still_up, AP_NAME))
                    Hint(stringResource(R.string.setup_troubleshooting))
                }
            }
        }
        is ProvisioningState.Failed -> {
            StatusLine(StatusKind.ERROR, stringResource(R.string.setup_sending_failed) + " " + networkErrorText(provisioning.error))
            if (provisioning.error == io.github.kriziw.bl3372setup.network.NetworkError.LocalNetworkBlocked) {
                OutlinedButton(onClick = actions.openAppSettings) { Text(stringResource(R.string.action_open_app_settings)) }
            }
        }
    }
}

@Composable
private fun DiscoveryCard(state: SetupUiState, savedMacs: Set<String>, actions: SetupActions) {
    var showOtherNetwork by rememberSaveable { mutableStateOf(false) }
    var address by rememberSaveable { mutableStateOf("") }
    var subnet by rememberSaveable(state.link?.address) { mutableStateOf(defaultSubnet(state.link)) }

    SectionCard(stringResource(R.string.setup_step4_title), number = 4) {
        Hint(stringResource(R.string.setup_discovery_explanation))
        val link = state.link
        when {
            link == null -> {
                StatusLine(StatusKind.INFO, stringResource(R.string.setup_connect_home_wifi))
                OpenWifiButton(actions)
            }
            link.isSetupAp || state.apCheck == ApCheck.CONFIRMED_MANUALLY -> {
                StatusLine(StatusKind.INFO, stringResource(R.string.setup_still_on_ap))
                TextButton(onClick = actions.openWifiPicker) { Text(stringResource(R.string.action_open_wifi)) }
            }
            else -> Hint(
                stringResource(R.string.setup_searching_on, link.ssid ?: stringResource(R.string.setup_current_network)) +
                    (link.address?.let { " (${it.hostAddress})" } ?: ""),
            )
        }
        OutlinedButton(onClick = actions.searchThisNetwork, enabled = state.canDiscover, modifier = Modifier.fillMaxWidth()) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_search_network))
        }

        TextButton(onClick = { showOtherNetwork = !showOtherNetwork }) {
            Text(stringResource(R.string.setup_other_vlan_toggle))
        }
        if (showOtherNetwork) {
            Hint(stringResource(R.string.setup_vlan_explanation))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.setup_address_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { actions.connectToAddress(address) }),
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { actions.connectToAddress(address) }, enabled = state.canDiscover && address.isNotBlank()) {
                    Text(stringResource(R.string.action_connect))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = subnet,
                    onValueChange = { subnet = it },
                    label = { Text(stringResource(R.string.setup_subnet_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { actions.scanSubnet(subnet) }),
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { actions.scanSubnet(subnet) }, enabled = state.canDiscover && subnet.isNotBlank()) {
                    Text(stringResource(R.string.action_scan))
                }
            }
        }

        when (val discovery = state.discovery) {
            DiscoveryState.Idle -> Unit
            is DiscoveryState.Searching -> {
                StatusLine(StatusKind.PROGRESS, stringResource(R.string.setup_searching_seconds, discovery.seconds))
                discovery.found.forEach { DeviceRow(it, it.mac in savedMacs, actions.openDevice) }
            }
            is DiscoveryState.Done -> {
                if (discovery.found.isEmpty()) {
                    StatusLine(
                        StatusKind.WARNING,
                        stringResource(if (discovery.mode == SearchMode.BROADCAST) R.string.setup_nothing_found_broadcast else R.string.setup_nothing_found_unicast),
                    )
                } else {
                    discovery.found.forEach { DeviceRow(it, it.mac in savedMacs, actions.openDevice) }
                }
            }
            is DiscoveryState.Failed -> StatusLine(StatusKind.ERROR, stringResource(R.string.setup_search_failed) + " " + networkErrorText(discovery.error))
            DiscoveryState.InvalidAddress -> StatusLine(StatusKind.ERROR, stringResource(R.string.setup_invalid_address))
            DiscoveryState.InvalidSubnet -> StatusLine(StatusKind.ERROR, stringResource(R.string.setup_invalid_subnet, Ipv4.MIN_SCAN_PREFIX))
        }
    }
}

private fun defaultSubnet(link: WifiLink?): String =
    link?.address?.let { Ipv4.surrounding24(it).toString() } ?: "192.168.1.0/24"

@Composable
private fun DeviceRow(device: DiscoveredDevice, saved: Boolean, open: (DiscoveredDevice) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        StatusLine(StatusKind.SUCCESS, stringResource(R.string.setup_device_found_at, device.address.hostAddress.orEmpty()))
        val type = "0x%04X".format(device.deviceType)
        Text(
            stringResource(if (device.isRunxinBl3372) R.string.device_type_runxin else R.string.device_type_other, type),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "MAC ${device.mac}" + if (device.name.isNotBlank()) "  ·  “${device.name}”" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (device.isLocked) {
            Text(stringResource(R.string.setup_device_locked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = { open(device) }, modifier = Modifier.padding(top = 6.dp)) {
            Text(stringResource(if (saved) R.string.action_open else R.string.action_save_and_open))
        }
    }
}
