package io.github.kriziw.bl3372setup.ui.setup

import io.github.kriziw.bl3372setup.broadlink.BroadlinkPackets
import io.github.kriziw.bl3372setup.broadlink.DiscoveredDevice
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress

class DiscoveryDeviceGroupsTest {
    private fun device(type: Int, name: String) = DiscoveredDevice(
        InetAddress.getLoopbackAddress(), type, "11:22:33:44:55:66", name, false,
    )

    @Test
    fun `module type rather than friendly name determines the default list`() {
        val water = device(BroadlinkPackets.DEVTYPE_RUNXIN_BL3372, "")
        val other = device(0xFFFF, "Runxin water softener")
        val groups = DiscoveryDeviceGroups(listOf(other, water))
        assertEquals(listOf(water), groups.visible(showOther = false))
        assertEquals(listOf(other), groups.other)
    }

    @Test
    fun `toggle reveals every hidden result and can hide them again without dropping results`() {
        val water = device(BroadlinkPackets.DEVTYPE_RUNXIN_BL3372, "Softener")
        val thermostat = device(0x4EAD, "Thermostat")
        val unknown = device(0xFFFF, "")
        val groups = DiscoveryDeviceGroups(listOf(thermostat, water, unknown))
        assertEquals(listOf(water, thermostat, unknown), groups.visible(showOther = true))
        assertEquals(listOf(water), groups.visible(showOther = false))
        assertEquals(2, groups.other.size)
    }

    @Test
    fun `only unrelated results remain available through the toggle`() {
        val unknown = device(0xFFFF, "")
        val groups = DiscoveryDeviceGroups(listOf(unknown))
        assertEquals(emptyList<DiscoveredDevice>(), groups.visible(showOther = false))
        assertEquals(listOf(unknown), groups.visible(showOther = true))
        assertEquals(emptyList<DiscoveredDevice>(), DiscoveryDeviceGroups(emptyList()).visible(showOther = true))
    }
}
