package io.github.kriziw.bl3372setup.runxin

import io.github.kriziw.bl3372setup.broadlink.hex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** Frames produced by Danirv/ypsilon-local's own framing/f79d code (tools/golden_device_vectors.py). */
class RunxinFramesTest {
    @Test
    fun `query frames match ypsilon-local`() {
        assertArrayEquals(hex("5a5c1b0000000000000000000100120800dffd0809012210deeaa5"), RunxinFrames.query(listOf(1, 34)))
        assertArrayEquals(
            hex(
                "5a5c4c0000000000000000000100123900dffd39090102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d" +
                    "1e1f202122232425262728292a2b2c2d2e2f303132334cdec4a5",
            ),
            F79d.stateQuery(),
        )
    }

    @Test
    fun `setting write frames match ypsilon-local`() {
        val cases = mapOf(
            SoftenerSetting.Hardness(150) to "5a5c1c0000000000000000000100120900dffd09192f9600c3de52a5",
            SoftenerSetting.Regenerate to "5a5c1c0000000000000000000100120900dffd091922010021de0ea5",
            SoftenerSetting.ControllerClock(LocalTime.of(14, 37)) to "5a5c1c0000000000000000000100120900dffd0919040e2535de36a5",
            SoftenerSetting.FlowShutoff(200) to "5a5c1c0000000000000000000100120900dffd09190700c8cdde66a5",
            SoftenerSetting.SaltAdded(50) to "5a5c1c0000000000000000000100120900dffd09192b32005bde82a5",
        )
        for ((setting, expected) in cases) assertArrayEquals(setting.toString(), hex(expected), F79d.writeFrame(setting))
    }

    @Test
    fun `field encodings verified on hardware by ypsilon-local`() {
        assertEquals(listOf(43, 50, 0), SoftenerSetting.SaltAdded(50).encode())
        assertEquals(listOf(47, 0x90, 0x01), SoftenerSetting.Hardness(400).encode())
        assertEquals(listOf(7, 0x00, 0xC8), SoftenerSetting.FlowShutoff(200).encode())
        assertEquals(listOf(10, 2, 30), SoftenerSetting.RegenerationTime(LocalTime.of(2, 30)).encode())
        assertEquals(listOf(6, 120, 0), SoftenerSetting.ContinuousFlowLimit(120).encode())
    }

    @Test
    fun `settings reject values outside the WaterDevice ranges`() {
        assertThrows(IllegalArgumentException::class.java) { SoftenerSetting.Hardness(49) }
        assertThrows(IllegalArgumentException::class.java) { SoftenerSetting.Hardness(1501) }
        assertThrows(IllegalArgumentException::class.java) { SoftenerSetting.SaltAdded(101) }
        assertThrows(IllegalArgumentException::class.java) { SoftenerSetting.ContinuousFlowLimit(121) }
        assertThrows(IllegalArgumentException::class.java) { SoftenerSetting.FlowShutoff(1001) }
    }

    @Test
    fun `response parsing and decoding match ypsilon-local`() {
        val response = hex("5a5c310000000000000000000100121e00dffd1ec90109000802000b0096220000230001243b022f96000703e8d6dea2a5")
        val state = SoftenerState(RunxinFrames.parseResponse(response))
        // ypsilon-local: deviceModel 9, unit 2, flowRate 150, station 0, residual 159.02, hardness 150, flowRateOff 1000
        assertEquals(9, state.deviceModel)
        assertEquals(VolumeUnit.CUBIC_METRES, state.volumeUnit)
        assertEquals(150, state.flowRateHundredths)
        assertEquals(Station.IN_SERVICE, state.station)
        assertEquals(159.02, state.remainingCapacity!!, 1e-9)
        assertEquals(150, state.hardnessMgPerLitre)
        assertEquals(1000, state.flowShutoffHundredths)
        assertTrue(state.isActive) // water is flowing
    }

    @Test
    fun `volume pairs follow the unit and fail closed`() {
        val pair = mapOf(35 to (0 to 1), 36 to (59 to 2))
        assertNull(SoftenerState(pair).remainingCapacity)
        assertNull(SoftenerState(mapOf(8 to (2 to 0), 35 to (0 to 1))).remainingCapacity)
        for (unit in 0..1) {
            assertEquals((2 or (59 shl 8) or (1 shl 16)).toDouble(), SoftenerState(pair + (8 to (unit to 0))).remainingCapacity!!, 0.0)
        }
    }

    @Test
    fun `vacation status, phase time and close reason`() {
        assertEquals(VacationStatus.OFF, SoftenerState(mapOf(49 to (0 to 0), 34 to (0 to 0))).vacationStatus)
        assertEquals(VacationStatus.PREPARING, SoftenerState(mapOf(49 to (1 to 0), 34 to (3 to 0))).vacationStatus)
        assertEquals(VacationStatus.ACTIVE, SoftenerState(mapOf(49 to (1 to 0), 34 to (8 to 0))).vacationStatus)
        assertEquals(272, SoftenerState(mapOf(34 to (1 to 0), 16 to (4 to 32))).phaseRemainingSeconds)
        assertEquals(CloseReason.LEAK_DETECTED, SoftenerState(mapOf(12 to (0x01 to 0x02))).closeReason)
        assertEquals(true, SoftenerState(mapOf(33 to (1 to 0))).saltReminder)
        assertEquals(false, SoftenerState(mapOf(33 to (1 to 0))).filterReminder)
    }

    @Test
    fun `clock confirmation tolerates the minute ticking over`() {
        val sent = SoftenerSetting.ControllerClock(LocalTime.of(23, 59, 40))
        assertTrue(sent.isConfirmedBy(SoftenerState(mapOf(4 to (23 to 59)))))
        assertTrue(sent.isConfirmedBy(SoftenerState(mapOf(4 to (0 to 0)))))
        assertEquals(false, sent.isConfirmedBy(SoftenerState(mapOf(4 to (0 to 1)))))
    }

    @Test
    fun `malformed frames are rejected`() {
        val good = RunxinFrames.build(RunxinFrames.QUERY_RESPONSE, listOf(1, 9, 0))
        assertEquals(mapOf(1 to (9 to 0)), RunxinFrames.parseResponse(good))
        assertThrows(RunxinProtocolException::class.java) { RunxinFrames.parseResponse(good.copyOf(good.size - 1)) }
        assertThrows(RunxinProtocolException::class.java) {
            RunxinFrames.parseResponse(good.copyOf().also { it[20] = (it[20] + 1).toByte() })
        }
        assertThrows(RunxinProtocolException::class.java) { RunxinFrames.parseResponse(RunxinFrames.query(listOf(1))) }
        // A write acknowledgement with a non-field payload is still a valid frame.
        assertEquals(emptyMap<Int, Pair<Int, Int>>(), RunxinFrames.parseResponse(RunxinFrames.build(RunxinFrames.WRITE_RESPONSE, listOf(0))))
    }
}
