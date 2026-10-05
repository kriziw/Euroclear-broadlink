package io.github.kriziw.bl3372setup.appliance.gruenbeck

import io.github.kriziw.bl3372setup.appliance.Alert
import io.github.kriziw.bl3372setup.appliance.AlertKind
import io.github.kriziw.bl3372setup.appliance.ApplianceAction
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
import io.github.kriziw.bl3372setup.appliance.RegenerationStep
import io.github.kriziw.bl3372setup.appliance.SettingKey
import io.github.kriziw.bl3372setup.network.LocalHttp
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * Grünbeck softliQ SC / MC through the controller's local web server: `POST /mux_http` with a form
 * body `id=<client>&[code=<n>&][edit=<key>>value&]show=<key>|<key>~`, answered with XML elements
 * named after the keys. Keys follow the community integrations tizianodeg/gruenbeck_softliQ_SC
 * (MIT) and OhmegaStar/gruenbeck_softliq_mc; only keys both describe the same way are relied on,
 * plus the SC-only regeneration step and error memory, which are shown when present.
 */
object GruenbeckProtocol {
    const val PATH = "/mux_http"
    private const val CLIENT_ID = 2444

    const val FLOW = "D_A_1_1"            // m³/h
    const val MAINTENANCE_DAYS = "D_A_2_2"
    const val SALT_RANGE_DAYS = "D_A_2_3"
    const val RAW_HARDNESS = "D_D_1"      // °dH
    const val MODE = "D_C_5_1"            // 0 Eco, 1 Power, 2 Comfort, 3 Individual
    const val REGENERATION_STEP = "D_Y_5" // SC: 0 none, 1 fill brine, 2 salting, 3 slow rinse, 4 backwash, 5 wash out
    const val FIRMWARE = "D_Y_6"
    const val START_REGENERATION = "D_B_1"
    const val SYSTEM_TYPE = "D_F_4"       // SC with code 290: 1 SC18, 2 SC23
    const val LAST_ERROR = "D_K_10_1"     // SC with code 245, e.g. "E4_12h"

    val STATE_KEYS = listOf(FLOW, MAINTENANCE_DAYS, SALT_RANGE_DAYS, RAW_HARDNESS, MODE, REGENERATION_STEP, FIRMWARE)

    val MODES = listOf(
        Choice(0, ChoiceLabel.ECO), Choice(1, ChoiceLabel.POWER), Choice(2, ChoiceLabel.COMFORT), Choice(3, ChoiceLabel.INDIVIDUAL),
    )

    fun query(show: List<String>, code: String? = null, edit: Pair<String, String>? = null): String = buildString {
        append("id=").append(CLIENT_ID)
        code?.let { append("&code=").append(it) }
        edit?.let { (key, value) -> append("&edit=").append(key).append('>').append(value) }
        val keys = if (edit != null && edit.first !in show) show + edit.first else show
        append("&show=").append(keys.joinToString("|")).append('~')
    }

    private val ELEMENT = Regex("""<(D_[A-Z0-9_]+)>([^<]*)</\1>""")

    /** The reply is flat XML; elements are read by name, without a general XML parser. */
    fun parse(xml: String): Map<String, String> {
        if (!xml.contains("<")) throw IOException("Grünbeck: unexpected reply")
        return ELEMENT.findAll(xml).associate { it.groupValues[1] to it.groupValues[2].trim() }
    }

    fun step(code: String?): RegenerationStep? = when (code) {
        "1" -> RegenerationStep.FILL_BRINE
        "2" -> RegenerationStep.SALTING
        "3" -> RegenerationStep.SLOW_RINSE
        "4" -> RegenerationStep.BACKWASH
        "5" -> RegenerationStep.WASH_OUT
        else -> null
    }

    /** `E4_12h` -> E4; error codes from the SC integration's translations. */
    fun alert(raw: String?): Alert? {
        val code = raw?.substringBefore('_')?.trim()?.uppercase() ?: return null
        if (code.isEmpty() || code == "0") return null
        val kind = when (code) {
            "E1" -> AlertKind.MOTOR_OR_VALVE
            "E4" -> AlertKind.SALT_EMPTY
            "E6" -> AlertKind.WATER_METER
            "E7" -> AlertKind.BRINE
            "EA" -> AlertKind.SALT_LOW
            "EC" -> AlertKind.FLOW_LIMIT
            else -> AlertKind.OTHER
        }
        return Alert(kind, code, fatal = code !in setOf("EA", "EC"))
    }

