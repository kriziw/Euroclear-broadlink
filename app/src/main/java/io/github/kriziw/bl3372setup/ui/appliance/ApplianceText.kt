package io.github.kriziw.bl3372setup.ui.appliance

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.kriziw.bl3372setup.R
import io.github.kriziw.bl3372setup.appliance.Alert
import io.github.kriziw.bl3372setup.appliance.AlertKind
import io.github.kriziw.bl3372setup.appliance.ApplianceAction
import io.github.kriziw.bl3372setup.appliance.ApplianceStatus
import io.github.kriziw.bl3372setup.appliance.Brand
import io.github.kriziw.bl3372setup.appliance.ChoiceLabel
import io.github.kriziw.bl3372setup.appliance.Reading
import io.github.kriziw.bl3372setup.appliance.ReadingKind
import io.github.kriziw.bl3372setup.appliance.RegenerationStep
import io.github.kriziw.bl3372setup.appliance.SettingKey
import io.github.kriziw.bl3372setup.ui.common.daysText
import io.github.kriziw.bl3372setup.ui.common.uiLocale
import kotlin.math.roundToInt

@Composable
fun readingLabel(kind: ReadingKind): String = stringResource(
    when (kind) {
        ReadingKind.REMAINING_CAPACITY_L -> R.string.device_remaining_capacity
        ReadingKind.REMAINING_CAPACITY_2_L -> R.string.reading_remaining_capacity_2
        ReadingKind.SALT_LEVEL_PERCENT -> R.string.reading_salt_level
        ReadingKind.SALT_STOCK_KG -> R.string.reading_salt_stock
        ReadingKind.SALT_RANGE_DAYS, ReadingKind.SALT_RANGE_WEEKS -> R.string.reading_salt_range
        ReadingKind.FLOW_L_H, ReadingKind.FLOW_M3_H -> R.string.device_flow_now
        ReadingKind.WATER_TODAY_L -> R.string.device_today
        ReadingKind.WATER_MONTH_L -> R.string.reading_water_month
        ReadingKind.WATER_YEAR_L -> R.string.reading_water_year
        ReadingKind.WATER_TOTAL_M3 -> R.string.reading_water_total
        ReadingKind.SOFT_WATER_TOTAL_M3 -> R.string.reading_soft_water_total
        ReadingKind.RAW_HARDNESS_DH -> R.string.setting_hardness
        ReadingKind.SOFT_HARDNESS_DH -> R.string.reading_soft_hardness
        ReadingKind.TARGET_HARDNESS -> R.string.setting_target_hardness
        ReadingKind.MAINTENANCE_DAYS -> R.string.reading_maintenance
        ReadingKind.REGENERATIONS -> R.string.reading_regenerations
        ReadingKind.OPERATING_DAYS -> R.string.reading_operating_days
    },
)

@Composable
fun readingValue(reading: Reading): String {
    val locale = uiLocale()
    val v = reading.value
    fun f(pattern: String) = String.format(locale, pattern, v)
    return when (reading.kind) {
        ReadingKind.REMAINING_CAPACITY_L, ReadingKind.REMAINING_CAPACITY_2_L,
        ReadingKind.WATER_TODAY_L, ReadingKind.WATER_MONTH_L, ReadingKind.WATER_YEAR_L -> f("%.0f L")
        ReadingKind.SALT_LEVEL_PERCENT -> f("%.0f %%")
        ReadingKind.SALT_STOCK_KG -> f("%.1f kg")
        ReadingKind.SALT_RANGE_DAYS, ReadingKind.MAINTENANCE_DAYS, ReadingKind.OPERATING_DAYS -> daysText(v.roundToInt())
        ReadingKind.SALT_RANGE_WEEKS -> pluralStringResource(R.plurals.value_weeks, v.roundToInt(), v.roundToInt())
        ReadingKind.FLOW_L_H -> f("%.0f L/h")
        ReadingKind.FLOW_M3_H -> f("%.2f m³/h")
        ReadingKind.WATER_TOTAL_M3, ReadingKind.SOFT_WATER_TOTAL_M3 -> f("%.2f m³")
        ReadingKind.RAW_HARDNESS_DH, ReadingKind.SOFT_HARDNESS_DH, ReadingKind.TARGET_HARDNESS ->
            f(if (v % 1.0 == 0.0) "%.0f" else "%.1f") + " " + (reading.unitText ?: "°dH")
        ReadingKind.REGENERATIONS -> f("%.0f")
    }
}

@Composable
fun settingLabel(key: SettingKey): String = stringResource(
    when (key) {
        SettingKey.TARGET_HARDNESS -> R.string.setting_target_hardness
        SettingKey.SALT_STOCK -> R.string.reading_salt_stock
        SettingKey.SALT_WARNING_DAYS -> R.string.setting_salt_warning
        SettingKey.OPERATING_MODE -> R.string.setting_operating_mode
        SettingKey.REGENERATION_MODE -> R.string.setting_regeneration_mode
        SettingKey.REGENERATION_INTERVAL -> R.string.setting_regeneration_interval
        SettingKey.REGENERATION_TIME -> R.string.setting_regeneration_time
    },
)

