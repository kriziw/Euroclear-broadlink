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
}
