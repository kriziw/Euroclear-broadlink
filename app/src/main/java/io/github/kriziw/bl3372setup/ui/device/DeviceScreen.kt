package io.github.kriziw.bl3372setup.ui.device

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kriziw.bl3372setup.Permissions
import io.github.kriziw.bl3372setup.R
import io.github.kriziw.bl3372setup.SystemScreens
import io.github.kriziw.bl3372setup.broadlink.Ipv4
import io.github.kriziw.bl3372setup.runxin.F79d
import io.github.kriziw.bl3372setup.runxin.SoftenerSetting
import io.github.kriziw.bl3372setup.runxin.SoftenerState
import io.github.kriziw.bl3372setup.runxin.VacationStatus
import io.github.kriziw.bl3372setup.runxin.VolumeUnit
import io.github.kriziw.bl3372setup.runxin.WriteResult
import io.github.kriziw.bl3372setup.ui.common.AppBackground
import io.github.kriziw.bl3372setup.ui.common.Hint
import io.github.kriziw.bl3372setup.ui.common.LanguageDialog
import io.github.kriziw.bl3372setup.ui.common.SectionCard
import io.github.kriziw.bl3372setup.ui.common.StatusKind
import io.github.kriziw.bl3372setup.ui.common.StatusLine
import io.github.kriziw.bl3372setup.ui.common.ValueRow
import io.github.kriziw.bl3372setup.ui.common.closeReasonText
import io.github.kriziw.bl3372setup.ui.common.daysText
import io.github.kriziw.bl3372setup.ui.common.durationText
import io.github.kriziw.bl3372setup.ui.common.flowText
import io.github.kriziw.bl3372setup.ui.common.flowUnitText
import io.github.kriziw.bl3372setup.ui.common.networkErrorText
import io.github.kriziw.bl3372setup.ui.common.stationText
import io.github.kriziw.bl3372setup.ui.common.timeText
import io.github.kriziw.bl3372setup.ui.common.uiLocale
import io.github.kriziw.bl3372setup.ui.common.volumeText
import io.github.kriziw.bl3372setup.ui.common.yesNo
import io.github.kriziw.bl3372setup.ui.setup.LocalNetworkCard
import java.text.DateFormat
import java.time.LocalTime
import java.util.Date
import java.util.Locale

@Composable
fun DeviceRoute(mac: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: DeviceViewModel = viewModel(key = mac) { DeviceViewModel(application, mac) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val localNetworkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.reconnect()
    }

    // Poll only while the screen is visible.
    LifecycleResumeEffect(mac) {
        viewModel.start()
        onPauseOrDispose { viewModel.stop() }
    }

    DeviceScreen(
        state = state,
        onBack = onBack,
        onApply = viewModel::apply,
        onRename = viewModel::rename,
        onSetAddress = viewModel::setAddress,
        onRetry = viewModel::reconnect,
        onUnlock = viewModel::unlockControls,
        onRemove = {
            viewModel.remove()
            onBack()
        },
        onOpenWifi = { SystemScreens.openWifiPicker(context) },
        onRequestLocalNetwork = { localNetworkLauncher.launch(Permissions.ACCESS_LOCAL_NETWORK) },
        onOpenAppSettings = { SystemScreens.openAppSettings(context) },
        onWriteShown = viewModel::consumeWriteOutcome,
    )
}

/** Which edit dialog is open. */
private enum class Editor { HARDNESS, SALT, REGEN_TIME, CONTINUOUS_FLOW, FLOW_SHUTOFF, REGENERATE, RENAME, ADDRESS, REMOVE }