@Composable
fun settingHint(key: SettingKey): String? = when (key) {
    SettingKey.TARGET_HARDNESS -> stringResource(R.string.setting_target_hardness_hint)
    SettingKey.SALT_STOCK -> stringResource(R.string.setting_salt_stock_hint)
    SettingKey.SALT_WARNING_DAYS -> stringResource(R.string.setting_salt_warning_hint)
    SettingKey.REGENERATION_TIME -> stringResource(R.string.setting_regeneration_time_hint)
    else -> null
}

@Composable
fun choiceLabel(label: ChoiceLabel): String = stringResource(
    when (label) {
        ChoiceLabel.ECO -> R.string.choice_eco
        ChoiceLabel.POWER -> R.string.choice_power
        ChoiceLabel.COMFORT -> R.string.choice_comfort
        ChoiceLabel.INDIVIDUAL -> R.string.choice_individual
        ChoiceLabel.STANDARD -> R.string.choice_standard
        ChoiceLabel.AUTOMATIC -> R.string.choice_automatic
    },
)

@Composable
fun actionLabel(action: ApplianceAction): String = stringResource(
    when (action) {
        ApplianceAction.REGENERATE -> R.string.action_regenerate_now
        ApplianceAction.CLOSE_VALVE -> R.string.action_close_valve
        ApplianceAction.OPEN_VALVE -> R.string.action_open_valve
    },
)

@Composable
fun statusText(status: ApplianceStatus, step: RegenerationStep?): String {
    val base = stringResource(
        when (status) {
            ApplianceStatus.IN_SERVICE -> R.string.station_in_service
            ApplianceStatus.REGENERATING -> R.string.status_regenerating
            ApplianceStatus.HOLIDAY -> R.string.status_holiday
            ApplianceStatus.VALVE_CLOSED -> R.string.station_closed
            ApplianceStatus.OUT_OF_SERVICE -> R.string.status_out_of_service
            ApplianceStatus.UNKNOWN -> R.string.status_not_reported
        },
    )
    val stepText = step?.let {
        stringResource(
            when (it) {
                RegenerationStep.FILL_BRINE -> R.string.station_brine_refill
                RegenerationStep.SALTING -> R.string.step_salting
                RegenerationStep.SLOW_RINSE -> R.string.step_slow_rinse
                RegenerationStep.BACKWASH -> R.string.station_backwash
                RegenerationStep.WASH_OUT -> R.string.step_wash_out
            },
        )
    }
    return if (stepText != null) "$base · $stepText" else base
}

@Composable
fun alertText(alert: Alert): String = stringResource(
    R.string.alert_with_code,
    stringResource(
        when (alert.kind) {
            AlertKind.SALT_LOW -> R.string.alert_kind_salt_low
            AlertKind.SALT_EMPTY -> R.string.alert_kind_salt_empty
            AlertKind.MAINTENANCE_DUE -> R.string.alert_kind_maintenance
            AlertKind.POWER_OUTAGE -> R.string.alert_kind_power
            AlertKind.LEAK -> R.string.alert_kind_leak
            AlertKind.FLOW_LIMIT -> R.string.alert_kind_flow
            AlertKind.MOTOR_OR_VALVE -> R.string.alert_kind_motor
            AlertKind.SENSOR -> R.string.alert_kind_sensor
            AlertKind.BRINE -> R.string.alert_kind_brine
            AlertKind.WATER_METER -> R.string.alert_kind_meter
            AlertKind.SOFTWARE_UPDATE -> R.string.alert_kind_update
            AlertKind.OTHER -> R.string.alert_kind_other
        },
    ),
    alert.code,
)

@Composable
fun brandHint(brand: Brand): String = stringResource(
    when (brand) {
        Brand.JUDO -> R.string.appliance_brand_judo_hint
        Brand.BWT_PERLA -> R.string.appliance_brand_bwt_hint
        Brand.GRUENBECK -> R.string.appliance_brand_gruenbeck_hint
        Brand.SYR_NEOSOFT -> R.string.appliance_brand_syr_hint
    },
)

@Composable
fun brandSetupHint(brand: Brand): String = stringResource(
    when (brand) {
        Brand.JUDO -> R.string.appliance_judo_setup
        Brand.BWT_PERLA -> R.string.appliance_bwt_setup
        Brand.GRUENBECK -> R.string.appliance_gruenbeck_setup
        Brand.SYR_NEOSOFT -> R.string.appliance_syr_setup
    },
)

/** Where each protocol is documented; shown on the dashboard and in the compatibility guide. */
fun brandSource(brand: Brand): String = when (brand) {
    Brand.JUDO -> "https://judo.eu/app/uploads/2024/11/API-KOMMANDOZEILEN.pdf"
    Brand.BWT_PERLA -> "https://github.com/dkarv/ha-bwt-perla"
    Brand.GRUENBECK -> "https://github.com/tizianodeg/gruenbeck_softliQ_SC"
    Brand.SYR_NEOSOFT -> "https://iotsyrpublicapi.z1.web.core.windows.net/"
}
