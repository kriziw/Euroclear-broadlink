package io.github.kriziw.bl3372setup.ui.device

import android.app.Application
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.StrokeCap
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
import io.github.kriziw.bl3372setup.runxin.ControllerProfiles
import io.github.kriziw.bl3372setup.runxin.F79d
import io.github.kriziw.bl3372setup.runxin.SoftenerSetting
import io.github.kriziw.bl3372setup.runxin.SoftenerState
import io.github.kriziw.bl3372setup.runxin.Station
import io.github.kriziw.bl3372setup.runxin.VacationStatus
import io.github.kriziw.bl3372setup.runxin.VolumeUnit
import io.github.kriziw.bl3372setup.ui.common.AppScaffold
import io.github.kriziw.bl3372setup.ui.common.BackButton
import io.github.kriziw.bl3372setup.ui.common.Banner
import io.github.kriziw.bl3372setup.ui.common.BannerAction
import io.github.kriziw.bl3372setup.ui.common.ExpandableCard
import io.github.kriziw.bl3372setup.ui.common.Hint
import io.github.kriziw.bl3372setup.ui.common.ListCard
import io.github.kriziw.bl3372setup.ui.common.ListSubheader
import io.github.kriziw.bl3372setup.ui.common.SectionCard
import io.github.kriziw.bl3372setup.ui.common.SettingRow
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
import io.github.kriziw.bl3372setup.ui.theme.LocalStatusColors
import java.text.DateFormat
import java.time.LocalTime
import java.util.Date

