package io.github.kriziw.bl3372setup.appliance.judo

import io.github.kriziw.bl3372setup.appliance.ApplianceAction
import io.github.kriziw.bl3372setup.appliance.ApplianceAddress
import io.github.kriziw.bl3372setup.appliance.ApplianceChange
import io.github.kriziw.bl3372setup.appliance.ApplianceDriver
import io.github.kriziw.bl3372setup.appliance.ApplianceSetting
import io.github.kriziw.bl3372setup.appliance.ApplianceState
import io.github.kriziw.bl3372setup.appliance.Brand
import io.github.kriziw.bl3372setup.appliance.ChangeResult
import io.github.kriziw.bl3372setup.appliance.Reading
import io.github.kriziw.bl3372setup.appliance.ReadingKind
import io.github.kriziw.bl3372setup.appliance.SettingKey
import io.github.kriziw.bl3372setup.network.LocalHttp
import io.github.kriziw.bl3372setup.network.LoginRejectedException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.IOException
import kotlin.math.roundToInt

/**
 * JUDO softeners through the JUDO Connectivity Module's local REST API, as published by JUDO
 * ("API-Kommandozeilen", judo.eu, 2024-11): `GET /api/rest/<command><00><data>` with HTTP basic
 * auth, answering `{"data":"<hex>"}`. Multi-byte values are little-endian.
 */
object JudoProtocol {
    enum class Family { I_SOFT, I_SOFT_SAFE, I_SOFT_PRO, SOFTWELL }

    /** [valve]: the model has the leak-protection shut-off valve that 3C/3D close and open. */
    data class Model(val name: String, val family: Family, val valve: Boolean)

    /** Device types from the official tables. Other JUDO products are not softeners. */
    val MODELS: Map<Int, Model> = buildMap {
        listOf(0x32, 0x53).forEach { put(it, Model("i-soft", Family.I_SOFT, valve = false)) }
        listOf(0x43, 0x54).forEach { put(it, Model("i-soft K", Family.I_SOFT, valve = false)) }
        listOf(0x33, 0x57).forEach { put(it, Model("i-soft SAFE+", Family.I_SOFT_SAFE, valve = true)) }
        listOf(0x42, 0x67).forEach { put(it, Model("i-soft K SAFE+", Family.I_SOFT_SAFE, valve = true)) }
        put(0x58, Model("i-soft PRO", Family.I_SOFT_PRO, valve = false))
        put(0x4B, Model("i-soft PRO", Family.I_SOFT_PRO, valve = true))
        put(0x4C, Model("i-soft PRO L", Family.I_SOFT_PRO, valve = false))
        listOf(0x34, 0x59).forEach { put(it, Model("SOFTwell P", Family.SOFTWELL, valve = false)) }
        listOf(0x35, 0x63).forEach { put(it, Model("SOFTwell S", Family.SOFTWELL, valve = false)) }
        listOf(0x36, 0x5A).forEach { put(it, Model("SOFTwell K", Family.SOFTWELL, valve = false)) }
        listOf(0x47, 0x62).forEach { put(it, Model("SOFTwell KP", Family.SOFTWELL, valve = false)) }
        listOf(0x48, 0x64).forEach { put(it, Model("SOFTwell KS", Family.SOFTWELL, valve = false)) }
    }

    const val DEVICE_TYPE = 0xFF
    const val FIRMWARE = 0x01
    const val HARDNESS_UNIT = 0x23
    const val TARGET_HARDNESS_READ = 0x51
    const val TARGET_HARDNESS_WRITE = 0x30
    const val SALT = 0x56
    const val SALT_WARNING = 0x57
    const val TOTAL_WATER = 0x28
    const val SOFT_WATER = 0x29
    const val REGENERATE = 0x35
    const val CLOSE_VALVE = 0x3C
    const val OPEN_VALVE = 0x3D

