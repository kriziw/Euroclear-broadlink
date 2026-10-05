package io.github.kriziw.bl3372setup.appliance.syr

import io.github.kriziw.bl3372setup.appliance.Alert
import io.github.kriziw.bl3372setup.appliance.AlertKind
import io.github.kriziw.bl3372setup.appliance.ApplianceAddress
import io.github.kriziw.bl3372setup.appliance.ApplianceChange
import io.github.kriziw.bl3372setup.appliance.ApplianceDriver
import io.github.kriziw.bl3372setup.appliance.ApplianceSetting
import io.github.kriziw.bl3372setup.appliance.ApplianceState
import io.github.kriziw.bl3372setup.appliance.ApplianceStatus
import io.github.kriziw.bl3372setup.appliance.Brand
import io.github.kriziw.bl3372setup.appliance.ChangeResult
import io.github.kriziw.bl3372setup.appliance.Choice
import io.github.kriziw.bl3372setup.appliance.ChoiceLabel
import io.github.kriziw.bl3372setup.appliance.Reading
import io.github.kriziw.bl3372setup.appliance.ReadingKind
import io.github.kriziw.bl3372setup.appliance.SettingKey
import io.github.kriziw.bl3372setup.network.LocalHttp
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * SYR NeoSoft 2500 / 5000 Connect local JSON API, as published by SYR
 * (iotsyrpublicapi.z1.web.core.windows.net): `GET http://<host>:5333/neosoft/get/all` and
 * `GET .../neosoft/set/<key>/<value>`, no login. Firmware quirks (lower-case set paths, literal
 * colons, missing content type, mixed value types) follow the community alexhass/syr_connect (MIT).
 */
object SyrNeoSoftProtocol {
    const val BASE = "/neosoft"
    const val GET_ALL = "$BASE/get/all"

    val MODES = listOf(
        Choice(1, ChoiceLabel.STANDARD), Choice(2, ChoiceLabel.ECO), Choice(3, ChoiceLabel.POWER), Choice(4, ChoiceLabel.AUTOMATIC),
    )

    private val HARDNESS_UNITS = listOf("°dH", "°fH", "ppm", "mmol/l")

    /** Set paths are lower case and values are sent literally (`/set/rtm/02:30`). */
    fun setPath(key: String, value: String) = "$BASE/set/${key.lowercase()}/$value"

    /** Codes are hex strings; `ff` means none. Lists such as `04,01,ff` use their first entry. */
    private fun code(raw: Any?): String? =
        raw?.toString()?.split(',')?.firstOrNull()?.trim()?.uppercase()?.takeUnless { it.isEmpty() || it == "FF" }

    fun alarm(code: String): Alert = Alert(
        kind = when (code) {
            "0D" -> AlertKind.SALT_EMPTY
            "0E", "A1", "A2" -> AlertKind.MOTOR_OR_VALVE
            "A3", "A4", "A5", "A6", "A7" -> AlertKind.LEAK
            "A8", "A9", "AA", "AB", "AC" -> AlertKind.SENSOR
            else -> AlertKind.OTHER
        },
        code = code,
        fatal = true,
    )

    fun warning(code: String): Alert = Alert(
        kind = when (code) {
            "01" -> AlertKind.POWER_OUTAGE
            "02" -> AlertKind.SALT_LOW
            "07", "0A", "0B", "11", "A6" -> AlertKind.LEAK
            else -> AlertKind.OTHER
        },
        code = code,
        fatal = false,
    )

    fun notification(code: String): Alert = Alert(
        kind = when (code) {
            "01", "04" -> AlertKind.SOFTWARE_UPDATE
            "02", "03", "07", "08" -> AlertKind.MAINTENANCE_DUE
            else -> AlertKind.OTHER
        },
        code = code,
        fatal = false,
    )

