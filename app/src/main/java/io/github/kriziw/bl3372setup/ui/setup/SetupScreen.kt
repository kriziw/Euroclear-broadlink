package io.github.kriziw.bl3372setup.ui.setup

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import io.github.kriziw.bl3372setup.network.NetworkError
import io.github.kriziw.bl3372setup.network.WifiLink
import io.github.kriziw.bl3372setup.ui.common.AppScaffold
import io.github.kriziw.bl3372setup.ui.common.BackButton
import io.github.kriziw.bl3372setup.ui.common.Banner
import io.github.kriziw.bl3372setup.ui.common.BannerAction
import io.github.kriziw.bl3372setup.ui.common.ExpandableCard
import io.github.kriziw.bl3372setup.ui.common.Hint
import io.github.kriziw.bl3372setup.ui.common.IconBadge
import io.github.kriziw.bl3372setup.ui.common.PrivacyFooter
import io.github.kriziw.bl3372setup.ui.common.SectionCard
import io.github.kriziw.bl3372setup.ui.common.StatusKind
import io.github.kriziw.bl3372setup.ui.common.StatusLine
import io.github.kriziw.bl3372setup.ui.common.networkErrorText

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
    AppScaffold(
        title = if (standalone) {
            stringResource(R.string.home_add_title)
        } else {
            stringResource(R.string.setup_step_of, step.ordinal + 1, SetupStep.entries.size)
        },
        navigation = {
            if (standalone) BackButton(onExit) else BackButton(onExit, R.drawable.ic_close, stringResource(R.string.cd_close))
        },
        belowTopBar = { if (!standalone) StepIndicator(step) },
        bottomBar = { SetupNavigation(state, step, standalone, onStepChange, onExit) },
    ) {
        if (Permissions.localNetworkRequired && !state.localNetworkGranted &&
            (step == SetupStep.CONFIGURE || step == SetupStep.FIND)
        ) {
            LocalNetworkCard(state.localNetworkDenied, actions.requestLocalNetwork, actions.openAppSettings)
        }
        AnimatedContent(targetState = step, label = "setup-step") { current ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        when (current) {
                            SetupStep.CONNECT -> R.string.setup_step1_title
                            SetupStep.WIFI -> R.string.setup_step2_title
                            SetupStep.CONFIGURE -> R.string.setup_step3_title
                            SetupStep.FIND -> R.string.setup_step4_title
                        },
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
                )
                when (current) {
                    SetupStep.CONNECT -> ConnectStep(state, actions)
                    SetupStep.WIFI -> CredentialsStep(state, actions)
                    SetupStep.CONFIGURE -> {
                        if (!state.apCheck.isConfirmed) ConnectStep(state, actions)
                        ConfigureStep(state, actions)
                    }
                    SetupStep.FIND -> DiscoveryStep(state, savedMacs, actions)
                }
            }
        }
    }
}

/** One segment per step, filled up to the current one. */
@Composable
private fun StepIndicator(step: SetupStep) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        SetupStep.entries.forEach {
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(
                        if (it.ordinal <= step.ordinal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    ),
            )
        }
    }
}

/** Back / Next, pinned to the bottom. "Next" only unlocks when the current step is actually complete. */
@Composable
private fun SetupNavigation(
    state: SetupUiState,
    step: SetupStep,
    standalone: Boolean,
    onStepChange: (SetupStep) -> Unit,
    onExit: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (standalone) {
                    // Nothing to go back to inside this flow; the top bar returns home.
                } else if (step.ordinal > 0) {
                    TextButton(onClick = { onStepChange(SetupStep.entries[step.ordinal - 1]) }) { Text(stringResource(R.string.action_back)) }
                } else {
                    TextButton(onClick = { onStepChange(SetupStep.FIND) }) { Text(stringResource(R.string.setup_skip_to_find)) }
                }
                Spacer(Modifier.weight(1f))
                val next = Modifier.height(48.dp)
                when (step) {
                    SetupStep.CONNECT -> Button(onClick = { onStepChange(SetupStep.WIFI) }, enabled = state.apCheck.isConfirmed, modifier = next) {
                        Text(stringResource(R.string.action_next))
                    }
                    SetupStep.WIFI -> Button(
                        onClick = { onStepChange(SetupStep.CONFIGURE) },
                        enabled = state.form.problem == null && !state.form.isDeviceApName,
                        modifier = next,
                    ) { Text(stringResource(R.string.action_next)) }
                    SetupStep.CONFIGURE -> Button(
                        onClick = { onStepChange(SetupStep.FIND) },
                        enabled = state.provisioning is ProvisioningState.Sent,
                        modifier = next,
                    ) { Text(stringResource(R.string.action_next)) }
                    SetupStep.FIND -> Button(onClick = onExit, modifier = next) { Text(stringResource(R.string.action_done)) }
                }
            }
        }
    }
}

