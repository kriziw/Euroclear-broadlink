package io.github.kriziw.bl3372setup.runxin

import io.github.kriziw.bl3372setup.broadlink.BroadlinkException
import io.github.kriziw.bl3372setup.broadlink.BroadlinkSession
import io.github.kriziw.bl3372setup.broadlink.loopbackSockets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

/** The whole device path (session, TFB transport, frames, write verification) against [FakeController]. */
class SoftenerClientTest {
    private val baseline = mapOf(
        1 to (9 to 0), 8 to (2 to 0), 34 to (0 to 0), 47 to (0x78 to 0x00), 43 to (20 to 0),
        35 to (0 to 1), 36 to (59 to 2), 49 to (0 to 0), 52 to (90 to 0),
    )

    private fun client(device: FakeController, timeoutMillis: Long = 2_000) = SoftenerClient(
        Bl3372Transport(BroadlinkSession(loopbackSockets, device.endpoint, timeoutMillis = timeoutMillis, retryIntervalMillis = 200)),
        settingTimeoutMillis = 1_000, settingIntervalMillis = 100,
        mechanicalTimeoutMillis = 1_000, mechanicalIntervalMillis = 100,
    )

    @Test
    fun `authenticates and reads the decoded state`() = runBlocking {
        FakeController(baseline).use { device ->
            val c = client(device)
            val state = c.readState()
            assertEquals(listOf(1), device.queriedFields.first())
            assertEquals(ControllerProfiles.F79D, c.profile)
            assertEquals(ControllerProfiles.F79D.stateFields, device.queriedFields[1])
            assertEquals(1, device.authCount.get())
            assertEquals(9, state.deviceModel)
            assertEquals(120, state.hardnessMgPerLitre)
            assertEquals(159.02, state.remainingCapacity!!, 1e-9)
            assertEquals(90, state.filterMaterialDays) // field 52 read separately
        }
    }

    @Test
    fun `unknown controller has fallback readings without a registered profile`() = runBlocking {
        FakeController(baseline + (1 to (136 to 0))).use { device ->
            val c = client(device)
            assertEquals(136, c.readState().deviceModel)
            assertNull(c.profile) // Product name F136 is not a protocol-code mapping.
            assertEquals(listOf(1), device.queriedFields.first())
        }
    }

    @Test
    fun `changed controller identity invalidates the loaded profile and optional cache`() = runBlocking {
        FakeController(baseline).use { device ->
            val c = client(device)
            c.readState()
            device.fields[1] = 105 to 0
            device.fields[52] = 180 to 0
            val state = c.readState()
            assertNull(c.profile)
            assertEquals(105, state.deviceModel)
            assertEquals(180, state.filterMaterialDays)
        }
    }

    @Test
    fun `a setting is confirmed by read-back`() = runBlocking {
        FakeController(baseline).use { device ->
            val result = client(device).write(SoftenerSetting.Hardness(150))
            assertTrue(result is WriteResult.Confirmed)
            assertEquals(150, (result as WriteResult.Confirmed).state.hardnessMgPerLitre)
            assertEquals(1, device.writesReceived.get())
        }
    }

    @Test
    fun `an acknowledged write the controller ignores is reported as not confirmed`() = runBlocking {
        FakeController(baseline).use { device ->
            device.ignoreWrites = true
            val result = client(device).write(SoftenerSetting.SaltAdded(50))
            assertEquals(WriteResult.NotConfirmed::class, result::class)
            result as WriteResult.NotConfirmed
            assertEquals(false, result.ambiguousDelivery)
            assertEquals(20, result.lastState?.saltAddedKg)
            assertEquals(1, device.writesReceived.get()) // never resent
        }
    }

    @Test
    fun `a write whose reply is lost is sent once and reconciled by reading`() = runBlocking {
        FakeController(baseline).use { device ->
            device.dropWriteReplies = true
            val result = client(device, timeoutMillis = 500).write(SoftenerSetting.RegenerationTime(LocalTime.of(2, 30)))
            assertTrue(result is WriteResult.Confirmed)
            assertEquals(1, device.writesReceived.get())
        }
    }

    @Test
    fun `forced regeneration is confirmed when the valve leaves service`() = runBlocking {
        FakeController(baseline).use { device ->
            val result = client(device).write(SoftenerSetting.Regenerate)
            assertTrue(result is WriteResult.Confirmed)
            assertEquals(Station.BACKWASH, (result as WriteResult.Confirmed).state.station)
        }
    }

    @Test
    fun `forced regeneration that does not move the valve is not confirmed`() = runBlocking {
        FakeController(baseline).use { device ->
            device.onRegenerate = emptyMap()
            val result = client(device).write(SoftenerSetting.Regenerate)
            assertTrue(result is WriteResult.NotConfirmed)
            assertEquals(1, device.writesReceived.get())
        }
    }

    @Test
    fun `vacation mode writes field 49 and is confirmed by the controller's own flag`() = runBlocking {
        assertEquals(listOf(49, 1, 0), SoftenerSetting.Vacation(true).encode())
        assertEquals(listOf(49, 0, 0), SoftenerSetting.Vacation(false).encode())
        FakeController(baseline + (1 to (12 to 0))).use { device ->
            val result = client(device).write(SoftenerSetting.Vacation(true))
            assertTrue(result is WriteResult.Confirmed)
            assertEquals(true, (result as WriteResult.Confirmed).state.vacationFlag)
            assertEquals(1, device.writesReceived.get())
        }
    }

    @Test
    fun `a vacation write the controller acknowledges but ignores is not confirmed`() = runBlocking {
        // What ypsilon-local observed on the Ypsilon G6: ACK, but the flag stays false.
        FakeController(baseline).use { device ->
            device.ignoreWrites = true
            val result = client(device).write(SoftenerSetting.Vacation(true))
            assertTrue(result is WriteResult.NotConfirmed)
            assertEquals(false, (result as WriteResult.NotConfirmed).lastState?.vacationFlag)
            assertEquals(1, device.writesReceived.get()) // never resent
        }
    }

    @Test
    fun `resin volume is in tenths of a litre on the Midnight (model 12)`() {
        assertEquals(25.0, SoftenerState(mapOf(1 to (12 to 0), 26 to (250 to 0))).resinVolumeLitres!!, 0.0)
        assertEquals(40.0, SoftenerState(mapOf(1 to (9 to 0), 26 to (40 to 0))).resinVolumeLitres!!, 0.0)
    }

    @Test
    fun `transient -5 replies are retried for reads`() = runBlocking {
        FakeController(baseline).use { device ->
            val c = client(device)
            c.readState()
            device.failNextCommandsWith = BroadlinkException.TRANSIENT to 2
            assertEquals(9, c.readState().deviceModel)
        }
    }

    @Test
    fun `an expired key triggers one re-authentication`() = runBlocking {
        FakeController(baseline).use { device ->
            val c = client(device)
            c.readState()
            device.failNextCommandsWith = BroadlinkException.KEY_EXPIRED to 1
            assertEquals(9, c.readState().deviceModel)
            assertEquals(2, device.authCount.get())
        }
    }

    @Test(expected = BroadlinkException::class)
    fun `persistent authentication failure is reported`(): Unit = runBlocking {
        FakeController(baseline).use { device ->
            val c = client(device)
            c.readState()
            device.failNextCommandsWith = BroadlinkException.AUTH_FAILED to 10
            c.readState()
        }
    }
}
