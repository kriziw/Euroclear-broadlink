package io.github.kriziw.bl3372setup.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.kriziw.bl3372setup.R
import io.github.kriziw.bl3372setup.network.NetworkError
import io.github.kriziw.bl3372setup.runxin.CloseReason
import io.github.kriziw.bl3372setup.runxin.Station
import io.github.kriziw.bl3372setup.runxin.VolumeUnit
import java.time.LocalTime
import java.util.Locale

/** The locale the UI is rendered in. Read from the Configuration so per-app language changes recompose. */
@Composable
fun uiLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
fun networkErrorText(error: NetworkError): String = when (error) {
    NetworkError.LocalNetworkBlocked -> stringResource(R.string.error_local_network_blocked)
    NetworkError.NotConnected -> stringResource(R.string.error_not_connected)
    NetworkError.Timeout -> stringResource(R.string.error_timeout)
    NetworkError.AuthRejected -> stringResource(R.string.error_auth_rejected)
    is NetworkError.Other -> error.detail
}

@Composable
fun stationText(station: Station?, code: Int?): String = when (station) {
    Station.IN_SERVICE -> stringResource(R.string.station_in_service)
    Station.BACKWASH -> stringResource(R.string.station_backwash)
    Station.BRINE_DRAW -> stringResource(R.string.station_brine_draw)
    Station.BRINE_REFILL -> stringResource(R.string.station_brine_refill)
    Station.FAST_RINSE -> stringResource(R.string.station_fast_rinse)
    Station.CLOSED -> stringResource(R.string.station_closed)
    Station.SALT_DISSOLVING -> stringResource(R.string.station_salt_dissolving)
    Station.PAUSE_1 -> stringResource(R.string.station_pause_1)
    Station.PAUSE_2 -> stringResource(R.string.station_pause_2)
    null -> stringResource(R.string.value_unknown_code, code?.toString() ?: "–")
}

@Composable
fun closeReasonText(reason: CloseReason?, code: Int?): String = when (reason) {
    CloseReason.MANUAL -> stringResource(R.string.close_reason_manual)
    CloseReason.LEAK_DETECTED -> stringResource(R.string.close_reason_leak)
    CloseReason.CONTINUOUS_FLOW_TIMEOUT -> stringResource(R.string.close_reason_continuous_flow)
    CloseReason.FLOW_RATE_EXCEEDED -> stringResource(R.string.close_reason_flow_exceeded)
    null -> stringResource(R.string.value_unknown_code, code?.toString() ?: "–")
}

@Composable
fun volumeUnitText(unit: VolumeUnit?): String = when (unit) {
    VolumeUnit.GALLONS -> "gal"
    VolumeUnit.LITRES -> "L"
    VolumeUnit.CUBIC_METRES -> "m³"
    null -> ""
}

@Composable
fun flowUnitText(unit: VolumeUnit?): String = when (unit) {
    VolumeUnit.GALLONS -> "gpm"
    VolumeUnit.LITRES -> "L/min"
    VolumeUnit.CUBIC_METRES -> "m³/h"
    null -> ""
}

@Composable
fun volumeText(value: Double?, unit: VolumeUnit?): String {
    if (value == null) return "–"
    val locale = uiLocale()
    val number = if (unit == VolumeUnit.CUBIC_METRES) "%.2f".format(locale, value) else "%.0f".format(locale, value)
    return "$number ${volumeUnitText(unit)}"
}

@Composable
fun flowText(hundredths: Int?, unit: VolumeUnit?): String {
    val locale = uiLocale()
    return hundredths?.let { "%.2f %s".format(locale, it / 100.0, flowUnitText(unit)) } ?: "–"
}

/** `1:05:30` or `4:32` from seconds. */
fun durationText(seconds: Int?): String {
    if (seconds == null) return "–"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun timeText(time: LocalTime?): String = time?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "–"

@Composable
fun daysText(days: Int): String = pluralStringResource(R.plurals.value_days, days, days)

@Composable
fun yesNo(value: Boolean?): String = when (value) {
    true -> stringResource(R.string.value_yes)
    false -> stringResource(R.string.value_no)
    null -> "–"
}
