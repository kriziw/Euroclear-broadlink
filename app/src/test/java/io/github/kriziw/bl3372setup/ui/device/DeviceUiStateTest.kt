package io.github.kriziw.bl3372setup.ui.device

import io.github.kriziw.bl3372setup.broadlink.BroadlinkPackets
import io.github.kriziw.bl3372setup.devices.SavedDevice
import io.github.kriziw.bl3372setup.runxin.SoftenerState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the dashboard offers vacation mode and regeneration. */
class DeviceUiStateTest {
    private val midnight = SavedDevice(
        mac = "AA:BB:CC:DD:EE:FF",
        name = "Softener",
        lastIp = "192.0.2.10",
        deviceType = BroadlinkPackets.DEVTYPE_RUNXIN_BL3372,
        controlsUnlocked = true,
        unlockedModelCode = 12,
    )

    private fun ui(model: Int, station: Int, vacation: Boolean, device: SavedDevice = midnight) = DeviceUiState(
        device = device,
        connection = Connection.Live,
        state = SoftenerState(mapOf(1 to (model to 0), 34 to (station to 0), 49 to ((if (vacation) 1 else 0) to 0))),
    )

    @Test
    fun `vacation starts only from service on an unlocked experimental controller`() {
        val inService = ui(model = 12, station = 0, vacation = false)
        assertTrue(inService.offersVacation)
        assertTrue(inService.canStartVacation)
        assertFalse(inService.canEndVacation)
        assertFalse(ui(model = 12, station = 1, vacation = false).canStartVacation) // regenerating
        assertFalse(ui(model = 12, station = 5, vacation = false).canStartVacation) // valve closed
    }

    @Test
    fun `vacation ends only from the stable vacation pause`() {
        assertFalse(ui(model = 12, station = 3, vacation = true).canEndVacation) // still preparing
        assertTrue(ui(model = 12, station = 8, vacation = true).canEndVacation)
        assertFalse(ui(model = 12, station = 8, vacation = true).canStartVacation)
    }

    @Test
    fun `vacation needs the experimental opt-in`() {
        val locked = midnight.copy(controlsUnlocked = false, unlockedModelCode = null)
        assertTrue(ui(model = 12, station = 0, vacation = false, device = locked).offersVacation)
        assertFalse(ui(model = 12, station = 0, vacation = false, device = locked).canStartVacation)
    }

    @Test
    fun `vacation is not offered on the verified F79D, where the write has no effect`() {
        val g6 = midnight.copy(controlsUnlocked = false, unlockedModelCode = null)
        val state = ui(model = 9, station = 0, vacation = false, device = g6)
        assertFalse(state.offersVacation)
        assertFalse(state.canStartVacation)
        assertTrue(state.canRegenerate)
    }

    @Test
    fun `regeneration is not offered during vacation`() {
        assertFalse(ui(model = 12, station = 8, vacation = true).canRegenerate)
    }
}
