// Field catalogue and codecs ported from Danirv/ypsilon-local `runxin/fields.py`, `runxin/f79d.py`
// and `runxin/semantics.py` (Apache License 2.0); see THIRD_PARTY_NOTICES.md and docs/DEVICE_PROTOCOL.md.
package io.github.kriziw.bl3372setup.runxin

import java.time.LocalTime

/** Valve phase reported in field 34. */
enum class Station(val code: Int) {
    IN_SERVICE(0), BACKWASH(1), BRINE_DRAW(2), BRINE_REFILL(3), FAST_RINSE(4),
    CLOSED(5), SALT_DISSOLVING(6), PAUSE_1(7), PAUSE_2(8);

    companion object {
        fun of(code: Int?) = entries.firstOrNull { it.code == code }
    }
}

enum class VolumeUnit(val code: Int) {
    GALLONS(0), LITRES(1), CUBIC_METRES(2);

    companion object {
        fun of(code: Int?) = entries.firstOrNull { it.code == code }
    }
}

/** Field-12 reasons for a closed valve (legacy WaterDevice UI). */
enum class CloseReason(val code: Int) {
    MANUAL(257), LEAK_DETECTED(513), CONTINUOUS_FLOW_TIMEOUT(769), FLOW_RATE_EXCEEDED(1025);

    companion object {
        fun of(code: Int?) = entries.firstOrNull { it.code == code }
    }
}

enum class VacationStatus { OFF, PREPARING, ACTIVE }

/**
 * A decoded F79D state snapshot. Every accessor returns null when the field was not part of the
 * reply, so a missing value is never shown as a plausible zero.
 */
class SoftenerState(val fields: Map<Int, Pair<Int, Int>>) {
    private fun u8(id: Int) = fields[id]?.first
    private fun bool(id: Int) = fields[id]?.let { it.first != 0 }
    private fun u16le(id: Int) = fields[id]?.let { (a, b) -> a or (b shl 8) }
    private fun u16be(id: Int) = fields[id]?.let { (a, b) -> (a shl 8) or b }
    private fun time(id: Int) = fields[id]?.let { (h, m) -> if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null }
    private fun durationSeconds(id: Int) = fields[id]?.let { (m, s) -> m * 60 + s }

    /** Legacy WaterDevice three-byte volume spread over a field and its continuation. */
    private fun volume(id: Int): Double? {
        val base = fields[id] ?: return null
        val next = fields[id + 1] ?: return null
        val (_, baseHigh) = base
        val (nextLow, nextHigh) = next
        return when (volumeUnit) {
            VolumeUnit.GALLONS, VolumeUnit.LITRES -> (nextHigh or (nextLow shl 8) or (baseHigh shl 16)).toDouble()
            VolumeUnit.CUBIC_METRES -> (nextHigh + nextLow * 100 + baseHigh * 10_000) / 100.0
            null -> null
        }
    }

    val deviceModel get() = u8(1)
    val language get() = u8(2)
    val timeScheme24h get() = u8(3)?.let { it == 1 }
    val controllerTime get() = time(4)
    val washInitiationTime get() = time(5)
    /** SafeHOME continuous-flow limit, minutes (0 = off). */
    val continuousFlowLimitMinutes get() = u8(6)
    /** SafeHOME flow shutoff threshold in hundredths of the flow unit (0 = off). Big-endian. */
    val flowShutoffHundredths get() = u16be(7)
    val volumeUnit get() = VolumeUnit.of(u8(8))
    val workPattern get() = u8(9)
    val regenerationTime get() = time(10)
    /** Instantaneous flow in hundredths of the flow unit. Big-endian. */
    val flowRateHundredths get() = u16be(11)
    val closeReasonCode get() = u16le(12)
    val closeReason get() = CloseReason.of(closeReasonCode)
    val rinsingFrequency get() = u8(13)
    val backwashIntervalCount get() = u8(14)
    val backwashSeconds get() = durationSeconds(15)
    val backwashRemainingSeconds get() = durationSeconds(16)
    val brineSlowRinseSeconds get() = durationSeconds(17)
    val brineSlowRinseRemainingSeconds get() = durationSeconds(18)
    val brineRefillSeconds get() = durationSeconds(19)
    val brineRefillRemainingSeconds get() = durationSeconds(20)
    val fastRinseSeconds get() = durationSeconds(21)
    val fastRinseRemainingSeconds get() = durationSeconds(22)
    val maxRegenerationIntervalDays get() = u8(23)
    val outputRelayMode get() = u8(24)
    val regenerationReminderCount get() = u16le(25)
    /**
     * Resin volume in litres. Model 12 (Euro-Clear Midnight) reports tenths: a 25 L unit reads
     * `FA 00` (250). The reference F79D (model 9) reports whole litres.
     */
    val resinVolumeLitres: Double?
        get() = u8(26)?.let { if (deviceModel == F79d.MIDNIGHT_MODEL) it / 10.0 else it.toDouble() }
    val clockChipFault get() = bool(27)
    val multiplePositionSignalFault get() = bool(28)
    val noPositionSignalFault get() = bool(29)
    val memoryFault get() = bool(30)
    /** Legacy UI meaning: low brine concentration. */
    val lowBrineAlarm get() = bool(31)
    val resinReplacementReminder get() = bool(32)
    val saltReminder get() = fields[33]?.let { it.first != 0 }
    val filterReminder get() = fields[33]?.let { it.second != 0 }
    val stationCode get() = u8(34)
    val station get() = Station.of(stationCode)
    val remainingCapacity get() = volume(35)
    val todayConsumption get() = volume(37)
    val weeklyAverageConsumption get() = volume(39)
    val capacityPerCycle get() = volume(41)
    /** Salt added, as bookkept by the controller (kg). Not a measured salt level. */
    val saltAddedKg get() = u8(43)
    val serviceDays get() = u8(44)
    val remainingDays get() = u8(45)
    /** 0 = by volume (meter), 1 = by time (days). */
    val regenerationByTime get() = u8(46)?.let { it == 1 }
    val hardnessMgPerLitre get() = u16le(47)
    val brineDrawForward get() = u8(48)?.let { it == 1 }
    val vacationFlag get() = bool(49)
    val saltDissolutionRemainingMinutes get() = u8(50)
    val pauseRemainingMinutes get() = u8(51)
    val filterMaterialDays get() = u16le(52)