    fun model(systemType: String?): String = when (systemType) {
        "1" -> "Grünbeck softliQ:SC18"
        "2" -> "Grünbeck softliQ:SC23"
        else -> "Grünbeck softliQ"
    }

    fun state(values: Map<String, String>, model: String, lastError: String?): ApplianceState {
        fun num(key: String) = values[key]?.replace(',', '.')?.toDoubleOrNull()
        val step = step(values[REGENERATION_STEP])
        val readings = buildList {
            num(SALT_RANGE_DAYS)?.let { add(Reading(ReadingKind.SALT_RANGE_DAYS, it)) }
            num(FLOW)?.let { add(Reading(ReadingKind.FLOW_M3_H, it)) }
            num(RAW_HARDNESS)?.let { add(Reading(ReadingKind.RAW_HARDNESS_DH, it)) }
            num(MAINTENANCE_DAYS)?.let { add(Reading(ReadingKind.MAINTENANCE_DAYS, it)) }
        }
        return ApplianceState(
            model = model,
            firmware = values[FIRMWARE]?.ifBlank { null },
            status = when {
                step != null -> ApplianceStatus.REGENERATING
                values[REGENERATION_STEP] == "0" -> ApplianceStatus.IN_SERVICE
                else -> ApplianceStatus.UNKNOWN
            },
            step = step,
            readings = readings,
            alerts = listOfNotNull(alert(lastError)),
            settings = listOf(ApplianceSetting.Options(SettingKey.OPERATING_MODE, values[MODE]?.toIntOrNull(), MODES)),
            actions = listOf(ApplianceAction.REGENERATE),
        )
    }
}

class GruenbeckDriver(private val http: LocalHttp, private val address: ApplianceAddress) : ApplianceDriver {
    override val brand = Brand.GRUENBECK
    private val lock = Mutex()
    private var model: String? = null

    override suspend fun read(publish: (ApplianceState) -> Unit): ApplianceState = lock.withLock { readUnlocked() }

    private suspend fun readUnlocked(): ApplianceState {
        if (model == null) {
            model = GruenbeckProtocol.model(optional(GruenbeckProtocol.SYSTEM_TYPE, "290"))
        }
        val values = mux(GruenbeckProtocol.query(GruenbeckProtocol.STATE_KEYS))
        if (values.isEmpty()) throw IOException("Grünbeck: no values in reply")
        return GruenbeckProtocol.state(values, model!!, optional(GruenbeckProtocol.LAST_ERROR, "245"))
    }

    override suspend fun apply(change: ApplianceChange): ChangeResult = lock.withLock {
        when {
            change is ApplianceChange.SetOption && change.key == SettingKey.OPERATING_MODE -> {
                require(GruenbeckProtocol.MODES.any { it.code == change.code })
                val value = change.code.toString()
                val echo = mux(GruenbeckProtocol.query(listOf(GruenbeckProtocol.MODE), edit = GruenbeckProtocol.MODE to value))
                val state = try { readUnlocked() } catch (_: IOException) { null }
                val mode = (state?.settings?.firstOrNull() as? ApplianceSetting.Options)?.value
                if (echo[GruenbeckProtocol.MODE] == value && (state == null || mode == change.code)) {
                    ChangeResult.Confirmed(state)
                } else {
                    ChangeResult.NotConfirmed(state)
                }
            }
            change is ApplianceChange.Run && change.action == ApplianceAction.REGENERATE -> {
                val echo = mux(
                    GruenbeckProtocol.query(listOf(GruenbeckProtocol.START_REGENERATION), edit = GruenbeckProtocol.START_REGENERATION to "1"),
                )
                val state = try { readUnlocked() } catch (_: IOException) { null }
                when {
                    state?.status == ApplianceStatus.REGENERATING -> ChangeResult.Confirmed(state)
                    echo[GruenbeckProtocol.START_REGENERATION] == "1" -> ChangeResult.Accepted(state)
                    else -> ChangeResult.NotConfirmed(state)
                }
            }
            else -> throw IllegalArgumentException("Grünbeck has no change $change")
        }
    }

    /** Values only some models answer (system type, error memory). */
    private suspend fun optional(key: String, code: String): String? = try {
        mux(GruenbeckProtocol.query(listOf(key), code = code))[key]?.ifBlank { null }
    } catch (_: IOException) {
        null
    }

    private suspend fun mux(body: String): Map<String, String> {
        val response = http.post(address.host, address.port, GruenbeckProtocol.PATH, body, "application/x-www-form-urlencoded")
        if (response.status != 200) throw IOException("Grünbeck HTTP ${response.status}")
        return GruenbeckProtocol.parse(response.body)
    }
}