@Composable
fun DeviceScreen(
    state: DeviceUiState,
    onBack: () -> Unit,
    onApply: (SoftenerSetting) -> Unit,
    onRename: (String) -> Unit,
    onSetAddress: (java.net.Inet4Address) -> Unit,
    onRetry: () -> Unit,
    onUnlock: () -> Unit,
    onRemove: () -> Unit,
    onOpenWifi: () -> Unit,
    onRequestLocalNetwork: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onWriteShown: () -> Unit,
) {
    var editor by rememberSaveable { mutableStateOf<Editor?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val outcomeText = state.lastWrite?.let { writeOutcomeText(it) }
    LaunchedEffect(state.lastWrite) {
        if (outcomeText != null) {
            snackbar.showSnackbar(outcomeText)
            onWriteShown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AppBackground(topBar = {
            DeviceTopBar(
                title = state.device?.name ?: stringResource(R.string.device_default_name),
                onBack = onBack,
                onRename = { editor = Editor.RENAME },
                onChangeAddress = { editor = Editor.ADDRESS },
                onRemove = { editor = Editor.REMOVE },
            )
        }) {
            ConnectionCard(state, onRetry, onOpenWifi, onRequestLocalNetwork, onOpenAppSettings, onChangeAddress = { editor = Editor.ADDRESS })
            val s = state.state
            if (s != null) {
                if (!state.isVerifiedModel && state.device?.controlsUnlocked != true) UnverifiedModelCard(s, onUnlock)
                StatusCard(s)
                WaterCard(s)
                SaltCard(s, state, onEdit = { editor = Editor.SALT })
                SettingsCard(s, state, onEdit = { editor = it }, onSyncClock = { onApply(SoftenerSetting.ControllerClock(LocalTime.now())) })
                ProgrammeCard(s)
                RawFieldsCard(s)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing))
    }

    val s = state.state
    when (editor) {
        Editor.HARDNESS -> NumberDialog(
            title = stringResource(R.string.setting_hardness),
            unit = "mg/l",
            initial = s?.hardnessMgPerLitre ?: 150,
            range = SoftenerSetting.Hardness.RANGE,
            hint = stringResource(R.string.setting_hardness_hint),
            onDismiss = { editor = null },
            onConfirm = { editor = null; onApply(SoftenerSetting.Hardness(it)) },
        )
        Editor.SALT -> NumberDialog(
            title = stringResource(R.string.setting_salt_added),
            unit = "kg",
            initial = s?.saltAddedKg ?: 0,
            range = SoftenerSetting.SaltAdded.RANGE,
            hint = stringResource(R.string.setting_salt_added_hint),
            onDismiss = { editor = null },
            onConfirm = { editor = null; onApply(SoftenerSetting.SaltAdded(it)) },
        )
        Editor.CONTINUOUS_FLOW -> NumberDialog(
            title = stringResource(R.string.setting_continuous_flow),
            unit = stringResource(R.string.unit_minutes),
            initial = s?.continuousFlowLimitMinutes ?: 0,
            range = SoftenerSetting.ContinuousFlowLimit.RANGE,
            hint = stringResource(R.string.setting_safehome_hint),
            onDismiss = { editor = null },
            onConfirm = { editor = null; onApply(SoftenerSetting.ContinuousFlowLimit(it)) },
        )
        Editor.FLOW_SHUTOFF -> DecimalDialog(
            title = stringResource(R.string.setting_flow_shutoff),
            unit = "m³/h",
            initialHundredths = s?.flowShutoffHundredths ?: 0,
            rangeHundredths = SoftenerSetting.FlowShutoff.RANGE,
            hint = stringResource(R.string.setting_safehome_hint),
            onDismiss = { editor = null },
            onConfirm = { editor = null; onApply(SoftenerSetting.FlowShutoff(it)) },
        )
        Editor.REGEN_TIME -> TimeDialog(
            title = stringResource(R.string.setting_regeneration_time),
            initial = s?.regenerationTime ?: LocalTime.of(2, 0),
            hint = stringResource(R.string.setting_regeneration_time_hint),
            onDismiss = { editor = null },
            onConfirm = { editor = null; onApply(SoftenerSetting.RegenerationTime(it)) },
        )
        Editor.REGENERATE -> AlertDialog(
            onDismissRequest = { editor = null },
            title = { Text(stringResource(R.string.regenerate_confirm_title)) },
            text = { Text(stringResource(R.string.regenerate_confirm_text)) },
            confirmButton = {
                Button(onClick = { editor = null; onApply(SoftenerSetting.Regenerate) }) { Text(stringResource(R.string.action_regenerate)) }
            },
            dismissButton = { TextButton(onClick = { editor = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
        Editor.RENAME -> TextDialog(
            title = stringResource(R.string.device_rename),
            initial = state.device?.name.orEmpty(),
            label = stringResource(R.string.device_name_label),
            validate = { it.isNotBlank() },
            onDismiss = { editor = null },
            onConfirm = { editor = null; onRename(it) },
        )
        Editor.ADDRESS -> TextDialog(
            title = stringResource(R.string.device_change_address),
            initial = state.device?.lastIp.orEmpty(),
            label = stringResource(R.string.device_address_label),
            hint = stringResource(R.string.device_address_hint),
            keyboardType = KeyboardType.Uri,
            validate = { Ipv4.parse(it) != null },
            onDismiss = { editor = null },
            onConfirm = { text -> editor = null; Ipv4.parse(text)?.let(onSetAddress) },
        )
        Editor.REMOVE -> AlertDialog(
            onDismissRequest = { editor = null },
            title = { Text(stringResource(R.string.device_remove_title)) },
            text = { Text(stringResource(R.string.device_remove_text)) },
            confirmButton = {
                Button(
                    onClick = { editor = null; onRemove() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = { TextButton(onClick = { editor = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
        null -> Unit
    }
}

@Composable
private fun DeviceTopBar(title: String, onBack: () -> Unit, onRename: () -> Unit, onChangeAddress: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var language by rememberSaveable { mutableStateOf(false) }
    if (language) LanguageDialog(onDismiss = { language = false })
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.cd_back)) }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.cd_more)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.device_rename)) }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text(stringResource(R.string.device_change_address)) }, onClick = { menu = false; onChangeAddress() })
                DropdownMenuItem(text = { Text(stringResource(R.string.language_title)) }, onClick = { menu = false; language = true })
                DropdownMenuItem(text = { Text(stringResource(R.string.device_remove_title)) }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    state: DeviceUiState,
    onRetry: () -> Unit,
    onOpenWifi: () -> Unit,
    onRequestLocalNetwork: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onChangeAddress: () -> Unit,
) {
    val connection = state.connection
    if (connection == Connection.NeedsLocalNetworkPermission) {
        LocalNetworkCard(denied = false, request = onRequestLocalNetwork, openAppSettings = onOpenAppSettings)
        return
    }
    SectionCard(stringResource(R.string.device_connection_title)) {
        val device = state.device
        if (device != null) {
            Hint("${device.lastIp}  ·  MAC ${device.mac}" + (state.link?.ssid?.let { "  ·  $it" } ?: ""))
        }
        when (connection) {
            Connection.Starting, Connection.Connecting -> StatusLine(StatusKind.PROGRESS, stringResource(R.string.device_connecting))
            is Connection.Locating -> StatusLine(
                StatusKind.PROGRESS,
                stringResource(if (connection.crossSubnet) R.string.device_locating_subnet else R.string.device_locating),
            )
            Connection.Live -> {
                val locale = uiLocale()
                val time = state.updatedAt?.let { DateFormat.getTimeInstance(DateFormat.MEDIUM, locale).format(Date(it)) } ?: "–"
                StatusLine(StatusKind.SUCCESS, stringResource(R.string.device_live, time))
            }
            Connection.NoWifi -> {
                StatusLine(StatusKind.WARNING, stringResource(R.string.device_no_wifi))
                OutlinedButton(onClick = onOpenWifi) { Text(stringResource(R.string.action_open_wifi)) }
            }
            is Connection.NotFound -> {
                StatusLine(StatusKind.WARNING, stringResource(R.string.device_not_found, connection.lastIp))
                Hint(stringResource(R.string.device_not_found_hint))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                    OutlinedButton(onClick = onChangeAddress) { Text(stringResource(R.string.device_change_address)) }
                }
            }
            is Connection.Failed -> {
                StatusLine(StatusKind.ERROR, networkErrorText(connection.error))
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
            Connection.NeedsLocalNetworkPermission -> Unit
        }
        if (state.pendingWrite != null) StatusLine(StatusKind.PROGRESS, stringResource(R.string.device_writing))
    }
}

@Composable
private fun UnverifiedModelCard(s: SoftenerState, onUnlock: () -> Unit) {
    var confirmed by rememberSaveable { mutableStateOf(false) }
    SectionCard(stringResource(R.string.device_unverified_title)) {
        StatusLine(
            StatusKind.WARNING,
            stringResource(R.string.device_unverified_text, s.deviceModel?.toString() ?: "?", F79d.VERIFIED_MODEL),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().toggleable(confirmed, role = Role.Checkbox, onValueChange = { confirmed = it }),
        ) {
            Checkbox(checked = confirmed, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.device_unverified_confirm), style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton(onClick = onUnlock, enabled = confirmed) { Text(stringResource(R.string.action_unlock_controls)) }
    }
}

@Composable
private fun StatusCard(s: SoftenerState) {
    SectionCard(stringResource(R.string.device_status_title)) {
        val phase = stationText(s.station, s.stationCode)
        val remaining = s.phaseRemainingSeconds
        Text(
            if (remaining != null) stringResource(R.string.device_phase_remaining, phase, durationText(remaining)) else phase,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        ValueRow(stringResource(R.string.device_flow_now), flowText(s.flowRateHundredths, s.volumeUnit))
        if (s.station == io.github.kriziw.bl3372setup.runxin.Station.CLOSED) {
            StatusLine(StatusKind.WARNING, stringResource(R.string.device_closed_reason, closeReasonText(s.closeReason, s.closeReasonCode)))
        }
        val vacation = when (s.vacationStatus) {
            VacationStatus.OFF -> stringResource(R.string.vacation_off)
            VacationStatus.PREPARING -> stringResource(R.string.vacation_preparing)
            VacationStatus.ACTIVE -> stringResource(R.string.vacation_active)
            null -> "–"
        }
        ValueRow(stringResource(R.string.device_vacation), vacation)
        Hint(stringResource(R.string.device_vacation_hint))
        ValueRow(stringResource(R.string.device_controller_clock), timeText(s.controllerTime))

        val alerts = buildList {
            if (s.lowBrineAlarm == true) add(stringResource(R.string.alert_low_brine))
            if (s.saltReminder == true) add(stringResource(R.string.alert_salt_reminder))
            if (s.resinReplacementReminder == true) add(stringResource(R.string.alert_resin))
            if (s.filterReminder == true) add(stringResource(R.string.alert_filter))
            if (s.clockChipFault == true) add(stringResource(R.string.fault_clock))
            if (s.multiplePositionSignalFault == true) add(stringResource(R.string.fault_multiple_position))
            if (s.noPositionSignalFault == true) add(stringResource(R.string.fault_no_position))
            if (s.memoryFault == true) add(stringResource(R.string.fault_memory))
        }
        if (alerts.isEmpty()) {
            StatusLine(StatusKind.SUCCESS, stringResource(R.string.device_no_alerts))
        } else {
            alerts.forEach { StatusLine(StatusKind.WARNING, it) }
        }
    }
}

@Composable
private fun WaterCard(s: SoftenerState) {
    SectionCard(stringResource(R.string.device_water_title)) {
        ValueRow(stringResource(R.string.device_remaining_capacity), volumeText(s.remainingCapacity, s.volumeUnit))
        ValueRow(stringResource(R.string.device_today), volumeText(s.todayConsumption, s.volumeUnit))
        ValueRow(stringResource(R.string.device_weekly_average), volumeText(s.weeklyAverageConsumption, s.volumeUnit))
        ValueRow(stringResource(R.string.device_capacity_per_cycle), volumeText(s.capacityPerCycle, s.volumeUnit))
        val mode = when (s.regenerationByTime) {
            true -> stringResource(R.string.device_regeneration_by_days, s.serviceDays?.toString() ?: "–")
            false -> stringResource(R.string.device_regeneration_by_volume)
            null -> "–"
        }
        ValueRow(stringResource(R.string.device_regeneration_mode), mode)
        if (s.regenerationByTime == true) {
            ValueRow(stringResource(R.string.device_days_remaining), s.remainingDays?.toString() ?: "–")
        }
    }
}

@Composable
private fun SaltCard(s: SoftenerState, state: DeviceUiState, onEdit: () -> Unit) {
    SectionCard(stringResource(R.string.device_salt_title)) {
        EditableRow(stringResource(R.string.setting_salt_added), s.saltAddedKg?.let { "$it kg" } ?: "–", state.controlsEnabled, onEdit)
        Hint(stringResource(R.string.setting_salt_added_hint))
        ValueRow(stringResource(R.string.alert_low_brine), yesNo(s.lowBrineAlarm))
        ValueRow(stringResource(R.string.alert_salt_reminder), yesNo(s.saltReminder))
        ValueRow(stringResource(R.string.device_regeneration_reminder), s.regenerationReminderCount?.toString() ?: "–")
        ValueRow(stringResource(R.string.device_filter_days), s.filterMaterialDays?.let { daysText(it) } ?: "–")
    }
}

@Composable
private fun SettingsCard(s: SoftenerState, state: DeviceUiState, onEdit: (Editor) -> Unit, onSyncClock: () -> Unit) {
    val enabled = state.controlsEnabled
    SectionCard(stringResource(R.string.device_settings_title)) {
        if (!enabled && state.connection == Connection.Live && state.pendingWrite == null) {
            Hint(stringResource(R.string.device_controls_locked))
        }
        val locale = uiLocale()
        val hardness = s.hardnessMgPerLitre?.let {
            stringResource(R.string.value_hardness, it, String.format(locale, "%.1f", it / 10.0))
        } ?: "–"
        EditableRow(stringResource(R.string.setting_hardness), hardness, enabled) { onEdit(Editor.HARDNESS) }
        EditableRow(stringResource(R.string.setting_regeneration_time), timeText(s.regenerationTime), enabled) { onEdit(Editor.REGEN_TIME) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.device_controller_clock), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(timeText(s.controllerTime), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            OutlinedButton(onClick = onSyncClock, enabled = enabled) { Text(stringResource(R.string.action_sync_clock)) }
        }
        HorizontalDivider()
        Text(stringResource(R.string.device_safehome_title), style = MaterialTheme.typography.labelLarge)
        val continuous = s.continuousFlowLimitMinutes?.let { if (it == 0) stringResource(R.string.value_off) else stringResource(R.string.value_minutes, it) } ?: "–"
        EditableRow(stringResource(R.string.setting_continuous_flow), continuous, enabled) { onEdit(Editor.CONTINUOUS_FLOW) }
        val shutoff = s.flowShutoffHundredths?.let { if (it == 0) stringResource(R.string.value_off) else flowText(it, s.volumeUnit) } ?: "–"
        // Only the cubic-metre unit family has been verified for field 7 writes.
        EditableRow(stringResource(R.string.setting_flow_shutoff), shutoff, enabled && s.volumeUnit == VolumeUnit.CUBIC_METRES) { onEdit(Editor.FLOW_SHUTOFF) }
        Hint(stringResource(R.string.setting_safehome_hint))
        HorizontalDivider()
        Button(
            onClick = { onEdit(Editor.REGENERATE) },
            enabled = state.canRegenerate,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.pendingWrite == SoftenerSetting.Regenerate) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(10.dp))
            }
            Text(stringResource(R.string.action_regenerate_now))
        }
        Hint(stringResource(R.string.regenerate_hint))
    }
}

@Composable
private fun ProgrammeCard(s: SoftenerState) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionCard(stringResource(R.string.device_programme_title)) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.action_hide else R.string.action_show))
        }
        if (!expanded) return@SectionCard
        ValueRow(stringResource(R.string.programme_backwash), durationText(s.backwashSeconds))
        ValueRow(stringResource(R.string.programme_brine_slow_rinse), durationText(s.brineSlowRinseSeconds))
        ValueRow(stringResource(R.string.programme_brine_refill), durationText(s.brineRefillSeconds))
        ValueRow(stringResource(R.string.programme_fast_rinse), durationText(s.fastRinseSeconds))
        ValueRow(stringResource(R.string.programme_rinsing_frequency), s.rinsingFrequency?.toString() ?: "–")
        ValueRow(stringResource(R.string.programme_backwash_interval), s.backwashIntervalCount?.toString() ?: "–")
        ValueRow(stringResource(R.string.programme_max_interval), s.maxRegenerationIntervalDays?.let { daysText(it) } ?: "–")
        ValueRow(stringResource(R.string.programme_resin_volume), s.resinVolumeLitres?.let { "$it L" } ?: "–")
        ValueRow(stringResource(R.string.programme_output_mode), s.outputRelayMode?.let { "b-0${it + 1}" } ?: "–")
        ValueRow(
            stringResource(R.string.programme_brine_draw),
            when (s.brineDrawForward) {
                true -> stringResource(R.string.programme_brine_draw_forward)
                false -> stringResource(R.string.programme_brine_draw_reverse)
                null -> "–"
            },
        )
        ValueRow(stringResource(R.string.programme_work_pattern), s.workPattern?.toString() ?: "–")
        ValueRow(stringResource(R.string.programme_units), flowUnitText(s.volumeUnit))
        ValueRow(stringResource(R.string.programme_time_format), s.timeScheme24h?.let { if (it) "24 h" else "12 h" } ?: "–")
    }
}

