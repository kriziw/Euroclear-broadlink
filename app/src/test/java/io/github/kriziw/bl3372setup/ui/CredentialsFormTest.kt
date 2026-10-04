package io.github.kriziw.bl3372setup.ui

import io.github.kriziw.bl3372setup.broadlink.SecurityMode
import io.github.kriziw.bl3372setup.network.isSetupApSsid
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialsFormTest {
    @Test
    fun `toString never reveals the credentials`() {
        val text = CredentialsForm("MyHomeWiFi", "correct-horse-42", SecurityMode.WPA2).toString()
        assertFalse(text.contains("correct-horse-42"))
        assertFalse(text.contains("MyHomeWiFi"))
    }

    @Test
    fun `recognises provisioning access points`() {
        assertTrue(isSetupApSsid("WiFi-BL3372"))
        assertTrue(isSetupApSsid("wifi-bl3372"))
        assertTrue(isSetupApSsid("BroadlinkProv"))
        assertFalse(isSetupApSsid("MyHomeWiFi"))
        assertTrue(CredentialsForm(ssid = "WiFi-BL3372").isDeviceApName)
    }
}
