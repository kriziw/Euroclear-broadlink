package io.github.kriziw.bl3372setup.runxin

import io.github.kriziw.bl3372setup.devices.SavedDevice
import io.github.kriziw.bl3372setup.ui.device.Connection
import io.github.kriziw.bl3372setup.ui.device.DeviceUiState
import org.junit.Assert.*
import org.junit.Test

class ControllerProfilesTest {
    private fun ui(model: Int?, module: Int = 0x520F, unlocked: Boolean = false, checkedModel: Int? = null) = DeviceUiState(
        device = SavedDevice("AA:BB:CC:DD:EE:FF", "Softener", "192.168.1.2", module, unlocked, checkedModel),
        connection = Connection.Live,
        state = SoftenerState(buildMap {
            if (model != null) put(1, model to 0)
            put(34, 0 to 0)
            put(49, 0 to 0)
        }),
    )

    @Test
    fun `established identity automatically loads profile and enables controls`() {
        val state = ui(9)
        assertEquals(ControllerProfiles.F79D, state.profile)
        assertTrue(state.controlsEnabled)
        assertTrue(state.canRegenerate)
        assertFalse(state.copy(connection = Connection.NoWifi).controlsEnabled)
        assertFalse(state.copy(pendingWrite = SoftenerSetting.SaltAdded(20)).controlsEnabled)
    }

    @Test
    fun `catalogue names are never guessed as protocol identities`() {
        listOf(79, 82, 105, 136).forEach { code ->
            assertNull(ui(code).profile)
            assertFalse(ui(code).controlsEnabled)
        }
    }

    @Test
    fun `experimental opt-in only enables the checked controller identity`() {
        assertTrue(ui(105, unlocked = true, checkedModel = 105).controlsEnabled)
        assertFalse(ui(136, unlocked = true, checkedModel = 105).controlsEnabled)
        assertFalse(ui(105, unlocked = true).controlsEnabled) // Legacy unscoped opt-in.
        assertFalse(ui(null, unlocked = true).controlsEnabled)
        assertFalse(ui(9, module = 0x2712, unlocked = true, checkedModel = 9).controlsEnabled)
    }
}
