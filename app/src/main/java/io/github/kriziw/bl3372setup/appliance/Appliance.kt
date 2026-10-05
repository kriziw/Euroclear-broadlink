package io.github.kriziw.bl3372setup.appliance

import java.time.LocalTime

/**
 * Softener brands reached over their own local HTTP APIs, as opposed to the Runxin controller
 * behind a BroadLink BL3372 module. Every one is experimental until confirmed on real hardware.
 */
enum class Brand(val id: String, val displayName: String, val defaultPort: Int, val credential: CredentialKind) {
    JUDO("judo", "JUDO", 80, CredentialKind.USER_PASSWORD),
    BWT_PERLA("bwt", "BWT Perla", 8080, CredentialKind.CODE),
    GRUENBECK("gruenbeck", "Grünbeck softliQ", 80, CredentialKind.NONE),
    SYR_NEOSOFT("syr", "SYR NeoSoft", 5333, CredentialKind.NONE),
    ;

    companion object {
        fun of(id: String?): Brand? = entries.firstOrNull { it.id == id }
    }
}

enum class CredentialKind { NONE, CODE, USER_PASSWORD }

/** How to reach one appliance. [secret] is the password or login code, kept only on this phone. */
data class ApplianceAddress(
    val host: String,
    val port: Int,
    val user: String? = null,
    val secret: String? = null,
) {
    override fun toString() = "ApplianceAddress(host=$host, port=$port, user=$user, secret=${if (secret == null) null else "***"})"
}

/** What the appliance is doing, as far as its API says. */
enum class ApplianceStatus { IN_SERVICE, REGENERATING, HOLIDAY, VALVE_CLOSED, OUT_OF_SERVICE, UNKNOWN }

/** Regeneration steps some appliances report (Grünbeck softliQ). */
enum class RegenerationStep { FILL_BRINE, SALTING, SLOW_RINSE, BACKWASH, WASH_OUT }

/** A measured or reported value. Each kind has a fixed unit; the UI formats it. */
enum class ReadingKind {
    REMAINING_CAPACITY_L,
    REMAINING_CAPACITY_2_L,
    SALT_LEVEL_PERCENT,
    SALT_STOCK_KG,
    SALT_RANGE_DAYS,
    SALT_RANGE_WEEKS,
    FLOW_L_H,
    FLOW_M3_H,
    WATER_TODAY_L,
    WATER_MONTH_L,
    WATER_YEAR_L,
    WATER_TOTAL_M3,
    SOFT_WATER_TOTAL_M3,
    RAW_HARDNESS_DH,
    SOFT_HARDNESS_DH,
    TARGET_HARDNESS,
    MAINTENANCE_DAYS,
    REGENERATIONS,
    OPERATING_DAYS,
}

data class Reading(val kind: ReadingKind, val value: Double, val unitText: String? = null)

/** Known alert meanings; anything else is shown with its raw code. */
enum class AlertKind { SALT_LOW, SALT_EMPTY, MAINTENANCE_DUE, POWER_OUTAGE, LEAK, FLOW_LIMIT, MOTOR_OR_VALVE, SENSOR, BRINE, WATER_METER, SOFTWARE_UPDATE, OTHER }

data class Alert(val kind: AlertKind, val code: String, val fatal: Boolean)

enum class SettingKey { TARGET_HARDNESS, SALT_STOCK, SALT_WARNING_DAYS, OPERATING_MODE, REGENERATION_MODE, REGENERATION_INTERVAL, REGENERATION_TIME }

/** Labels for choice settings; the UI translates them. */
enum class ChoiceLabel { ECO, POWER, COMFORT, INDIVIDUAL, STANDARD, AUTOMATIC }

data class Choice(val code: Int, val label: ChoiceLabel)

sealed interface ApplianceSetting {
    val key: SettingKey

    data class Number(
        override val key: SettingKey,
        val value: Double?,
        val min: Double,
        val max: Double,
        val step: Double,
        val unit: String,
    ) : ApplianceSetting

    data class Options(override val key: SettingKey, val value: Int?, val options: List<Choice>) : ApplianceSetting

    data class Time(override val key: SettingKey, val value: LocalTime?) : ApplianceSetting
}

enum class ApplianceAction { REGENERATE, CLOSE_VALVE, OPEN_VALVE }

/** One read of an appliance. Fields the device does not report stay empty. */
data class ApplianceState(
    val model: String? = null,
    /** Identity code from the device (e.g. JUDO type byte); scopes the experimental opt-in. */
    val modelCode: Int? = null,
    val firmware: String? = null,
    val status: ApplianceStatus = ApplianceStatus.UNKNOWN,
    val step: RegenerationStep? = null,
    val readings: List<Reading> = emptyList(),
    val alerts: List<Alert> = emptyList(),
    val settings: List<ApplianceSetting> = emptyList(),
    val actions: List<ApplianceAction> = emptyList(),
    val nextMaintenance: String? = null,
    val lastRegeneration: String? = null,
    /** False while a slow device is still being read. */
    val complete: Boolean = true,
) {
    fun reading(kind: ReadingKind): Reading? = readings.firstOrNull { it.kind == kind }
    fun setting(key: SettingKey): ApplianceSetting? = settings.firstOrNull { it.key == key }
}

/** A requested change: a new value for a setting, or an action. */
sealed interface ApplianceChange {
    data class SetNumber(val key: SettingKey, val value: Double) : ApplianceChange
    data class SetOption(val key: SettingKey, val code: Int) : ApplianceChange
    data class SetTime(val key: SettingKey, val time: LocalTime) : ApplianceChange
    data class Run(val action: ApplianceAction) : ApplianceChange
}

sealed interface ChangeResult {
    /** A fresh read shows the requested value (or the action's effect). */
    data class Confirmed(val state: ApplianceState?) : ChangeResult

    /** The appliance answered, but a fresh read does not show the change. */
    data class NotConfirmed(val state: ApplianceState?) : ChangeResult

    /** Accepted by the appliance, which offers no way to read the result back (e.g. JUDO actions). */
    data class Accepted(val state: ApplianceState?) : ChangeResult
}

/** One appliance protocol. Implementations serialise their own requests. */
interface ApplianceDriver {
    val brand: Brand

    /** Reads the appliance. Slow devices call [publish] with partial states while reading. */
    suspend fun read(publish: (ApplianceState) -> Unit = {}): ApplianceState

    /** Sends [change] once, then reads back. Never resends. */
    suspend fun apply(change: ApplianceChange): ChangeResult

    /** Delay between polls; some appliances limit how often they can be asked. */
    val pollIntervalMillis: Long get() = 30_000
}
