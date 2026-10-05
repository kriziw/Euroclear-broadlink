package io.github.kriziw.bl3372setup.ui.appliance

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kriziw.bl3372setup.Permissions
import io.github.kriziw.bl3372setup.R
import io.github.kriziw.bl3372setup.SystemScreens
import io.github.kriziw.bl3372setup.appliance.ApplianceAction
import io.github.kriziw.bl3372setup.appliance.ApplianceChange
import io.github.kriziw.bl3372setup.appliance.ApplianceSetting
import io.github.kriziw.bl3372setup.appliance.ApplianceState
import io.github.kriziw.bl3372setup.appliance.ApplianceStatus
import io.github.kriziw.bl3372setup.appliance.Brand
import io.github.kriziw.bl3372setup.appliance.ChangeResult
import io.github.kriziw.bl3372setup.appliance.CredentialKind
import io.github.kriziw.bl3372setup.appliance.ReadingKind
import io.github.kriziw.bl3372setup.ui.common.AppScaffold
import io.github.kriziw.bl3372setup.ui.common.BackButton
import io.github.kriziw.bl3372setup.ui.common.Banner
import io.github.kriziw.bl3372setup.ui.common.BannerAction
import io.github.kriziw.bl3372setup.ui.common.ExpandableCard
import io.github.kriziw.bl3372setup.ui.common.ExperimentalNote
import io.github.kriziw.bl3372setup.ui.common.Hint
import io.github.kriziw.bl3372setup.ui.common.ListCard
import io.github.kriziw.bl3372setup.ui.common.SectionCard
import io.github.kriziw.bl3372setup.ui.common.SettingRow
import io.github.kriziw.bl3372setup.ui.common.StatusKind
import io.github.kriziw.bl3372setup.ui.common.ValueRow
import io.github.kriziw.bl3372setup.ui.common.networkErrorText
import io.github.kriziw.bl3372setup.ui.common.timeText
import io.github.kriziw.bl3372setup.ui.common.uiLocale
import io.github.kriziw.bl3372setup.ui.device.Connection
import io.github.kriziw.bl3372setup.ui.device.TextDialog
import io.github.kriziw.bl3372setup.ui.device.TimeDialog
import io.github.kriziw.bl3372setup.ui.device.rememberSelectedText
import io.github.kriziw.bl3372setup.ui.setup.LocalNetworkCard
import io.github.kriziw.bl3372setup.ui.theme.LocalStatusColors
import java.math.BigDecimal
import java.text.DateFormat
import java.time.LocalTime
import java.util.Date

