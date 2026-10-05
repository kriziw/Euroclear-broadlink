// Transport policy and write reconciliation follow Danirv/ypsilon-local
// `transport/broadlink_bl3372.py` and `coordinator.py` (Apache License 2.0); see THIRD_PARTY_NOTICES.md.
package io.github.kriziw.bl3372setup.runxin

import io.github.kriziw.bl3372setup.broadlink.BroadlinkException
import io.github.kriziw.bl3372setup.broadlink.BroadlinkSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import kotlin.random.Random

/**
 * Carries raw Runxin frames through BroadLink command 0x6A, wrapped in the BL3372 "TFB" envelope
 * (a two-byte little-endian length prefix).
 *
 * Reads may be retried: one re-authentication for -1/-7, and short jittered retries for -5, which
 * is empirically transient on the BL3372. Writes are sent once and never resent automatically.
 */
class Bl3372Transport(
    private val session: BroadlinkSession,
    private val transientDelaysMillis: List<Long> = listOf(400, 800),
) {
    suspend fun read(frame: ByteArray): ByteArray {
        var reauthLeft = 1
        var transientAttempt = 0
        while (true) {
            try {
                return once(frame, retransmit = true)
            } catch (e: BroadlinkException) {
                when {
                    e.code in AUTH_ERRORS && reauthLeft > 0 -> {
                        reauthLeft--
                        session.invalidate()
                    }
                    e.code == BroadlinkException.TRANSIENT && transientAttempt < transientDelaysMillis.size -> {
                        val base = transientDelaysMillis[transientAttempt++]
                        delay(base + Random.nextLong(0, base / 2 + 1))
                    }
                    else -> {
                        session.invalidate()
                        throw e
                    }
                }
            } catch (e: IOException) {
                session.invalidate()
                throw e
            }
        }
    }

    /** Drops the session, e.g. after a malformed product frame that may mean stale session state. */
    fun invalidate() = session.invalidate()

    /** Sends a write exactly once. A failure here is ambiguous: the controller may have acted. */
    suspend fun writeOnce(frame: ByteArray): ByteArray = try {
        once(frame, retransmit = false)
    } catch (e: IOException) {
        session.invalidate()
        throw e
    }

    private suspend fun once(frame: ByteArray, retransmit: Boolean): ByteArray {
        if (!session.isAuthenticated) session.authenticate()
        val payload = ByteArray(frame.size + 2)
        payload[0] = frame.size.toByte()
        payload[1] = (frame.size shr 8).toByte()
        frame.copyInto(payload, 2)
        return unpackTfb(session.command(payload, retransmit))
    }

    companion object {
        private val AUTH_ERRORS = setOf(BroadlinkException.AUTH_FAILED, BroadlinkException.KEY_EXPIRED)

        /** Removes the length prefix; anything after the declared length is AES block padding. */
        fun unpackTfb(plaintext: ByteArray): ByteArray {
            if (plaintext.size < 2) throw RunxinProtocolException("missing BL3372 length prefix")
            val declared = (plaintext[0].toInt() and 0xFF) or ((plaintext[1].toInt() and 0xFF) shl 8)
            if (declared > plaintext.size - 2) throw RunxinProtocolException("BL3372 length exceeds payload")
            return plaintext.copyOfRange(2, 2 + declared)
        }
    }
}

sealed interface WriteResult {
    data class Confirmed(val state: SoftenerState) : WriteResult

    /**
     * The controller did not report the requested value in time. [ambiguousDelivery] means the
     * write itself got no reply, so it may or may not have reached the controller.
     */
    data class NotConfirmed(val lastState: SoftenerState?, val ambiguousDelivery: Boolean) : WriteResult
}

/** Reads and writes a Runxin F79D-family controller through any [Bl3372Transport]. */
class SoftenerClient(
    private val transport: Bl3372Transport,
    private val settingTimeoutMillis: Long = 5_000,
    private val settingIntervalMillis: Long = 500,
    private val mechanicalTimeoutMillis: Long = 15_000,
    private val mechanicalIntervalMillis: Long = 1_000,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val moduleType: Int = io.github.kriziw.bl3372setup.broadlink.BroadlinkPackets.DEVTYPE_RUNXIN_BL3372,
) {
    private val lock = Mutex()
    private var field52: Pair<Int, Int>? = null
    private var identified = false
    private var modelCode: Int? = null
    var profile: ControllerProfile? = null
        private set

    /** Reads fields 1..51 (and field 52 once) and returns the decoded state. */
    suspend fun readState(): SoftenerState = lock.withLock { readUnlocked() }

    /**
     * Writes [setting] once, then reads the controller back until it reports the requested value.
     * Success is only ever reported from fresh read-back, never from the write acknowledgement.
     */
    suspend fun write(setting: SoftenerSetting): WriteResult = lock.withLock {
        val mechanical = setting.isMechanical
        val timeout = if (mechanical) mechanicalTimeoutMillis else settingTimeoutMillis
        val interval = if (mechanical) mechanicalIntervalMillis else settingIntervalMillis

        val ambiguous = try {
            transport.writeOnce(F79d.writeFrame(setting))
            false
        } catch (_: IOException) {
            true
        }

        val deadline = now() + timeout
        var last: SoftenerState? = null
        while (true) {
            try {
                last = readUnlocked()
                if (setting.isConfirmedBy(last)) return@withLock WriteResult.Confirmed(last)
            } catch (_: IOException) {
                // Keep reconciling until the deadline; the last good read is reported.
            }
            if (now() >= deadline) return@withLock WriteResult.NotConfirmed(last, ambiguous)
            delay(interval)
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    private suspend fun readUnlocked(): SoftenerState {
        if (!identified) {
            modelCode = query(RunxinFrames.query(listOf(1)))[1]?.first
            profile = ControllerProfiles.resolve(moduleType, modelCode)
            identified = true
        }
        // Unknown identities use the existing F79D decoder as an explicitly experimental fallback.
        val fields = query(RunxinFrames.query(profile?.stateFields ?: F79d.STATE_FIELDS)).toMutableMap()
        val reportedModel = fields[1]?.first
        if (reportedModel != modelCode) {
            modelCode = reportedModel
            profile = ControllerProfiles.resolve(moduleType, modelCode)
            field52 = null
        }
        if (field52 == null && (profile?.optionalFields ?: listOf(52)).contains(52)) {
            field52 = try {
                query(F79d.field52Query())[52]
            } catch (_: IOException) {
                null // Optional, slow-changing field; the main state is still valid.
            }
        }
        field52?.let { fields.putIfAbsent(52, it) }
        return SoftenerState(fields)
    }

    private suspend fun query(frame: ByteArray): Map<Int, Pair<Int, Int>> = try {
        RunxinFrames.parseResponse(transport.read(frame))
    } catch (e: RunxinProtocolException) {
        transport.invalidate()
        throw e
    }
}