@Composable
private fun RawFieldsCard(s: SoftenerState) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionCard(stringResource(R.string.device_raw_title)) {
        Hint(stringResource(R.string.device_raw_hint))
        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.action_hide else R.string.action_show))
        }
        if (!expanded) return@SectionCard
        s.fields.toSortedMap().forEach { (id, bytes) ->
            ValueRow("$id  ${F79d.FIELD_NAMES[id].orEmpty()}", "%02X %02X".format(bytes.first, bytes.second))
        }
    }
}

@Composable
private fun EditableRow(label: String, value: String, enabled: Boolean, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
        IconButton(onClick = onEdit, enabled = enabled) {
            Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.cd_edit, label))
        }
    }
}

@Composable
private fun writeOutcomeText(outcome: WriteOutcome): String {
    val name = settingName(outcome.setting)
    val error = outcome.error
    return when (val result = outcome.result) {
        is WriteResult.Confirmed ->
            if (outcome.setting == SoftenerSetting.Regenerate) stringResource(R.string.write_regeneration_started)
            else stringResource(R.string.write_confirmed, name)
        is WriteResult.NotConfirmed ->
            if (result.ambiguousDelivery) stringResource(R.string.write_ambiguous, name)
            else stringResource(R.string.write_not_confirmed, name)
        null -> stringResource(R.string.write_failed, name, error?.let { networkErrorText(it) }.orEmpty())
    }
}

