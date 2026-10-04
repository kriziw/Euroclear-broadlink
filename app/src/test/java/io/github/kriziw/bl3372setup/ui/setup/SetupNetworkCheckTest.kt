package io.github.kriziw.bl3372setup.ui.setup

import org.junit.Assert.*
import org.junit.Test

class SetupNetworkCheckTest {
    @Test
    fun `user confirmation accepts arbitrary visible hidden and internet-validated device names`() {
        listOf("WiFi-BL3372", "WaterSoftener-123", "BroadlinkProv", "BroadlinkProv_ABC", "My-device-WIFI", null).forEach { ssid ->
            listOf(false, true).forEach { internet ->
                assertTrue(assessSetupNetwork(true, ssid, internet, userConfirmed = true).isConfirmed)
            }
        }
    }

    @Test
    fun `name and Internet detection provide hints until the user confirms`() {
        assertEquals(ApCheck.RECOGNISED_NAME, assessSetupNetwork(true, "WiFi-BL3372", false, false))
        assertEquals(ApCheck.RECOGNISED_NAME, assessSetupNetwork(true, "BroadlinkProv", false, false))
        assertEquals(ApCheck.DIFFERENT_NAME, assessSetupNetwork(true, "My-device-WIFI", false, false))
        assertEquals(ApCheck.HAS_INTERNET, assessSetupNetwork(true, "My-device-WIFI", true, false))
        assertEquals(ApCheck.NEEDS_MANUAL_CONFIRMATION, assessSetupNetwork(true, null, false, false))
        ApCheck.entries.filter { it != ApCheck.CONFIRMED_MANUALLY }.forEach { assertFalse(it.isConfirmed) }
    }

    @Test
    fun `confirmation cannot proceed without an active Wi-Fi connection`() {
        assertEquals(ApCheck.NO_WIFI, assessSetupNetwork(false, null, false, true))
        assertFalse(assessSetupNetwork(false, "WiFi-BL3372", true, true).isConfirmed)
    }
}
