package io.github.kriziw.bl3372setup.runxin

import org.junit.Assert.*
import org.junit.Test

class ControllerCatalogueTest {
    @Test
    fun `reference catalogue cannot register executable profiles`() {
        val linked = ControllerCatalogue.entries.filter { it.protocolProfileId != null }
        assertEquals(listOf(ControllerProfiles.F79D.id), linked.map { it.protocolProfileId })
        ControllerCatalogue.entries.filter { it.protocolProfileId == null }.forEach {
            // Even numeric product aliases must never be used as wire identities.
            it.aliases.mapNotNull(String::toIntOrNull).forEach { alias ->
                assertNull(ControllerProfiles.resolve(0x520F, alias))
            }
        }
        assertEquals(ControllerCatalogue.entries.size, ControllerCatalogue.entries.map { it.name }.toSet().size)
    }

    @Test
    fun `search finds old model names and exact manufacturer aliases`() {
        assertEquals(listOf("F105AHW"), ControllerCatalogue.search(" 86602ed ").map { it.name })
        assertEquals(listOf("F136BHW"), ControllerCatalogue.search("f136bhw").map { it.name })
        assertEquals(ControllerCatalogue.entries, ControllerCatalogue.search(""))
        assertTrue(ControllerCatalogue.search("not-a-model").isEmpty())
        assertEquals(4, ControllerCatalogue.search("LCD Wi-Fi").size)
    }

    @Test
    fun `family browsing preserves all variants and searches manufacturer aliases`() {
        assertEquals(ControllerCatalogue.entries.toSet(), ControllerCatalogue.families().flatMap { it.variants }.toSet())
        val aliasMatches = ControllerCatalogue.families("86602ed").single()
        assertEquals("F105 / F136", aliasMatches.name)
        assertEquals(listOf("F105AHW"), aliasMatches.variants.map { it.name })
        val f82 = ControllerCatalogue.families("F82").flatMap { it.variants }
        assertTrue(f82.isNotEmpty())
        assertTrue(f82.all { it.name.startsWith("F82") && it.compatibility == CompatibilityStatus.UNVERIFIED })
        assertTrue(ControllerCatalogue.families("unknown-family").isEmpty())
    }

    @Test
    fun `family membership and documentation do not promote an unimplemented controller to supported`() {
        val family = ControllerCatalogue.families().single { it.name == "F79 / F82" }
        assertEquals(listOf("F79D"), family.variants.filter { it.compatibility == CompatibilityStatus.SUPPORTED }.map { it.name })
        assertEquals(CompatibilityStatus.UNVERIFIED, family.variants.single { it.name == "F82A LCD Wi-Fi" }.compatibility)
        assertTrue(ControllerCatalogue.families("F105").flatMap { it.variants }.all { it.compatibility == CompatibilityStatus.UNVERIFIED })
        val implemented = family.variants.single { it.name == "F79D" }
        assertEquals(CompatibilityStatus.UNVERIFIED, implemented.copy(protocolProfileId = "missing-profile").compatibility)
    }
}