@Composable
fun ApplianceRoute(id: String, onBack: () -> Unit, onCompatibility: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: ApplianceViewModel = viewModel(key = id) { ApplianceViewModel(application, id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val localNetworkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.reconnect() }

    LifecycleResumeEffect(id) {
        viewModel.start()
        onPauseOrDispose { viewModel.stop() }
    }

    ApplianceScreen(
        state = state,
        onBack = onBack,
        onApply = viewModel::apply,
        onRename = viewModel::rename,
        onSetAddress = viewModel::setAddress,
        onSetLogin = viewModel::setLogin,
        onRetry = viewModel::reconnect,
        onUnlock = viewModel::unlockControls,
        onLock = viewModel::lockControls,
        onCompatibility = onCompatibility,
        onSettings = onSettings,
        onRemove = { viewModel.remove(); onBack() },
        onOpenWifi = { SystemScreens.openWifiPicker(context) },
        onRequestLocalNetwork = { localNetworkLauncher.launch(Permissions.ACCESS_LOCAL_NETWORK) },
        onOpenAppSettings = { SystemScreens.openAppSettings(context) },
        onOutcomeShown = viewModel::consumeOutcome,
    )
}

/** Which dialog is open: a setting key, an action, or one of the device-level dialogs. */
private sealed interface Dialog {
    data class Setting(val key: String) : Dialog
    data class Action(val action: ApplianceAction) : Dialog
    data object Rename : Dialog
    data object Address : Dialog
    data object Login : Dialog
    data object Remove : Dialog
}

@Composable
fun ApplianceScreen(
    state: ApplianceUiState,
    onBack: () -> Unit,
    onApply: (ApplianceChange) -> Unit,
    onRename: (String) -> Unit,
    onSetAddress: (String, Int) -> Unit,
    onSetLogin: (String?, String) -> Unit,
    onRetry: () -> Unit,
    onUnlock: () -> Unit,
    onLock: () -> Unit,
    onCompatibility: () -> Unit,
    onSettings: () -> Unit,
    onRemove: () -> Unit,
    onOpenWifi: () -> Unit,
    onRequestLocalNetwork: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOutcomeShown: () -> Unit,
) {
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val outcome = state.lastOutcome?.let { outcomeText(it) }
    LaunchedEffect(state.lastOutcome) {
        if (outcome != null) {
            snackbar.showSnackbar(outcome)
            onOutcomeShown()
        }
    }
    val brand = state.brand

    AppScaffold(
        title = state.device?.name ?: brand?.displayName.orEmpty(),
        subtitle = { ConnectionLine(state) },
        navigation = { BackButton(onBack) },
        actions = {
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.cd_more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.device_rename)) }, onClick = { menu = false; dialog = Dialog.Rename })
                    DropdownMenuItem(text = { Text(stringResource(R.string.device_change_address)) }, onClick = { menu = false; dialog = Dialog.Address })
                    if (brand != null && brand.credential != CredentialKind.NONE) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.appliance_change_login)) }, onClick = { menu = false; dialog = Dialog.Login })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.settings_title)) }, onClick = { menu = false; onSettings() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.device_remove_title)) }, onClick = { menu = false; dialog = Dialog.Remove })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) {
        when (val c = state.connection) {
            Connection.NeedsLocalNetworkPermission -> LocalNetworkCard(denied = false, request = onRequestLocalNetwork, openAppSettings = onOpenAppSettings)
            Connection.NoWifi -> Banner(StatusKind.WARNING, stringResource(R.string.device_no_wifi)) {
                BannerAction(stringResource(R.string.action_open_wifi), onOpenWifi)
            }
            is Connection.Failed -> Banner(StatusKind.ERROR, networkErrorText(c.error)) {
                BannerAction(stringResource(R.string.action_retry), onRetry)
                BannerAction(stringResource(R.string.device_change_address), onClick = { dialog = Dialog.Address })
            }
            else -> Unit
        }
        val appliance = state.appliance
        if (appliance == null) {
            if (state.connection is Connection.Starting || state.connection is Connection.Connecting) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                ) {
                    CircularProgressIndicator()
                    Hint(stringResource(if (brand == Brand.JUDO) R.string.appliance_reading_slow else R.string.device_connecting))
                }
            }
        } else {
            // Read-only appliances have nothing to unlock.
            if (appliance.settings.isNotEmpty() || appliance.actions.isNotEmpty()) {
                ExperimentalNote(
                    on = state.unlocked,
                    canEnable = state.connection == Connection.Live && appliance.complete,
                    onChange = { if (it) onUnlock() else onLock() },
                )
            }
            Hero(appliance, state, onAction = { dialog = Dialog.Action(it) })
            if (appliance.alerts.isNotEmpty()) {
                Banner(
                    if (appliance.alerts.any { it.fatal }) StatusKind.ERROR else StatusKind.WARNING,
                    title = stringResource(R.string.device_alerts_title),
                    text = appliance.alerts.map { alertText(it) }.joinToString("\n"),
                )
            }
            Readings(appliance)
            Settings(appliance, state, onEdit = { dialog = Dialog.Setting(it.name) })
            Details(appliance, state, onCompatibility)
        }
    }

    val appliance = state.appliance
    when (val d = dialog) {
        is Dialog.Setting -> appliance?.settings?.firstOrNull { it.key.name == d.key }?.let { setting ->
            SettingDialog(setting, onDismiss = { dialog = null }) { dialog = null; onApply(it) }
        }
        is Dialog.Action -> ActionDialog(d.action, onDismiss = { dialog = null }) { dialog = null; onApply(ApplianceChange.Run(d.action)) }
        Dialog.Rename -> TextDialog(
            title = stringResource(R.string.device_rename),
            initial = state.device?.name.orEmpty(),
            label = stringResource(R.string.device_name_label),
            validate = { it.isNotBlank() },
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; onRename(it) },
        )
        Dialog.Address -> AddressDialog(state, onDismiss = { dialog = null }) { host, port -> dialog = null; onSetAddress(host, port) }
        Dialog.Login -> LoginDialog(state, onDismiss = { dialog = null }) { user, secret -> dialog = null; onSetLogin(user, secret) }
        Dialog.Remove -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.device_remove_title)) },
            text = { Text(stringResource(R.string.device_remove_text)) },
            confirmButton = {
                Button(
                    onClick = { dialog = null; onRemove() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
        null -> Unit
    }
}

@Composable
private fun ConnectionLine(state: ApplianceUiState) {
    val status = LocalStatusColors.current
    val (dot, text) = when {
        state.pending != null -> MaterialTheme.colorScheme.primary to stringResource(R.string.device_writing)
        state.connection == Connection.Live -> {
            val time = state.updatedAt?.let { DateFormat.getTimeInstance(DateFormat.SHORT, uiLocale()).format(Date(it)) } ?: "–"
            status.success to stringResource(R.string.device_live, time)
        }
        state.connection == Connection.Starting || state.connection == Connection.Connecting ->
            MaterialTheme.colorScheme.primary to stringResource(R.string.device_connecting)
        else -> status.warning to stringResource(R.string.device_offline)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Readings shown big in the status card, in order of preference. */
private val HERO_READINGS = listOf(
    ReadingKind.REMAINING_CAPACITY_L,
    ReadingKind.SALT_RANGE_DAYS,
    ReadingKind.SALT_RANGE_WEEKS,
    ReadingKind.SALT_LEVEL_PERCENT,
    ReadingKind.FLOW_L_H,
    ReadingKind.FLOW_M3_H,
    ReadingKind.SOFT_WATER_TOTAL_M3,
)

/** Readings that repeat a setting's current value. */
private val SETTING_READINGS = mapOf(
    io.github.kriziw.bl3372setup.appliance.SettingKey.SALT_STOCK to ReadingKind.SALT_STOCK_KG,
    io.github.kriziw.bl3372setup.appliance.SettingKey.TARGET_HARDNESS to ReadingKind.TARGET_HARDNESS,
)

private fun ApplianceState.heroReadings() = HERO_READINGS.mapNotNull { reading(it) }.take(2)

@Composable
private fun Hero(appliance: ApplianceState, state: ApplianceUiState, onAction: (ApplianceAction) -> Unit) {
    SectionCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        val muted = LocalContentColor.current.copy(alpha = 0.75f)
        val model = appliance.model ?: state.brand?.displayName.orEmpty()
        if (appliance.status == ApplianceStatus.UNKNOWN) {
            // Some APIs (JUDO) report no operating status: lead with the model instead of "unknown".
            Text(stringResource(R.string.device_status_title), style = MaterialTheme.typography.labelLarge, color = muted)
            Text(model, style = MaterialTheme.typography.headlineSmall)
        } else {
            Text(model, style = MaterialTheme.typography.labelLarge, color = muted)
            Text(statusText(appliance.status, appliance.step), style = MaterialTheme.typography.headlineMedium)
        }
        val hero = appliance.heroReadings()
        if (hero.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                hero.forEach { reading ->
                    Column(Modifier.weight(1f)) {
                        Text(readingLabel(reading.kind), style = MaterialTheme.typography.bodySmall, color = muted)
                        Text(readingValue(reading), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
        if (!appliance.complete) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.appliance_reading_slow), style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
        if (ApplianceAction.REGENERATE in appliance.actions) {
            HeroAction(ApplianceAction.REGENERATE, state, Modifier.fillMaxWidth().padding(top = 4.dp), onAction)
        }
        val valve = appliance.actions.filter { it != ApplianceAction.REGENERATE }
        if (valve.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                valve.forEach { HeroAction(it, state, Modifier.weight(1f), onAction) }
            }
        }
    }
}

@Composable
private fun HeroAction(action: ApplianceAction, state: ApplianceUiState, modifier: Modifier, onAction: (ApplianceAction) -> Unit) {
    OutlinedButton(
        onClick = { onAction(action) },
        enabled = state.controlsEnabled,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
    ) {
        if ((state.pending as? ApplianceChange.Run)?.action == action) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(actionLabel(action), maxLines = 1)
    }
}

@Composable
private fun Readings(appliance: ApplianceState) {
    val hero = appliance.heroReadings().map { it.kind }.toSet()
    val asSettings = appliance.settings.mapNotNull { SETTING_READINGS[it.key] }.toSet()
    val rest = appliance.readings.filter { it.kind !in hero && it.kind !in asSettings }
    if (rest.isEmpty()) return
    SectionCard(title = stringResource(R.string.appliance_readings_title)) {
        rest.forEach { ValueRow(readingLabel(it.kind), readingValue(it)) }
    }
}

@Composable
private fun Settings(appliance: ApplianceState, state: ApplianceUiState, onEdit: (io.github.kriziw.bl3372setup.appliance.SettingKey) -> Unit) {
    if (appliance.settings.isEmpty()) {
        if (state.brand == Brand.BWT_PERLA) SectionCard { Hint(stringResource(R.string.appliance_read_only)) }
        return
    }
    val enabled = state.controlsEnabled
    ListCard(title = stringResource(R.string.device_settings_title)) {
        if (!state.unlocked && state.connection == Connection.Live) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_lock), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Hint(stringResource(R.string.device_controls_locked))
            }
        }
        appliance.settings.forEach { setting ->
            SettingRow(
                label = settingLabel(setting.key),
                value = settingValue(setting),
                onClick = { onEdit(setting.key) },
                enabled = enabled,
                trailingIcon = R.drawable.ic_edit,
            )
        }
    }
}

@Composable
private fun settingValue(setting: ApplianceSetting): String = when (setting) {
    is ApplianceSetting.Number -> setting.value?.let { "${formatNumber(it, setting.step)} ${unitLabel(setting.unit)}" } ?: "–"
    is ApplianceSetting.Options -> setting.options.firstOrNull { it.code == setting.value }?.let { choiceLabel(it.label) }
        ?: setting.value?.let { stringResource(R.string.value_unknown_code, it.toString()) } ?: "–"
    is ApplianceSetting.Time -> timeText(setting.value)
}

@Composable
private fun unitLabel(unit: String): String = if (unit == "d") stringResource(R.string.unit_days) else unit

@Composable
private fun formatNumber(value: Double, step: Double): String =
    String.format(uiLocale(), if (step >= 1.0) "%.0f" else "%.1f", value)

@Composable
private fun Details(appliance: ApplianceState, state: ApplianceUiState, onCompatibility: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Text(
        stringResource(R.string.device_details_title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
    ExpandableCard(stringResource(R.string.appliance_device_title)) {
        appliance.model?.let { ValueRow(stringResource(R.string.appliance_model), it) }
        appliance.firmware?.let { ValueRow(stringResource(R.string.appliance_firmware), it) }
        state.device?.let { device ->
            val port = device.port ?: state.brand?.defaultPort
            ValueRow(stringResource(R.string.setup_address_label), if (port != null) "${device.lastIp}:$port" else device.lastIp)
        }
        appliance.lastRegeneration?.let { ValueRow(stringResource(R.string.appliance_last_regeneration), it) }
        appliance.nextMaintenance?.let { ValueRow(stringResource(R.string.appliance_next_maintenance), it) }
        Hint(stringResource(R.string.appliance_plain_http))
        state.brand?.let { brand ->
            TextButton(onClick = { uriHandler.openUri(brandSource(brand)) }) { Text(stringResource(R.string.appliance_protocol_source)) }
        }
        TextButton(onClick = onCompatibility) { Text(stringResource(R.string.compatibility_title)) }
    }
}

@Composable
private fun SettingDialog(setting: ApplianceSetting, onDismiss: () -> Unit, onConfirm: (ApplianceChange) -> Unit) {
    val title = settingLabel(setting.key)
    val hint = settingHint(setting.key)
    when (setting) {
        is ApplianceSetting.Number -> NumberSettingDialog(setting, title, hint, onDismiss) { onConfirm(ApplianceChange.SetNumber(setting.key, it)) }
        is ApplianceSetting.Options -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column(Modifier.selectableGroup()) {
                    setting.options.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = option.code == setting.value,
                                    role = Role.RadioButton,
                                    onClick = { onConfirm(ApplianceChange.SetOption(setting.key, option.code)) },
                                )
                                .padding(vertical = 10.dp),
                        ) {
                            RadioButton(selected = option.code == setting.value, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(choiceLabel(option.label), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
        is ApplianceSetting.Time -> TimeDialog(
            title = title,
            initial = setting.value ?: LocalTime.of(2, 0),
            hint = hint.orEmpty(),
            onDismiss = onDismiss,
            onConfirm = { onConfirm(ApplianceChange.SetTime(setting.key, it)) },
        )
    }
}

@Composable
private fun NumberSettingDialog(
    setting: ApplianceSetting.Number,
    title: String,
    hint: String?,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var field by rememberSelectedText(setting.value?.let { formatNumber(it, setting.step) }.orEmpty())
    // Accept a comma or a point; the value must lie in range and on the step grid.
    val value = field.text.trim().replace(',', '.').toBigDecimalOrNull()
        ?.takeIf { it.toDouble() in setting.min..setting.max }
        ?.takeIf { it.remainder(BigDecimal.valueOf(setting.step)).compareTo(BigDecimal.ZERO) == 0 }
        ?.toDouble()
    val unit = unitLabel(setting.unit)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it },
                    modifier = Modifier.focusRequester(focus),
                    singleLine = true,
                    suffix = { Text(unit) },
                    isError = value == null,
                    supportingText = {
                        Text(stringResource(R.string.dialog_range_text, formatNumber(setting.min, setting.step), formatNumber(setting.max, setting.step), unit))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = if (setting.step < 1.0) KeyboardType.Decimal else KeyboardType.Number),
                )
                hint?.let { Hint(it) }
            }
        },
        confirmButton = { Button(onClick = { value?.let(onConfirm) }, enabled = value != null) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ActionDialog(action: ApplianceAction, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val (title, text) = when (action) {
        ApplianceAction.REGENERATE -> R.string.regenerate_confirm_title to R.string.appliance_regenerate_text
        ApplianceAction.CLOSE_VALVE -> R.string.valve_close_title to R.string.valve_close_text
        ApplianceAction.OPEN_VALVE -> R.string.valve_open_title to R.string.valve_open_text
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(text)) },
        confirmButton = { Button(onClick = onConfirm) { Text(actionLabel(action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun AddressDialog(state: ApplianceUiState, onDismiss: () -> Unit, onConfirm: (String, Int) -> Unit) {
    val device = state.device
    var host by rememberSaveable { mutableStateOf(device?.lastIp.orEmpty()) }
    var port by rememberSaveable { mutableStateOf((device?.port ?: state.brand?.defaultPort ?: 80).toString()) }
    val portValue = port.trim().toIntOrNull()?.takeIf { it in 1..65535 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.device_change_address)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.setup_address_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text(stringResource(R.string.appliance_port)) },
                    singleLine = true,
                    isError = portValue == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Hint(stringResource(R.string.device_address_hint))
            }
        },
        confirmButton = {
            Button(onClick = { portValue?.let { onConfirm(host.trim(), it) } }, enabled = host.isNotBlank() && portValue != null) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun LoginDialog(state: ApplianceUiState, onDismiss: () -> Unit, onConfirm: (String?, String) -> Unit) {
    val brand = state.brand ?: return
    var user by rememberSaveable { mutableStateOf(state.device?.user.orEmpty()) }
    // The stored secret is never shown again; the user types a new one.
    var secret by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.appliance_change_login)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LoginFields(brand, user, { user = it }, secret, { secret = it })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Hint(stringResource(R.string.appliance_plain_http))
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(user.ifBlank { null }, secret) }, enabled = secret.isNotEmpty()) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** User name (JUDO) and password or login code; shared by the add screen and the login dialog. */
@Composable
fun LoginFields(brand: Brand, user: String, onUser: (String) -> Unit, secret: String, onSecret: (String) -> Unit) {
    if (brand.credential == CredentialKind.USER_PASSWORD) {
        OutlinedTextField(
            value = user,
            onValueChange = onUser,
            label = { Text(stringResource(R.string.appliance_user)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (brand.credential != CredentialKind.NONE) {
        OutlinedTextField(
            value = secret,
            onValueChange = onSecret,
            label = {
                Text(stringResource(if (brand.credential == CredentialKind.CODE) R.string.appliance_code else R.string.setup_password_label))
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun outcomeText(outcome: ApplianceOutcome): String {
    val name = when (val change = outcome.change) {
        is ApplianceChange.Run -> actionLabel(change.action)
        is ApplianceChange.SetNumber -> settingLabel(change.key)
        is ApplianceChange.SetOption -> settingLabel(change.key)
        is ApplianceChange.SetTime -> settingLabel(change.key)
    }
    return when (outcome.result) {
        is ChangeResult.Confirmed -> stringResource(R.string.write_confirmed, name)
        is ChangeResult.Accepted -> stringResource(R.string.appliance_write_accepted, name)
        is ChangeResult.NotConfirmed -> stringResource(R.string.write_not_confirmed, name)
        null -> stringResource(R.string.write_failed, name, outcome.error?.let { networkErrorText(it) }.orEmpty())
    }
}
