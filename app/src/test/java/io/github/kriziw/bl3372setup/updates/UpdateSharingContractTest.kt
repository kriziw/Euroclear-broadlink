package io.github.kriziw.bl3372setup.updates

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** getUriForFile resolves manifest metadata before the provider's lazy path strategy exists. */
class UpdateSharingContractTest {
    @Test
    fun `Android can resolve APK sharing paths directly from the provider declaration`() {
        val parser = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
        val android = "http://schemas.android.com/apk/res/android"
        val manifest = parser.parse(File("src/main/AndroidManifest.xml"))
        val providers = manifest.getElementsByTagName("provider")
        val provider = (0 until providers.length).map { providers.item(it) as Element }
            .single { it.getAttributeNS(android, "name") == ".updates.UpdateFileProvider" }
        assertEquals("false", provider.getAttributeNS(android, "exported"))
        assertEquals("true", provider.getAttributeNS(android, "grantUriPermissions"))
        val metadata = provider.getElementsByTagName("meta-data")
        val paths = (0 until metadata.length).map { metadata.item(it) as Element }
            .single { it.getAttributeNS(android, "name") == "android.support.FILE_PROVIDER_PATHS" }
        val resource = paths.getAttributeNS(android, "resource")
        assertTrue(resource.startsWith("@xml/"))
        val pathXml = parser.parse(File("src/main/res/xml/${resource.removePrefix("@xml/")}.xml"))
        val cachePaths = pathXml.getElementsByTagName("cache-path")
        assertEquals(1, cachePaths.length)
        assertEquals("updates/", (cachePaths.item(0) as Element).getAttribute("path"))
        assertEquals(0, pathXml.getElementsByTagName("root-path").length)
        assertEquals(0, pathXml.getElementsByTagName("external-path").length)
    }
}
