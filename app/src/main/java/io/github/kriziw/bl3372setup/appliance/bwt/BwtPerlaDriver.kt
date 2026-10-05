package io.github.kriziw.bl3372setup.appliance.bwt

import io.github.kriziw.bl3372setup.appliance.Alert
import io.github.kriziw.bl3372setup.appliance.AlertKind
import io.github.kriziw.bl3372setup.appliance.ApplianceAddress
import io.github.kriziw.bl3372setup.appliance.ApplianceChange
import io.github.kriziw.bl3372setup.appliance.ApplianceDriver
import io.github.kriziw.bl3372setup.appliance.ApplianceState
import io.github.kriziw.bl3372setup.appliance.ApplianceStatus
import io.github.kriziw.bl3372setup.appliance.Brand
import io.github.kriziw.bl3372setup.appliance.ChangeResult
import io.github.kriziw.bl3372setup.appliance.Reading
import io.github.kriziw.bl3372setup.appliance.ReadingKind
import io.github.kriziw.bl3372setup.network.LocalHttp
import io.github.kriziw.bl3372setup.network.LoginRejectedException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * BWT Perla One / Duplex (firmware 2.02+) local API: `GET http://<host>:8080/api/GetCurrentData`
 * with HTTP basic auth `user:<login code>`. Read-only: the API offers no commands.
 * Field names and error codes follow the community-maintained dkarv/bwt_api (MIT).
 */
object BwtPerlaProtocol {
    const val PATH = "/api/GetCurrentData"

    private val WARNINGS = setOf(5, 15, 16, 25, 32, 33, 34, 35, 36, 54, 55, 61, 62, 63, 64, 66, 67, 74, 75, 88)

    fun alertKind(code: Int): AlertKind = when (code) {
        5, 25 -> AlertKind.SALT_LOW
        27 -> AlertKind.SALT_EMPTY
        32, 33, 34, 74 -> AlertKind.MAINTENANCE_DUE
        13, 14, 15, 26 -> AlertKind.LEAK
        1, 2, 3, 8, 9, 10, 12, 21, 43, 44, 45, 46, 58, 59, 68 -> AlertKind.MOTOR_OR_VALVE
        61, 62, 63, 64, 66, 67 -> AlertKind.SENSOR
        54 -> AlertKind.WATER_METER
        75 -> AlertKind.BRINE
        else -> AlertKind.OTHER
    }

    fun parse(json: JSONObject): ApplianceState {
        val hardIn = json.optDouble("HardnessIN_dH", Double.NaN)
        val hardOut = json.optDouble("HardnessOUT_dH", Double.NaN)
        val columns = if (json.optInt("CapacityColumn2_ml_dH", -1) == -1) 1 else 2
        /** Treated (0 °dH) water is blended with raw water; report what the house actually gets. */
        fun blended(treated: Double): Double =
            if (hardIn.isNaN() || hardOut.isNaN() || hardIn <= 0 || hardIn == hardOut) treated else treated / (1 - hardOut / hardIn)
        /** Column capacity in ml·°dH, as litres of water at the set output hardness. */
        fun capacityLitres(key: String): Double? {
            val raw = json.optDouble(key, Double.NaN)
            if (raw.isNaN() || raw < 0 || hardIn.isNaN() || hardOut.isNaN() || hardIn <= hardOut) return null
            return raw / (hardIn - hardOut) / 1000.0
        }
        fun num(key: String) = json.optDouble(key, Double.NaN).takeUnless { it.isNaN() }

        val readings = buildList {
            capacityLitres("CapacityColumn1_ml_dH")?.let { add(Reading(ReadingKind.REMAINING_CAPACITY_L, it)) }
            if (columns == 2) capacityLitres("CapacityColumn2_ml_dH")?.let { add(Reading(ReadingKind.REMAINING_CAPACITY_2_L, it)) }
            num("RegenerativLevel")?.let { add(Reading(ReadingKind.SALT_LEVEL_PERCENT, it)) }
            num("RegenerativRemainingDays")?.let { add(Reading(ReadingKind.SALT_RANGE_DAYS, it)) }
            num("CurrentFlowrate_l_h")?.let { add(Reading(ReadingKind.FLOW_L_H, it)) }
            num("WaterTreatedCurrentDay_l")?.let { add(Reading(ReadingKind.WATER_TODAY_L, blended(it))) }
            num("WaterTreatedCurrentMonth_l")?.let { add(Reading(ReadingKind.WATER_MONTH_L, blended(it))) }
            num("WaterTreatedCurrentYear_l")?.let { add(Reading(ReadingKind.WATER_YEAR_L, blended(it))) }
            num("BlendedWaterSinceSetup_l")?.let { add(Reading(ReadingKind.WATER_TOTAL_M3, it / 1000.0)) }
            if (!hardIn.isNaN()) add(Reading(ReadingKind.RAW_HARDNESS_DH, hardIn))
            if (!hardOut.isNaN()) add(Reading(ReadingKind.SOFT_HARDNESS_DH, hardOut))
            num("RegenerationCountSinceSetup")?.let { add(Reading(ReadingKind.REGENERATIONS, it)) }
        }
        val alerts = json.optString("ActiveErrorIDs", "").split(',').mapNotNull { it.trim().toIntOrNull() }.map {
            Alert(alertKind(it), it.toString(), fatal = it !in WARNINGS)
        }
        // HolidayModeStartTime: -1/0 inactive, 1 active, a larger value is a future start time.
        val status = when {
            json.optInt("OutOfService", 0) != 0 -> ApplianceStatus.OUT_OF_SERVICE
            json.optLong("HolidayModeStartTime", 0) == 1L -> ApplianceStatus.HOLIDAY
            else -> ApplianceStatus.IN_SERVICE
        }
        return ApplianceState(
            model = if (columns == 2) "BWT Perla Duplex" else "BWT Perla One",
            modelCode = columns,
            firmware = json.optString("FirmwareVersion", "").ifBlank { null },
            status = status,
            readings = readings,
            alerts = alerts,
            lastRegeneration = json.optString("LastRegenerationColumn1", "").ifBlank { null },
        )
    }
}

class BwtPerlaDriver(private val http: LocalHttp, private val address: ApplianceAddress) : ApplianceDriver {
    override val brand = Brand.BWT_PERLA
    private val lock = Mutex()

    override suspend fun read(publish: (ApplianceState) -> Unit): ApplianceState = lock.withLock {
        val response = http.get(address.host, address.port, BwtPerlaProtocol.PATH, LocalHttp.BasicAuth("user", address.secret.orEmpty()))
        // A wrong login code is answered with an empty 404.
        if (response.status == 401 || response.status == 403 || (response.status == 404 && response.body.isBlank())) {
            throw LoginRejectedException("BWT login code rejected")
        }
        if (response.status != 200) throw IOException("BWT HTTP ${response.status}")
        try {
            BwtPerlaProtocol.parse(JSONObject(response.body))
        } catch (e: JSONException) {
            throw IOException("BWT: unexpected reply", e)
        }
    }

    override suspend fun apply(change: ApplianceChange): ChangeResult =
        throw UnsupportedOperationException("The BWT Perla local API is read-only")
}