    fun parse(json: JSONObject): ApplianceState {
        fun raw(key: String): Any? = if (json.has("get$key")) json.get("get$key") else null
        fun num(key: String): Double? = when (val v = raw(key)) {
            is Number -> v.toDouble()
            is String -> v.trim().replace(',', '.').toDoubleOrNull()
            else -> null
        }
        fun text(key: String): String? = raw(key)?.toString()?.trim()?.ifEmpty { null }

        val dual = num("RE2") != null
        val unit = num("WHU")?.toInt()?.let { HARDNESS_UNITS.getOrNull(it) } ?: "°dH"
        val readings = buildList {
            num("RE1")?.let { add(Reading(ReadingKind.REMAINING_CAPACITY_L, it)) }
            num("RE2")?.let { add(Reading(ReadingKind.REMAINING_CAPACITY_2_L, it)) }
            num("SV1")?.let { add(Reading(ReadingKind.SALT_STOCK_KG, it)) }
            num("SS1")?.let { add(Reading(ReadingKind.SALT_RANGE_WEEKS, it)) }
            num("FLO")?.let { add(Reading(ReadingKind.FLOW_L_H, it)) }
            num("VOL")?.let { add(Reading(ReadingKind.WATER_TOTAL_M3, it / 1000.0)) }
            num("IWH")?.let { add(Reading(ReadingKind.RAW_HARDNESS_DH, it, unit)) }
            num("OWH")?.let { add(Reading(ReadingKind.SOFT_HARDNESS_DH, it, unit)) }
        }
        val regenerating = (num("RG1") ?: 0.0) > 0 || (num("RG2") ?: 0.0) > 0
        val alerts = listOfNotNull(
            code(raw("ALA"))?.let(::alarm),
            code(raw("WRN"))?.let(::warning),
            code(raw("NOT"))?.let(::notification),
        )
        val settings = buildList {
            add(ApplianceSetting.Options(SettingKey.REGENERATION_MODE, num("RMO")?.toInt(), MODES))
            // The NeoSoft 5000 (two tanks) starts regenerations itself; interval and time do not apply.
            if (!dual) {
                add(ApplianceSetting.Number(SettingKey.REGENERATION_INTERVAL, num("RPD"), 1.0, 3.0, 1.0, "d"))
                add(ApplianceSetting.Time(SettingKey.REGENERATION_TIME, text("RTM")?.let(::time)))
            }
        }
        return ApplianceState(
            model = if (dual) "SYR NeoSoft 5000" else "SYR NeoSoft 2500",
            modelCode = num("TYP")?.toInt(),
            firmware = text("VER"),
            status = if (regenerating) ApplianceStatus.REGENERATING else ApplianceStatus.IN_SERVICE,
            readings = readings,
            alerts = alerts,
            settings = settings,
            nextMaintenance = text("SRV"),
            lastRegeneration = num("LAR")?.toLong()?.takeIf { it > 0 }?.let {
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()))
            },
        )
    }

    fun time(text: String): LocalTime? {
        val parts = text.split(':')
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null
    }

    /** `{"setrmo2":"OK"}`; errors are `NSC` (no such command) and `MIMA` (out of range). */
    fun accepted(body: String): Boolean = try {
        val json = JSONObject(body)
        json.keys().asSequence().any { json.optString(it).equals("OK", ignoreCase = true) }
    } catch (_: JSONException) {
        false
    }
}

class SyrNeoSoftDriver(private val http: LocalHttp, private val address: ApplianceAddress) : ApplianceDriver {
    override val brand = Brand.SYR_NEOSOFT
    private val lock = Mutex()

    override suspend fun read(publish: (ApplianceState) -> Unit): ApplianceState = lock.withLock { readUnlocked() }

    private suspend fun readUnlocked(): ApplianceState {
        val response = http.get(address.host, address.port, SyrNeoSoftProtocol.GET_ALL)
        if (response.status != 200) throw IOException("SYR HTTP ${response.status}")
        return try {
            SyrNeoSoftProtocol.parse(JSONObject(response.body))
        } catch (e: JSONException) {
            throw IOException("SYR: unexpected reply", e)
        }
    }

    override suspend fun apply(change: ApplianceChange): ChangeResult = lock.withLock {
        val (key, value) = when {
            change is ApplianceChange.SetOption && change.key == SettingKey.REGENERATION_MODE -> {
                require(SyrNeoSoftProtocol.MODES.any { it.code == change.code })
                "RMO" to change.code.toString()
            }
            change is ApplianceChange.SetNumber && change.key == SettingKey.REGENERATION_INTERVAL -> {
                require(change.value in 1.0..3.0)
                "RPD" to change.value.toInt().toString()
            }
            change is ApplianceChange.SetTime && change.key == SettingKey.REGENERATION_TIME ->
                "RTM" to "%02d:%02d".format(change.time.hour, change.time.minute)
            else -> throw IllegalArgumentException("SYR NeoSoft has no change $change")
        }
        val response = http.get(address.host, address.port, SyrNeoSoftProtocol.setPath(key, value))
        if (response.status != 200 || !SyrNeoSoftProtocol.accepted(response.body)) {
            throw IOException("SYR rejected $key: ${response.body.take(40)}")
        }
        val state = try { readUnlocked() } catch (_: IOException) { null }
        val confirmed = when (change) {
            is ApplianceChange.SetOption -> (state?.setting(change.key) as? ApplianceSetting.Options)?.value == change.code
            is ApplianceChange.SetNumber -> (state?.setting(change.key) as? ApplianceSetting.Number)?.value == change.value
            is ApplianceChange.SetTime -> (state?.setting(change.key) as? ApplianceSetting.Time)?.value == change.time
            else -> false
        }
        if (confirmed) ChangeResult.Confirmed(state) else ChangeResult.NotConfirmed(state)
    }
}