@Composable
private fun settingName(setting: SoftenerSetting): String = stringResource(
    when (setting) {
        is SoftenerSetting.Hardness -> R.string.setting_hardness
        is SoftenerSetting.SaltAdded -> R.string.setting_salt_added
        is SoftenerSetting.RegenerationTime -> R.string.setting_regeneration_time
        is SoftenerSetting.ControllerClock -> R.string.device_controller_clock
        is SoftenerSetting.ContinuousFlowLimit -> R.string.setting_continuous_flow
        is SoftenerSetting.FlowShutoff -> R.string.setting_flow_shutoff
        SoftenerSetting.Regenerate -> R.string.action_regenerate
    },
)

@Composable
private fun NumberDialog(
    title: String,
    unit: String,
    initial: Int,
    range: IntRange,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial.toString()) }
    val value = text.trim().toIntOrNull()?.takeIf { it in range }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    suffix = { Text(unit) },
                    isError = value == null,
                    supportingText = { Text(stringResource(R.string.dialog_range, range.first, range.last, unit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Hint(hint)
            }
        },
        confirmButton = { Button(onClick = { value?.let(onConfirm) }, enabled = value != null) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun DecimalDialog(
    title: String,
    unit: String,
    initialHundredths: Int,
    rangeHundredths: IntRange,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("%.2f".format(Locale.ROOT, initialHundredths / 100.0)) }
    val hundredths = text.trim().replace(',', '.').toBigDecimalOrNull()
        ?.movePointRight(2)?.let { if (it.stripTrailingZeros().scale() <= 0) it.toInt() else null }
        ?.takeIf { it in rangeHundredths }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    suffix = { Text(unit) },
                    isError = hundredths == null,
                    supportingText = {
                        Text(stringResource(R.string.dialog_range_decimal, rangeHundredths.first / 100.0, rangeHundredths.last / 100.0, unit))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Hint(hint)
            }
        },
        confirmButton = { Button(onClick = { hundredths?.let(onConfirm) }, enabled = hundredths != null) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(title: String, initial: LocalTime, hint: String, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val picker = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TimeInput(state = picker)
                Hint(hint)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(LocalTime.of(picker.hour, picker.minute)) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun TextDialog(
    title: String,
    initial: String,
    label: String,
    validate: (String) -> Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    hint: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val valid = validate(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                )
                hint?.let { Hint(it) }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(text) }, enabled = valid) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