    /** Hardness units of command 23: 0 °dH, 1 °eH, 2 °fH, 3 gpg, 4 ppm, 5 mmol/l, 6 mval/l. */
    val HARDNESS_UNITS = listOf("°dH", "°eH", "°fH", "gpg", "ppm", "mmol/l", "mval/l")

    /** `5100` reads command 51; `300007` writes 7 to command 30. */
    fun path(command: Int, data: ByteArray = ByteArray(0)): String =
        "/api/rest/" + "%02X".format(command) + "00" + data.joinToString("") { "%02X".format(it) }

    fun le(value: Int, bytes: Int): ByteArray = ByteArray(bytes) { ((value shr (8 * it)) and 0xFF).toByte() }

    fun hex(data: String): ByteArray {
        val clean = data.trim()
        require(clean.length % 2 == 0) { "odd hex length" }
        return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    /** Little-endian unsigned integer of [length] bytes at [offset], or null if the reply is short. */
    fun leInt(bytes: ByteArray, offset: Int, length: Int): Long? {
        if (bytes.size < offset + length) return null
        var value = 0L
        for (i in length - 1 downTo 0) value = (value shl 8) or (bytes[offset + i].toLong() and 0xFF)
        return value
    }

    /** `6b1502` -> 2.21k; `0C0001` -> 1.0.12 (both examples from the official tables). */
    fun firmware(bytes: ByteArray): String? {
        if (bytes.size < 3) return null
        val (b0, b1, b2) = Triple(bytes[2].toInt() and 0xFF, bytes[1].toInt() and 0xFF, bytes[0].toInt() and 0xFF)
        return if (b2.toChar() in 'a'..'z') "%d.%02d%c".format(b0, b1, b2.toChar()) else "$b0.$b1.$b2"
    }
}

class JudoDriver(
    private val http: LocalHttp,
    private val address: ApplianceAddress,
    /** The module ignores requests that come faster than about every 10 s (firmware 2023+). */
    private val minGapMillis: Long = 11_000,
    private val now: () -> Long = System::currentTimeMillis,
) : ApplianceDriver {
    override val brand = Brand.JUDO
    override val pollIntervalMillis: Long = 60_000

    private val lock = Mutex()
    private var lastRequestAt = 0L
    private var typeCode: Int? = null
    private var firmware: String? = null
    private var unit: Int? = null
    private var warningDays: Int? = null
    private var infoRead = false
    /** Partial states are only published while the first read is in progress. */
    private var readOnce = false

    private val model get() = typeCode?.let { JudoProtocol.MODELS[it] }

    override suspend fun read(publish: (ApplianceState) -> Unit): ApplianceState = lock.withLock {
        if (typeCode == null) {
            val type = request(JudoProtocol.DEVICE_TYPE)
            typeCode = JudoProtocol.leInt(type, 0, 1)?.toInt() ?: throw IOException("JUDO: empty device type")
            if (model == null) throw IOException("JUDO device type 0x%02X is not a softener".format(typeCode))
            if (!readOnce) publish(snapshot(complete = false))
        }
        val family = model!!.family
        if (!infoRead) {
            infoRead = true
            firmware = optional(JudoProtocol.FIRMWARE)?.let(JudoProtocol::firmware)
            if (family != JudoProtocol.Family.SOFTWELL) {
                unit = optional(JudoProtocol.HARDNESS_UNIT)?.let { JudoProtocol.leInt(it, 0, 1)?.toInt() }
                warningDays = optional(JudoProtocol.SALT_WARNING)?.let { JudoProtocol.leInt(it, 0, 1)?.toInt() }
            }
            if (!readOnce) publish(snapshot(complete = false))
        }
        val registers = if (family == JudoProtocol.Family.SOFTWELL) {
            listOf(JudoProtocol.SOFT_WATER)
        } else {
            listOf(JudoProtocol.SALT, JudoProtocol.TARGET_HARDNESS_READ, JudoProtocol.SOFT_WATER, JudoProtocol.TOTAL_WATER)
        }
        registers.forEachIndexed { index, register ->
            optional(register)?.let { store(register, it) }
            if (index < registers.lastIndex) if (!readOnce) publish(snapshot(complete = false))
        }
        readOnce = true
        snapshot(complete = true)
    }

    override suspend fun apply(change: ApplianceChange): ChangeResult = lock.withLock {
        val m = model ?: throw IOException("JUDO: read the device first")
        when (change) {
            is ApplianceChange.SetNumber -> when (change.key) {
                SettingKey.TARGET_HARDNESS -> {
                    val dh = change.value.roundToInt()
                    write(JudoProtocol.TARGET_HARDNESS_WRITE, byteArrayOf(dh.toByte()))
                    readBack { store(JudoProtocol.TARGET_HARDNESS_READ, request(JudoProtocol.TARGET_HARDNESS_READ)); hardness == dh }
                }
                SettingKey.SALT_STOCK -> {
                    val grams = (change.value * 1000).roundToInt()
                    write(JudoProtocol.SALT, JudoProtocol.le(grams, 2))
                    readBack { store(JudoProtocol.SALT, request(JudoProtocol.SALT)); saltGrams == grams }
                }
                SettingKey.SALT_WARNING_DAYS -> {
                    val days = change.value.roundToInt()
                    write(JudoProtocol.SALT_WARNING, byteArrayOf(days.toByte()))
                    readBack {
                        warningDays = JudoProtocol.leInt(request(JudoProtocol.SALT_WARNING), 0, 1)?.toInt()
                        warningDays == days
                    }
                }
                else -> throw IllegalArgumentException("JUDO has no setting ${change.key}")
            }
            is ApplianceChange.Run -> {
                when (change.action) {
                    ApplianceAction.REGENERATE -> send(JudoProtocol.REGENERATE, byteArrayOf(0))
                    ApplianceAction.CLOSE_VALVE -> { require(m.valve); send(JudoProtocol.CLOSE_VALVE) }
                    ApplianceAction.OPEN_VALVE -> { require(m.valve); send(JudoProtocol.OPEN_VALVE) }
                }
                // The API has no read for regeneration or valve state.
                ChangeResult.Accepted(snapshot(complete = true))
            }
            else -> throw IllegalArgumentException("JUDO has no change $change")
        }
    }

    /** The write was sent; a failed read-back leaves it unconfirmed rather than failed. */
    private suspend fun readBack(check: suspend () -> Boolean): ChangeResult {
        val confirmed = try {
            check()
        } catch (_: IOException) {
            false
        }
        val state = snapshot(complete = true)
        return if (confirmed) ChangeResult.Confirmed(state) else ChangeResult.NotConfirmed(state)
    }

    private var hardness: Int? = null
    private var saltGrams: Int? = null
    private var saltDays: Int? = null
    private var softLitres: Long? = null
    private var totalLitres: Long? = null

    private fun store(register: Int, bytes: ByteArray) {
        when (register) {
            JudoProtocol.TARGET_HARDNESS_READ -> hardness = JudoProtocol.leInt(bytes, 0, 2)?.toInt()
            JudoProtocol.SALT -> {
                saltGrams = JudoProtocol.leInt(bytes, 0, 2)?.toInt()
                saltDays = JudoProtocol.leInt(bytes, 2, 2)?.toInt()
            }
            JudoProtocol.SOFT_WATER -> softLitres = JudoProtocol.leInt(bytes, 0, 4)
            JudoProtocol.TOTAL_WATER -> totalLitres = JudoProtocol.leInt(bytes, 0, 4)
        }
    }

    private fun snapshot(complete: Boolean): ApplianceState {
        val m = model
        val dh = unit == 0
        val softener = m != null && m.family != JudoProtocol.Family.SOFTWELL
        val readings = buildList {
            saltDays?.let { add(Reading(ReadingKind.SALT_RANGE_DAYS, it.toDouble())) }
            saltGrams?.let { add(Reading(ReadingKind.SALT_STOCK_KG, it / 1000.0)) }
            hardness?.let {
                add(Reading(ReadingKind.TARGET_HARDNESS, it.toDouble(), unit?.let { u -> JudoProtocol.HARDNESS_UNITS.getOrNull(u) }))
            }
            softLitres?.let { add(Reading(ReadingKind.SOFT_WATER_TOTAL_M3, it / 1000.0)) }
            totalLitres?.let { add(Reading(ReadingKind.WATER_TOTAL_M3, it / 1000.0)) }
        }
        val settings = if (!softener) emptyList() else buildList {
            // Target hardness is only offered in °dH, the unit the 1-byte write and the read share.
            if (dh) add(ApplianceSetting.Number(SettingKey.TARGET_HARDNESS, hardness?.toDouble(), 1.0, 18.0, 1.0, "°dH"))
            add(ApplianceSetting.Number(SettingKey.SALT_STOCK, saltGrams?.div(1000.0), 0.0, 50.0, 0.5, "kg"))
            add(ApplianceSetting.Number(SettingKey.SALT_WARNING_DAYS, warningDays?.toDouble(), 1.0, 90.0, 1.0, "d"))
        }
        val actions = buildList {
            if (softener) add(ApplianceAction.REGENERATE)
            if (m?.valve == true) {
                add(ApplianceAction.CLOSE_VALVE)
                add(ApplianceAction.OPEN_VALVE)
            }
        }
        return ApplianceState(
            model = m?.let { "JUDO ${it.name}" },
            modelCode = typeCode,
            firmware = firmware,
            readings = readings,
            settings = settings,
            actions = actions,
            complete = complete,
        )
    }

    /** Reads that some models lack: a failure leaves the value empty. */
    private suspend fun optional(command: Int): ByteArray? = try {
        request(command)
    } catch (e: LoginRejectedException) {
        throw e
    } catch (_: IOException) {
        null
    }

    /** A read: the reply must carry data. An empty reply is how the module answers when asked too often. */
    private suspend fun request(command: Int): ByteArray =
        exchange(command, ByteArray(0)).also { if (it.isEmpty()) throw IOException("JUDO: empty reply (rate limit?)") }

    /** An action, sent once; failure is reported because nothing can be read back. */
    private suspend fun send(command: Int, data: ByteArray = ByteArray(0)) {
        exchange(command, data)
    }

    /**
     * A setting write, sent once. A lost reply is ambiguous (the module may have applied it), so it
     * is not retried; the read-back that follows decides.
     */
    private suspend fun write(command: Int, data: ByteArray) {
        try {
            exchange(command, data)
        } catch (e: LoginRejectedException) {
            throw e
        } catch (_: IOException) {
        }
    }

    private suspend fun exchange(command: Int, data: ByteArray): ByteArray {
        val wait = lastRequestAt + minGapMillis - now()
        if (wait > 0) delay(wait)
        try {
            val auth = LocalHttp.BasicAuth(address.user ?: DEFAULT_USER, address.secret ?: DEFAULT_PASSWORD)
            val response = http.get(address.host, address.port, JudoProtocol.path(command, data), auth)
            if (response.status == 401 || response.status == 403) throw LoginRejectedException("JUDO login rejected")
            if (response.status != 200) throw IOException("JUDO HTTP ${response.status}")
            val hex = try {
                JSONObject(response.body).optString("data", "")
            } catch (e: org.json.JSONException) {
                throw IOException("JUDO: unexpected reply", e)
            }
            return JudoProtocol.hex(hex)
        } finally {
            lastRequestAt = now()
        }
    }

    companion object {
        const val DEFAULT_USER = "admin"
        const val DEFAULT_PASSWORD = "Connectivity"
    }
}