@Composable
fun DeviceRoute(mac: String, onBack: () -> Unit, onCompatibility: () -> Unit, onSettings: () -> Unit) {
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
        onLock = viewModel::lockControls,
        onCompatibility = onCompatibility,
        onSettings = onSettings,
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

/** Which dialog is open. */
private enum class Editor { HARDNESS, SALT, REGEN_TIME, CONTINUOUS_FLOW, FLOW_SHUTOFF, REGENERATE, VACATION_START, VACATION_END, UNLOCK, RENAME, ADDRESS, REMOVE }

/**
 * The dashboard for one softener: status first, then usage, then the settings people change,
 * with everything else folded away under Details.
 */
@Composable
fun DeviceScreen(
    state: DeviceUiState,
    onBack: () -> Unit,
    onApply: (SoftenerSetting) -> Unit,
    onRename: (String) -> Unit,
    onSetAddress: (java.net.Inet4Address) -> Unit,
    onRetry: () -> Unit,
    onUnlock: () -> Unit,
    onLock: () -> Unit,
    onCompatibility: () -> Unit,
    onSettings: () -> Unit,
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

    AppScaffold(
        title = state.device?.name ?: stringResource(R.string.device_default_name),
        subtitle = { ConnectionSubtitle(state) },
        navigation = { BackButton(onBack) },
        actions = {
            DeviceMenu(
                onRename = { editor = Editor.RENAME },
                onChangeAddress = { editor = Editor.ADDRESS },
                onSettings = onSettings,
                onRemove = { editor = Editor.REMOVE },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) {
        ConnectionProblem(state, onRetry, onOpenWifi, onRequestLocalNetwork, onOpenAppSettings, onChangeAddress = { editor = Editor.ADDRESS })
        val s = state.state
        if (s == null) {
            if (state.connection.isWaiting) Loading(state.connection)
        } else {
            if (!state.isVerifiedModel) ExperimentalBanner(state, onUnlock = { editor = Editor.UNLOCK }, onLock = onLock)
            StatusHero(
                s,
                state,
                onRegenerate = { editor = Editor.REGENERATE },
                onVacation = { start -> editor = if (start) Editor.VACATION_START else Editor.VACATION_END },
            )
            AlertsBanner(s)
            UsageTiles(s, state, onEditSalt = { editor = Editor.SALT })
            SettingsList(s, state, onEdit = { editor = it }, onSyncClock = { onApply(SoftenerSetting.ControllerClock(LocalTime.now())) })
            Details(s, state, onCompatibility)
        }
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
            icon = { Icon(painterResource(R.drawable.ic_autorenew), contentDescription = null) },
            title = { Text(stringResource(R.string.regenerate_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.regenerate_confirm_text))
                    Hint(stringResource(R.string.regenerate_hint))
                }
            },
            confirmButton = {
                Button(onClick = { editor = null; onApply(SoftenerSetting.Regenerate) }) { Text(stringResource(R.string.action_regenerate)) }
            },
            dismissButton = { TextButton(onClick = { editor = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
        Editor.VACATION_START, Editor.VACATION_END -> {
            val start = editor == Editor.VACATION_START
            AlertDialog(
                onDismissRequest = { editor = null },
                icon = { Icon(painterResource(R.drawable.ic_beach), contentDescription = null) },
                title = { Text(stringResource(if (start) R.string.vacation_start_title else R.string.vacation_end_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(if (start) R.string.vacation_start_text else R.string.vacation_end_text))
                        Hint(stringResource(R.string.vacation_untested))
                    }
                },
                confirmButton = {
                    Button(onClick = { editor = null; onApply(SoftenerSetting.Vacation(start)) }) {
                        Text(stringResource(if (start) R.string.action_vacation_start else R.string.action_vacation_end))
                    }
                },
                dismissButton = { TextButton(onClick = { editor = null }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
        Editor.UNLOCK -> UnlockDialog(
            state = state,
            onDismiss = { editor = null },
            onConfirm = { editor = null; onUnlock() },
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

private val Connection.isWaiting: Boolean
    get() = this == Connection.Starting || this == Connection.Connecting || this is Connection.Locating

@Composable
private fun DeviceMenu(onRename: () -> Unit, onChangeAddress: () -> Unit, onSettings: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.cd_more)) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.device_rename)) }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text(stringResource(R.string.device_change_address)) }, onClick = { menu = false; onChangeAddress() })
            DropdownMenuItem(text = { Text(stringResource(R.string.settings_title)) }, onClick = { menu = false; onSettings() })
            DropdownMenuItem(text = { Text(stringResource(R.string.device_remove_title)) }, onClick = { menu = false; onRemove() })
        }
    }
}

/** One line under the device name: a coloured dot and what the connection is doing. */
@Composable
private fun ConnectionSubtitle(state: DeviceUiState) {
    val status = LocalStatusColors.current
    val connection = state.connection
    val (dot, text) = when {
        state.pendingWrite != null -> MaterialTheme.colorScheme.primary to stringResource(R.string.device_writing)
        connection == Connection.Live -> {
            val time = state.updatedAt?.let { DateFormat.getTimeInstance(DateFormat.SHORT, uiLocale()).format(Date(it)) } ?: "–"
            status.success to stringResource(R.string.device_live, time)
        }
        connection is Connection.Locating ->
            MaterialTheme.colorScheme.primary to stringResource(if (connection.crossSubnet) R.string.device_locating_subnet else R.string.device_locating)
        connection.isWaiting -> MaterialTheme.colorScheme.primary to stringResource(R.string.device_connecting)
        else -> status.warning to stringResource(R.string.device_offline)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Shown only when something is wrong with the connection. */
@Composable
private fun ConnectionProblem(
    state: DeviceUiState,
    onRetry: () -> Unit,
    onOpenWifi: () -> Unit,
    onRequestLocalNetwork: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onChangeAddress: () -> Unit,
) {
    when (val connection = state.connection) {
        Connection.NeedsLocalNetworkPermission ->
            LocalNetworkCard(denied = false, request = onRequestLocalNetwork, openAppSettings = onOpenAppSettings)
        Connection.NoWifi -> Banner(StatusKind.WARNING, stringResource(R.string.device_no_wifi)) {
            BannerAction(stringResource(R.string.action_open_wifi), onOpenWifi)
        }
        is Connection.NotFound -> Banner(
            StatusKind.WARNING,
            title = stringResource(R.string.device_not_found, connection.lastIp),
            text = stringResource(R.string.device_not_found_hint),
        ) {
            BannerAction(stringResource(R.string.action_retry), onRetry)
            BannerAction(stringResource(R.string.device_change_address), onChangeAddress)
        }
        is Connection.Failed -> Banner(StatusKind.ERROR, networkErrorText(connection.error)) {
            BannerAction(stringResource(R.string.action_retry), onRetry)
        }
        is Connection.UnsupportedModule -> Banner(
            StatusKind.WARNING,
            stringResource(R.string.compatibility_unsupported_module, "0x%04X".format(connection.deviceType)),
        )
        else -> Unit
    }
}

@Composable
private fun Loading(connection: Connection) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
    ) {
        CircularProgressIndicator()
        Hint(
            stringResource(
                when {
                    connection is Connection.Locating && connection.crossSubnet -> R.string.device_locating_subnet
                    connection is Connection.Locating -> R.string.device_locating
                    else -> R.string.device_connecting
                },
            ),
        )
    }
}

@Composable
private fun ExperimentalBanner(state: DeviceUiState, onUnlock: () -> Unit, onLock: () -> Unit) {
    val s = state.state ?: return
    if (state.experimentalUnlocked) {
        Banner(
            StatusKind.WARNING,
            title = stringResource(R.string.device_unverified_title),
            text = stringResource(R.string.compatibility_experimental_enabled),
        ) {
            BannerAction(stringResource(R.string.compatibility_lock_controls), onLock)
        }
    } else {
        Banner(
            StatusKind.WARNING,
            title = stringResource(R.string.device_unverified_title),
            text = stringResource(R.string.device_unverified_short, s.deviceModel?.toString() ?: "?"),
        ) {
            BannerAction(
                stringResource(R.string.action_unlock_controls),
                onUnlock,
                enabled = state.connection == Connection.Live && s.deviceModel != null,
            )
        }
    }
}

/** The experimental opt-in: the full explanation, and the user's confirmation that readings match. */
@Composable
private fun UnlockDialog(state: DeviceUiState, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val s = state.state
    var confirmed by rememberSaveable(state.device?.mac, s?.deviceModel) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_warning), contentDescription = null) },
        title = { Text(stringResource(R.string.device_unverified_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.device_unverified_text, s?.deviceModel?.toString() ?: "?", F79d.VERIFIED_MODEL))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().toggleable(confirmed, role = Role.Checkbox, onValueChange = { confirmed = it }),
                ) {
                    Checkbox(checked = confirmed, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.device_unverified_confirm), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = confirmed && state.connection == Connection.Live && s?.deviceModel != null) {
                Text(stringResource(R.string.action_unlock_controls))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Phase, soft water left, flow, and the actions that move the valve. */
@Composable
private fun StatusHero(s: SoftenerState, state: DeviceUiState, onRegenerate: () -> Unit, onVacation: (start: Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    SectionCard(containerColor = colors.primaryContainer) {
        val muted = LocalContentColor.current.copy(alpha = 0.75f)
        Text(stringResource(R.string.device_status_title), style = MaterialTheme.typography.labelLarge, color = muted)
        val phase = stationText(s.station, s.stationCode)
        val remaining = s.phaseRemainingSeconds
        Text(
            if (remaining != null) stringResource(R.string.device_phase_remaining, phase, durationText(remaining)) else phase,
            style = MaterialTheme.typography.headlineMedium,
        )
        if (s.station == Station.CLOSED) {
            StatusLine(StatusKind.WARNING, stringResource(R.string.device_closed_reason, closeReasonText(s.closeReason, s.closeReasonCode)))
        }
        if (s.vacationStatus == VacationStatus.PREPARING || s.vacationStatus == VacationStatus.ACTIVE) {
            Text(
                stringResource(R.string.device_vacation) + ": " +
                    stringResource(if (s.vacationStatus == VacationStatus.ACTIVE) R.string.vacation_active else R.string.vacation_preparing),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            HeroStat(stringResource(R.string.device_remaining_capacity), volumeText(s.remainingCapacity, s.volumeUnit), Modifier.weight(1f))
            HeroStat(stringResource(R.string.device_flow_now), flowText(s.flowRateHundredths, s.volumeUnit), Modifier.weight(1f))
        }
        val left = s.remainingCapacity
        val perCycle = s.capacityPerCycle
        if (left != null && perCycle != null && perCycle > 0) {
            LinearProgressIndicator(
                progress = { (left / perCycle).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = colors.primary,
                trackColor = colors.onPrimaryContainer.copy(alpha = 0.12f),
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Text(
                stringResource(R.string.device_capacity_per_cycle) + ": " + volumeText(perCycle, s.volumeUnit),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
        if (s.regenerationByTime == true) {
            Text(
                stringResource(R.string.device_days_remaining) + ": " + (s.remainingDays?.toString() ?: "–"),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }

        HeroActions(s, state, onRegenerate, onVacation)
    }
}

/** Regenerate and, where offered, vacation mode. While on vacation only "End vacation mode" remains. */
@Composable
private fun HeroActions(s: SoftenerState, state: DeviceUiState, onRegenerate: () -> Unit, onVacation: (start: Boolean) -> Unit) {
    val pending = state.pendingWrite
    val regenerating = pending == SoftenerSetting.Regenerate
    val vacationPending = pending is SoftenerSetting.Vacation
    when {
        state.offersVacation && s.vacationFlag == true -> {
            HeroButton(R.string.action_vacation_end, R.drawable.ic_beach, state.canEndVacation, vacationPending, Modifier.fillMaxWidth()) {
                onVacation(false)
            }
            if (s.station != Station.PAUSE_2) {
                Text(
                    stringResource(R.string.vacation_end_wait),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.75f),
                )
            }
        }
        state.offersVacation -> Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroButton(R.string.action_regenerate, R.drawable.ic_autorenew, state.canRegenerate, regenerating, Modifier.weight(1f), onRegenerate)
            HeroButton(R.string.action_vacation, R.drawable.ic_beach, state.canStartVacation, vacationPending, Modifier.weight(1f)) {
                onVacation(true)
            }
        }
        else -> HeroButton(
            R.string.action_regenerate_now,
            R.drawable.ic_autorenew,
            state.canRegenerate,
            regenerating,
            Modifier.fillMaxWidth().padding(top = 4.dp),
            onRegenerate,
        )
    }
}

@Composable
private fun HeroButton(
    @StringRes label: Int,
    @DrawableRes icon: Int,
    enabled: Boolean,
    pending: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
    ) {
        if (pending) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(stringResource(label), maxLines = 1)
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.75f))
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

/** Alarms and reminders, only when there are any. */
@Composable
private fun AlertsBanner(s: SoftenerState) {
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
    if (alerts.isNotEmpty()) {
        Banner(StatusKind.WARNING, title = stringResource(R.string.device_alerts_title), text = alerts.joinToString("\n"))
    }
}

@Composable
private fun UsageTiles(s: SoftenerState, state: DeviceUiState, onEditSalt: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tile(stringResource(R.string.device_today), volumeText(s.todayConsumption, s.volumeUnit), Modifier.weight(1f))
        Tile(stringResource(R.string.device_weekly_average), volumeText(s.weeklyAverageConsumption, s.volumeUnit), Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tile(
            stringResource(R.string.setting_salt_added),
            s.saltAddedKg?.let { "$it kg" } ?: "–",
            Modifier.weight(1f),
            onEdit = onEditSalt.takeIf { state.controlsEnabled },
        )
        val mode = when (s.regenerationByTime) {
            true -> stringResource(R.string.device_regeneration_by_days, s.serviceDays?.toString() ?: "–")
            false -> stringResource(R.string.device_regeneration_by_volume)
            null -> "–"
        }
        Tile(stringResource(R.string.device_regeneration_mode), mode, Modifier.weight(1f))
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier, onEdit: (() -> Unit)? = null) {
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        val clickable = if (onEdit != null) {
            Modifier.clickable(onClickLabel = stringResource(R.string.cd_edit, label), role = Role.Button, onClick = onEdit)
        } else {
            Modifier
        }
        Row(clickable.fillMaxSize().padding(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium)
            }
            if (onEdit != null) {
                Icon(
                    painterResource(R.drawable.ic_edit),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsList(s: SoftenerState, state: DeviceUiState, onEdit: (Editor) -> Unit, onSyncClock: () -> Unit) {
    val enabled = state.controlsEnabled
    ListCard(title = stringResource(R.string.device_settings_title)) {
        if (!enabled && state.connection == Connection.Live && state.pendingWrite == null) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_lock), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Hint(stringResource(R.string.device_controls_locked))
            }
        }
        val locale = uiLocale()
        val hardness = s.hardnessMgPerLitre?.let {
            stringResource(R.string.value_hardness, it, String.format(locale, "%.1f", it / 10.0))
        } ?: "–"
        SettingRow(stringResource(R.string.setting_hardness), hardness, { onEdit(Editor.HARDNESS) }, enabled = enabled, trailingIcon = R.drawable.ic_edit)
        SettingRow(
            stringResource(R.string.setting_regeneration_time),
            timeText(s.regenerationTime),
            { onEdit(Editor.REGEN_TIME) },
            enabled = enabled,
            trailingIcon = R.drawable.ic_edit,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.device_controller_clock), style = MaterialTheme.typography.bodyLarge)
                Text(timeText(s.controllerTime), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onSyncClock, enabled = enabled) { Text(stringResource(R.string.action_sync_clock)) }
        }

        ListSubheader(stringResource(R.string.device_safehome_title))
        val continuous = s.continuousFlowLimitMinutes?.let { if (it == 0) stringResource(R.string.value_off) else stringResource(R.string.value_minutes, it) } ?: "–"
        SettingRow(
            stringResource(R.string.setting_continuous_flow),
            continuous,
            { onEdit(Editor.CONTINUOUS_FLOW) },
            enabled = enabled,
            trailingIcon = R.drawable.ic_edit,
        )
        val shutoff = s.flowShutoffHundredths?.let { if (it == 0) stringResource(R.string.value_off) else flowText(it, s.volumeUnit) } ?: "–"
        // Only the cubic-metre unit family has been verified for field 7 writes.
        SettingRow(
            stringResource(R.string.setting_flow_shutoff),
            shutoff,
            { onEdit(Editor.FLOW_SHUTOFF) },
            enabled = enabled && s.volumeUnit == VolumeUnit.CUBIC_METRES,
            trailingIcon = R.drawable.ic_edit,
        )
    }
}

/** Everything that is useful only occasionally, folded by default. */
@Composable
private fun Details(s: SoftenerState, state: DeviceUiState, onCompatibility: () -> Unit) {
    Text(
        stringResource(R.string.device_details_title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
    ExpandableCard(stringResource(R.string.device_salt_title)) {
        val vacation = when (s.vacationStatus) {
            VacationStatus.OFF -> stringResource(R.string.vacation_off)
            VacationStatus.PREPARING -> stringResource(R.string.vacation_preparing)
            VacationStatus.ACTIVE -> stringResource(R.string.vacation_active)
            null -> "–"
        }
        ValueRow(stringResource(R.string.device_vacation), vacation)
        Hint(stringResource(R.string.device_vacation_hint))
        ValueRow(stringResource(R.string.alert_low_brine), yesNo(s.lowBrineAlarm))
        ValueRow(stringResource(R.string.alert_salt_reminder), yesNo(s.saltReminder))
        ValueRow(stringResource(R.string.device_regeneration_reminder), s.regenerationReminderCount?.toString() ?: "–")
        ValueRow(stringResource(R.string.device_filter_days), s.filterMaterialDays?.let { daysText(it) } ?: "–")
    }
    ExpandableCard(stringResource(R.string.device_programme_title)) {
        ValueRow(stringResource(R.string.programme_backwash), durationText(s.backwashSeconds))
        ValueRow(stringResource(R.string.programme_brine_slow_rinse), durationText(s.brineSlowRinseSeconds))
        ValueRow(stringResource(R.string.programme_brine_refill), durationText(s.brineRefillSeconds))
        ValueRow(stringResource(R.string.programme_fast_rinse), durationText(s.fastRinseSeconds))
        ValueRow(stringResource(R.string.programme_rinsing_frequency), s.rinsingFrequency?.toString() ?: "–")
        ValueRow(stringResource(R.string.programme_backwash_interval), s.backwashIntervalCount?.toString() ?: "–")
        ValueRow(stringResource(R.string.programme_max_interval), s.maxRegenerationIntervalDays?.let { daysText(it) } ?: "–")
        val locale = uiLocale()
        ValueRow(
            stringResource(R.string.programme_resin_volume),
            s.resinVolumeLitres?.let { String.format(locale, if (it % 1.0 == 0.0) "%.0f L" else "%.1f L", it) } ?: "–",
        )
        // WaterDevice knows only 0 = b-01 and 1 = b-02; the Midnight reports 2, so show other codes raw.
        ValueRow(
            stringResource(R.string.programme_output_mode),
            when (val mode = s.outputRelayMode) {
                null -> "–"
                0, 1 -> "b-0${mode + 1}"
                else -> stringResource(R.string.value_unknown_code, mode.toString())
            },
        )
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
    ExpandableCard(stringResource(R.string.compatibility_identity_title)) {
        state.device?.let { device ->
            ValueRow(stringResource(R.string.device_address_label), device.lastIp)
            ValueRow("MAC", device.mac)
        }
        state.link?.ssid?.let { ValueRow(stringResource(R.string.setup_ssid_label), it) }
        ValueRow(stringResource(R.string.compatibility_module_type), state.device?.deviceType?.let { "0x%04X".format(it) } ?: "–")
        ValueRow(stringResource(R.string.compatibility_model_code), s.deviceModel?.toString() ?: "–")
        ValueRow(
            stringResource(R.string.compatibility_profile),
            state.profile?.name ?: stringResource(
                if (state.device != null && !ControllerProfiles.supportsTransport(state.device.deviceType)) {
                    R.string.compatibility_profile_unavailable
                } else {
                    R.string.compatibility_profile_unknown
                },
            ),
        )
        Hint(stringResource(if (state.isVerifiedModel) R.string.compatibility_profile_verified else R.string.compatibility_profile_hint))
        TextButton(onClick = onCompatibility) { Text(stringResource(R.string.compatibility_title)) }
    }
    ExpandableCard(stringResource(R.string.device_raw_title)) {
        Hint(stringResource(R.string.device_raw_hint))
        s.fields.toSortedMap().forEach { (id, bytes) ->
            ValueRow("$id  ${F79d.FIELD_NAMES[id].orEmpty()}", "%02X %02X".format(bytes.first, bytes.second))
        }
    }
}