    val vacationStatus: VacationStatus?
        get() = when (vacationFlag) {
            null -> null
            false -> VacationStatus.OFF
            true -> if (station == Station.PAUSE_2) VacationStatus.ACTIVE else VacationStatus.PREPARING
        }

    /** The valve is moving or in a regeneration phase (worth polling faster). */
    val isActive: Boolean
        get() {
            val s = station ?: return false
            if (s == Station.IN_SERVICE || s == Station.CLOSED) return (flowRateHundredths ?: 0) > 0
            return !(vacationFlag == true && s == Station.PAUSE_2)
        }

    /** Seconds left in the current regeneration phase, when the controller reports it. */
    val phaseRemainingSeconds: Int?
        get() = when (station) {
            Station.BACKWASH -> backwashRemainingSeconds
            Station.BRINE_DRAW -> brineSlowRinseRemainingSeconds
            Station.BRINE_REFILL -> brineRefillRemainingSeconds
            Station.FAST_RINSE -> fastRinseRemainingSeconds
            Station.SALT_DISSOLVING -> saltDissolutionRemainingMinutes?.times(60)
            Station.PAUSE_1 -> pauseRemainingMinutes?.times(60)
            else -> null
        }

    val faults: List<Int>
        get() = listOf(27, 28, 29, 30).filter { bool(it) == true }
}

/**
 * The settings this app may change. Each is hardware-write-verified on a Runxin F79D by
 * Danirv/ypsilon-local (write, then independent read-back); ranges come from the WaterDevice UI.
 */
sealed class SoftenerSetting(val fieldId: Int) {
    abstract fun encode(): List<Int>
    abstract fun isConfirmedBy(state: SoftenerState): Boolean

    data class Hardness(val mgPerLitre: Int) : SoftenerSetting(47) {
        init { require(mgPerLitre in RANGE) }
        override fun encode() = listOf(fieldId, mgPerLitre and 0xFF, mgPerLitre shr 8)
        override fun isConfirmedBy(state: SoftenerState) = state.hardnessMgPerLitre == mgPerLitre
        companion object { val RANGE = 50..1500 }
    }

    data class SaltAdded(val kg: Int) : SoftenerSetting(43) {
        init { require(kg in RANGE) }
        override fun encode() = listOf(fieldId, kg, 0)
        override fun isConfirmedBy(state: SoftenerState) = state.saltAddedKg == kg
        companion object { val RANGE = 0..100 }
    }

    data class RegenerationTime(val time: LocalTime) : SoftenerSetting(10) {
        override fun encode() = listOf(fieldId, time.hour, time.minute)
        override fun isConfirmedBy(state: SoftenerState) =
            state.regenerationTime?.let { it.hour == time.hour && it.minute == time.minute } == true
    }

    data class ControllerClock(val time: LocalTime) : SoftenerSetting(4) {
        override fun encode() = listOf(fieldId, time.hour, time.minute)
        /** The controller keeps counting, so the minute may already have moved on during read-back. */
        override fun isConfirmedBy(state: SoftenerState): Boolean {
            val read = state.controllerTime ?: return false
            val sent = time.withSecond(0).withNano(0)
            return read == sent || read == sent.plusMinutes(1)
        }
    }

