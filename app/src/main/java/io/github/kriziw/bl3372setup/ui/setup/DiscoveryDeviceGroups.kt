package io.github.kriziw.bl3372setup.ui.setup

import io.github.kriziw.bl3372setup.broadlink.DiscoveredDevice

/** Discovery identifies the module type; controller profiles are loaded only after opening it. */
class DiscoveryDeviceGroups(found: List<DiscoveredDevice>) {
    private val groups = found.partition { it.isRunxinBl3372 }
    val waterTreatment: List<DiscoveredDevice> = groups.first
    val other: List<DiscoveredDevice> = groups.second

    fun visible(showOther: Boolean): List<DiscoveredDevice> =
        if (showOther) waterTreatment + other else waterTreatment
}
