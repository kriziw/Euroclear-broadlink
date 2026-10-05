package io.github.kriziw.bl3372setup.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.kriziw.bl3372setup.R
import io.github.kriziw.bl3372setup.runxin.SoftenerSetting
import io.github.kriziw.bl3372setup.runxin.WriteResult
import io.github.kriziw.bl3372setup.ui.common.Hint
import io.github.kriziw.bl3372setup.ui.common.networkErrorText
import java.time.LocalTime
import java.util.Locale

@Composable
internal fun writeOutcomeText(outcome: WriteOutcome): String {
    val name = settingName(outcome.setting)
    val error = outcome.error
    val setting = outcome.setting
    return when (val result = outcome.result) {
        is WriteResult.Confirmed -> when (setting) {
            SoftenerSetting.Regenerate -> stringResource(R.string.write_regeneration_started)
            is SoftenerSetting.Vacation -> stringResource(if (setting.on) R.string.write_vacation_on else R.string.write_vacation_off)
            else -> stringResource(R.string.write_confirmed, name)
        }
        is WriteResult.NotConfirmed -> when {
            result.ambiguousDelivery -> stringResource(R.string.write_ambiguous, name)
            setting is SoftenerSetting.Vacation -> stringResource(R.string.write_vacation_not_confirmed)
            else -> stringResource(R.string.write_not_confirmed, name)
        }
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
        is SoftenerSetting.Vacation -> R.string.device_vacation
    },
)

@Composable
internal fun NumberDialog(
    title: String,
    unit: String,
    initial: Int,
    range: IntRange,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var field by rememberSelectedText(initial.toString())
    val value = field.text.trim().toIntOrNull()?.takeIf { it in range }
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
internal fun DecimalDialog(
    title: String,
    unit: String,
    initialHundredths: Int,
    rangeHundredths: IntRange,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var field by rememberSelectedText("%.2f".format(Locale.ROOT, initialHundredths / 100.0))
    val hundredths = field.text.trim().replace(',', '.').toBigDecimalOrNull()
        ?.movePointRight(2)?.let { if (it.stripTrailingZeros().scale() <= 0) it.toInt() else null }
        ?.takeIf { it in rangeHundredths }
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
internal fun TimeDialog(title: String, initial: LocalTime, hint: String, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
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
internal fun TextDialog(
    title: String,
    initial: String,
    label: String,
    validate: (String) -> Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    hint: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var field by rememberSelectedText(initial)
    val valid = validate(field.text)
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
                    label = { Text(label) },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                )
                hint?.let { Hint(it) }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(field.text) }, enabled = valid) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** A dialog's starting text, fully selected so that typing replaces it. */
@Composable
internal fun rememberSelectedText(initial: String) =
    rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