    data class ContinuousFlowLimit(val minutes: Int) : SoftenerSetting(6) {
        init { require(minutes in RANGE) }
        override fun encode() = listOf(fieldId, minutes, 0)
        override fun isConfirmedBy(state: SoftenerState) = state.continuousFlowLimitMinutes == minutes
        companion object { val RANGE = 0..120 }
    }

    /** Hundredths of m³/h; only offered when the controller reports cubic metres (unit 2). */
    data class FlowShutoff(val hundredths: Int) : SoftenerSetting(7) {
        init { require(hundredths in RANGE) }
        override fun encode() = listOf(fieldId, hundredths shr 8, hundredths and 0xFF) // big-endian
        override fun isConfirmedBy(state: SoftenerState) = state.flowShutoffHundredths == hundredths
        companion object { val RANGE = 0..1000 }
    }

    /**
     * Forced regeneration: field 34 (system mode) = 1, exactly as the WaterDevice app's button.
     * Confirmed once the valve leaves service; it is a mechanical action, never resent.
     */
    data object Regenerate : SoftenerSetting(34) {
        override fun encode() = listOf(fieldId, 1, 0)
        override fun isConfirmedBy(state: SoftenerState) =
            state.station.let { it != null && it != Station.IN_SERVICE && it != Station.CLOSED }
    }

    /**
     * Vacation mode: field 49 = 1 to start, 0 to end, as in the legacy WaterDevice UI. Starting runs
     * brine refill, a 240 min salt-dissolving pause, a shortened brine draw, then pause 2 until
     * ended (the controller's own "hold ▼ for 6 s"). ypsilon-local found that the Ypsilon G6
     * (model 9) acknowledges this write without applying it, so it is only offered as an
     * experimental control, and confirmed solely by the controller's own flag on read-back.
     */
    data class Vacation(val on: Boolean) : SoftenerSetting(49) {
        override fun encode() = listOf(fieldId, if (on) 1 else 0, 0)
        override fun isConfirmedBy(state: SoftenerState) = state.vacationFlag == on
    }

    /** Settings that move the valve; read back more patiently because the motor is running. */
    val isMechanical: Boolean get() = this is Regenerate || this is Vacation
}

object F79d {
    /** deviceModel reported by the F79D profile that ypsilon-local verified (Ypsilon G6). */
    const val VERIFIED_MODEL = 9

    /** deviceModel reported by the Euro-Clear Midnight (ECOPRO+ head), read on real hardware. */
    const val MIDNIGHT_MODEL = 12

    /** The normal state block. Field 52 is slow-changing and read separately. */
    val STATE_FIELDS = (1..51).toList()

    /** Field names from the catalogue, for the raw diagnostics table. */
    val FIELD_NAMES: Map<Int, String> = mapOf(
        1 to "deviceModel", 2 to "language", 3 to "deviceTimeScheme", 4 to "currentTime",
        5 to "washInitiationTime", 6 to "continuousWaterTime", 7 to "flowRateOff", 8 to "waterVolumeUnit",
        9 to "workPattern", 10 to "regeneratingTriggerTime", 11 to "flowRate", 12 to "systemCloseReason",
        13 to "washingIncreaseNumber", 14 to "backWashIntervalNumber", 15 to "backWashTime",
        16 to "backWashTimeRemaining", 17 to "absorbSaltSlowWashTime", 18 to "absorbSaltTimeRemaining",
        19 to "saltTankRefillTime", 20 to "saltTankRefillTimeRemaining", 21 to "washTime",
        22 to "washCountdownTime", 23 to "maximumRegenerationIntervalDay", 24 to "outRelayMode",
        25 to "regenerationAlarmNumber", 26 to "resinVolume", 27 to "clockChipFault",
        28 to "multiplePositionSignalFault", 29 to "noPositionSignalFault", 30 to "memoryErrorFault",
        31 to "saltShortageAlarm", 32 to "resinReplacementReminder", 33 to "reminderFlags", 34 to "station",
        35 to "residualWaterProduction", 36 to "(continuation 35)", 37 to "dailyWaterConsumption",
        38 to "(continuation 37)", 39 to "averageWeeklyWaterConsumption", 40 to "(continuation 39)",
        41 to "periodicWaterProduction", 42 to "(continuation 41)", 43 to "saltAddition", 44 to "operationDay",
        45 to "remainingDay", 46 to "regenerationPattern", 47 to "rawWaterHardness", 48 to "absorbSaltMode",
        49 to "vacationPattern", 50 to "saltDissolutionRemainingTime", 51 to "pauseRemainingTime",
        52 to "filterMaterialWorkingDay",
    )

    fun stateQuery(): ByteArray = RunxinFrames.query(STATE_FIELDS)
    fun field52Query(): ByteArray = RunxinFrames.query(listOf(52))
    fun writeFrame(setting: SoftenerSetting): ByteArray = RunxinFrames.write(setting.encode())
}