/** Android 17+: the runtime permission needed for any local-network traffic. */
@Composable
fun LocalNetworkCard(denied: Boolean, request: () -> Unit, openAppSettings: () -> Unit) {
    val text = stringResource(R.string.local_network_explanation) +
        if (denied) "\n\n" + stringResource(R.string.local_network_declined) else ""
    Banner(StatusKind.INFO, text) {
        if (denied) {
            BannerAction(stringResource(R.string.action_open_app_settings), openAppSettings)
        } else {
            BannerAction(stringResource(R.string.action_allow_local_network), request)
        }
    }
}

@Composable
private fun ConnectStep(state: SetupUiState, actions: SetupActions) {
    val link = state.link
    when (state.apCheck) {
        ApCheck.NO_WIFI -> Banner(StatusKind.WARNING, stringResource(R.string.setup_no_wifi))
        ApCheck.RECOGNISED_NAME -> Banner(StatusKind.INFO, stringResource(R.string.setup_recognised_network, link?.ssid.orEmpty()))
        ApCheck.DIFFERENT_NAME -> Banner(StatusKind.INFO, stringResource(R.string.setup_wrong_network, link?.ssid.orEmpty()))
        ApCheck.HAS_INTERNET -> Banner(StatusKind.WARNING, stringResource(R.string.setup_network_has_internet))
        ApCheck.CONFIRMED_MANUALLY -> Banner(StatusKind.SUCCESS, stringResource(R.string.setup_connection_confirmed))
        ApCheck.NEEDS_MANUAL_CONFIRMATION -> Banner(StatusKind.INFO, stringResource(R.string.setup_network_without_internet))
    }
    SectionCard {
        NumberedSteps(stringResource(R.string.setup_join_instructions))
        OpenWifiButton(actions)
        if (link != null) {
            NetworkDetails(link)
            if (link.ssid == null) WifiNameHelp(state, actions)
        }
    }
    if (state.apCheck != ApCheck.NO_WIFI) {
        val confirmed = state.apCheck == ApCheck.CONFIRMED_MANUALLY
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = if (confirmed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(confirmed, role = Role.Checkbox, onValueChange = actions.setManualConfirmation)
                    .padding(16.dp),
            ) {
                Checkbox(checked = confirmed, onCheckedChange = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.setup_manual_confirmation), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Renders "1. …
2. …" text as a list with numbered markers. */
@Composable
internal fun NumberedSteps(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        text.lines().filter { it.isNotBlank() }.forEachIndexed { index, line ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(24.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(line.replaceFirst(LIST_NUMBER, ""), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private val LIST_NUMBER = Regex("""^\s*\d+\.\s*""")

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
        else -> Hint(stringResource(R.string.setup_ssid_hidden, stringResource(R.string.setup_device_wifi)))
    }
}

@Composable
private fun OpenWifiButton(actions: SetupActions) {
    FilledTonalButton(onClick = actions.openWifiPicker, modifier = Modifier.fillMaxWidth()) {
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
    Hint(parts.joinToString("  ·  "))
}

@Composable
private fun CredentialsStep(state: SetupUiState, actions: SetupActions) {
    val form = state.form
    var passwordVisible by remember { mutableStateOf(false) }
    Banner(StatusKind.INFO, stringResource(R.string.setup_band_hint))
    SectionCard {
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
                    // Narrow padding so "WPA/WPA2" fits on small phones.
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    label = { Text(label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }
        Hint(stringResource(R.string.setup_security_hint))
        if (form.ssid != form.ssid.trim()) StatusLine(StatusKind.WARNING, stringResource(R.string.setup_ssid_spaces))
        if (form.ssid.any { it.code > 0x7F }) StatusLine(StatusKind.INFO, stringResource(R.string.setup_ssid_non_ascii))
        if (form.security.usesPassword && form.password.any { it.code !in 0x20..0x7E }) {
            StatusLine(StatusKind.WARNING, stringResource(R.string.setup_password_non_ascii))
        }
    }
    PrivacyFooter()
}

@Composable
private fun ConfigureStep(state: SetupUiState, actions: SetupActions) {
    SectionCard {
        Text(
            stringResource(R.string.setup_configure_explanation, state.link?.ssid ?: stringResource(R.string.setup_device_wifi)),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!state.canConfigure && !state.isSending) {
            val reason = when {
                !state.apCheck.isConfirmed -> stringResource(R.string.setup_reason_connect_first)
                state.form.isDeviceApName || state.form.problem != null -> stringResource(R.string.setup_reason_fill_details)
                else -> null
            }
            reason?.let { StatusLine(StatusKind.INFO, it) }
        }
        Button(onClick = actions.configure, enabled = state.canConfigure, modifier = Modifier.fillMaxWidth().height(52.dp)) {
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val deviceWifi = provisioning.setupSsid ?: stringResource(R.string.setup_device_wifi)
            when (val outcome = provisioning.outcome) {
                is Outcome.Acknowledged -> StatusLine(StatusKind.SUCCESS, stringResource(R.string.setup_acknowledged, outcome.from.hostAddress.orEmpty()))
                is Outcome.NotAcknowledged -> StatusLine(StatusKind.INFO, stringResource(R.string.setup_not_acknowledged, outcome.roundsSent))
                is Outcome.LinkLostAfterSend -> StatusLine(StatusKind.SUCCESS, stringResource(R.string.setup_link_lost_after_send, deviceWifi))
            }
            val target = provisioning.targetSsid
            when (val watch = provisioning.apWatch) {
                ApWatch.Watching -> StatusLine(StatusKind.PROGRESS, stringResource(R.string.setup_watching_ap, deviceWifi, target))
                is ApWatch.Gone -> StatusLine(StatusKind.SUCCESS, stringResource(R.string.setup_ap_gone, deviceWifi, watch.afterSeconds, target))
                ApWatch.StillConnected -> {
                    StatusLine(StatusKind.WARNING, stringResource(R.string.setup_ap_still_up, deviceWifi))
                    Hint(stringResource(R.string.setup_troubleshooting))
                }
            }
        }
        is ProvisioningState.Failed -> {
            StatusLine(StatusKind.ERROR, stringResource(R.string.setup_sending_failed) + " " + networkErrorText(provisioning.error))
            if (provisioning.error == NetworkError.LocalNetworkBlocked) {
                OutlinedButton(onClick = actions.openAppSettings) { Text(stringResource(R.string.action_open_app_settings)) }
            }
        }
    }
}

@Composable
private fun DiscoveryStep(state: SetupUiState, savedMacs: Set<String>, actions: SetupActions) {
    var showOtherDevices by rememberSaveable { mutableStateOf(false) }
    var address by rememberSaveable { mutableStateOf("") }
    var subnet by rememberSaveable(state.link?.address) { mutableStateOf(defaultSubnet(state.link)) }

    SectionCard {
        Text(stringResource(R.string.setup_discovery_explanation), style = MaterialTheme.typography.bodyMedium)
        val link = state.link
        when {
            link == null -> {
                StatusLine(StatusKind.INFO, stringResource(R.string.setup_connect_home_wifi))
                OpenWifiButton(actions)
            }
            link.isSetupAp || state.apCheck == ApCheck.CONFIRMED_MANUALLY -> {
                StatusLine(StatusKind.INFO, stringResource(R.string.setup_still_on_ap))
                OpenWifiButton(actions)
            }
            else -> Hint(
                stringResource(R.string.setup_searching_on, link.ssid ?: stringResource(R.string.setup_current_network)) +
                    (link.address?.let { " (${it.hostAddress})" } ?: ""),
            )
        }
        Button(onClick = actions.searchThisNetwork, enabled = state.canDiscover, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_search_network))
        }
    }

    ExpandableCard(stringResource(R.string.setup_other_vlan_toggle)) {
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
            FilledTonalButton(onClick = { actions.connectToAddress(address) }, enabled = state.canDiscover && address.isNotBlank()) {
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
            FilledTonalButton(onClick = { actions.scanSubnet(subnet) }, enabled = state.canDiscover && subnet.isNotBlank()) {
                Text(stringResource(R.string.action_scan))
            }
        }
    }

    when (val discovery = state.discovery) {
        DiscoveryState.Idle -> Unit
        is DiscoveryState.Searching -> {
            SectionCard { StatusLine(StatusKind.PROGRESS, stringResource(R.string.setup_searching_seconds, discovery.seconds)) }
            DiscoveryResults(discovery.found, savedMacs, actions, showOtherDevices) { showOtherDevices = it }
        }
        is DiscoveryState.Done -> {
            if (discovery.found.isEmpty()) {
                Banner(
                    StatusKind.WARNING,
                    stringResource(if (discovery.mode == SearchMode.BROADCAST) R.string.setup_nothing_found_broadcast else R.string.setup_nothing_found_unicast),
                )
            } else {
                if (DiscoveryDeviceGroups(discovery.found).waterTreatment.isEmpty()) {
                    Banner(StatusKind.INFO, stringResource(R.string.setup_no_water_devices))
                }
                DiscoveryResults(discovery.found, savedMacs, actions, showOtherDevices) { showOtherDevices = it }
            }
        }
        is DiscoveryState.Failed -> Banner(StatusKind.ERROR, stringResource(R.string.setup_search_failed) + " " + networkErrorText(discovery.error))
        DiscoveryState.InvalidAddress -> Banner(StatusKind.ERROR, stringResource(R.string.setup_invalid_address))
        DiscoveryState.InvalidSubnet -> Banner(StatusKind.ERROR, stringResource(R.string.setup_invalid_subnet, Ipv4.MIN_SCAN_PREFIX))
    }
}

@Composable
private fun DiscoveryResults(
    found: List<DiscoveredDevice>,
    savedMacs: Set<String>,
    actions: SetupActions,
    showOther: Boolean,
    onShowOther: (Boolean) -> Unit,
) {
    val groups = DiscoveryDeviceGroups(found)
    groups.visible(showOther).forEach { FoundDevice(it, it.mac in savedMacs, actions.openDevice) }
    if (groups.other.isNotEmpty()) {
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth().toggleable(value = showOther, role = Role.Switch, onValueChange = onShowOther),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.setup_show_other_devices, groups.other.size), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Switch(checked = showOther, onCheckedChange = null)
            }
            Hint(stringResource(R.string.setup_other_devices_hint))
        }
    }
}

private fun defaultSubnet(link: WifiLink?): String =
    link?.address?.let { Ipv4.surrounding24(it).toString() } ?: "192.168.1.0/24"

@Composable
private fun FoundDevice(device: DiscoveredDevice, saved: Boolean, open: (DiscoveredDevice) -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.Top) {
            if (device.isRunxinBl3372) {
                IconBadge(R.drawable.ic_water_drop)
            } else {
                IconBadge(R.drawable.ic_wifi, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.setup_device_found_at, device.address.hostAddress.orEmpty()), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(if (device.isRunxinBl3372) R.string.device_type_runxin else R.string.device_type_other, "0x%04X".format(device.deviceType)),
                    style = MaterialTheme.typography.bodySmall,
                )
                Hint("MAC ${device.mac}" + if (device.name.isNotBlank()) "  ·  “${device.name}”" else "")
                if (device.isLocked) Hint(stringResource(R.string.setup_device_locked))
            }
        }
        Button(onClick = { open(device) }, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(if (saved) R.string.action_open else R.string.action_save_and_open))
        }
    }
}
