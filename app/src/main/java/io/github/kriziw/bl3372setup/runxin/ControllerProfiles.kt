package io.github.kriziw.bl3372setup.runxin

import io.github.kriziw.bl3372setup.broadlink.BroadlinkPackets

/** A local protocol profile, not a catalogue product name or a Wi-Fi chipset model. */
data class ControllerProfile(
    val id: String,
    val name: String,
    val moduleType: Int,
    val modelCode: Int,
    val stateFields: List<Int>,
    val optionalFields: List<Int>,
)

/**
 * Only identities with an established wire mapping belong here. Official Runxin manuals
 * describe additional Wi-Fi products but do not map their names to field-1 values.
 * Model 9's mapping comes from the existing hardware-verified ypsilon-local evidence.
 * See docs/COMPATIBILITY.md for the official portfolio review and its limits.
 */
object ControllerProfiles {
    val F79D = ControllerProfile(
        id = "runxin-f79d-v1",
        name = "Runxin F79D",
        moduleType = BroadlinkPackets.DEVTYPE_RUNXIN_BL3372,
        modelCode = F79d.VERIFIED_MODEL,
        stateFields = F79d.STATE_FIELDS,
        optionalFields = listOf(52),
    )
    val registered: List<ControllerProfile> = listOf(F79D)

    fun resolve(moduleType: Int?, modelCode: Int?): ControllerProfile? =
        registered.singleOrNull { it.moduleType == moduleType && it.modelCode == modelCode }

    fun supportsTransport(moduleType: Int?) = moduleType == BroadlinkPackets.DEVTYPE_RUNXIN_BL3372

    /** An experimental opt-in belongs to the exact identity the user checked. */
    fun experimentalAllowed(moduleType: Int?, modelCode: Int?, unlocked: Boolean, unlockedModelCode: Int?): Boolean =
        supportsTransport(moduleType) && modelCode != null && unlocked && unlockedModelCode == modelCode
}
