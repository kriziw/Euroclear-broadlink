package io.github.kriziw.bl3372setup.updates

import org.junit.Assert.*
import org.junit.Test

class ReleasePolicyTest {
    private val hash = "a".repeat(64)
    private fun release(version: String, draft: Boolean = false, preview: Boolean = false): PublishedRelease {
        val name = "bl3372-wifi-setup-$version.apk"
        return PublishedRelease("v$version", draft, preview, "Changes", listOf(
            ReleaseAsset(name, "${ReleasePolicy.REPOSITORY}/releases/download/v$version/$name", 1024, true, "sha256:$hash"),
        ))
    }

    @Test
    fun `version comparison is numeric and matches Android release version codes`() {
        assertEquals(2010L, AppVersion.parse("v0.2.10")?.code)
        assertEquals(1_002_003L, AppVersion.parse("1.2.3")?.code)
        assertTrue(AppVersion.parse("0.2.10")!! > AppVersion.parse("0.2.9")!!)
        listOf("1.2", "1.2.3-rc1", "01.2.3", "-1.0.0", "1.1000.0", "999999999999999.0.0", "2147.999.999").forEach {
            assertNull(AppVersion.parse(it))
        }
    }

    @Test
    fun `newest stable APK wins regardless of release listing order`() {
        val releases = listOf(release("0.2.10"), release("0.3.0", draft = true), release("0.4.0", preview = true), release("0.2.9"))
        assertEquals("0.2.10", ReleasePolicy.select(releases, 2009)?.version?.name)
        assertNull(ReleasePolicy.select(releases, 2010))
        assertNull(ReleasePolicy.select(releases, 3000))
    }

    @Test
    fun `release published before APK upload does not hide an older available update`() {
        assertEquals("0.2.9", ReleasePolicy.select(listOf(release("0.3.0").copy(assets = emptyList()), release("0.2.9")), 2001)?.version?.name)
        val release = release("0.3.0")
        assertNull(ReleasePolicy.select(listOf(release.copy(assets = release.assets.map { it.copy(uploaded = false) })), 2001))
    }

    @Test
    fun `unverifiable ambiguous and foreign assets are rejected`() {
        val release = release("0.3.0")
        val apk = release.assets.single()
        listOf(
            listOf(apk.copy(digest = null)), listOf(apk.copy(url = "https://example.com/update.apk")),
            listOf(apk.copy(name = "debug.apk")), listOf(apk.copy(size = ReleasePolicy.MAX_APK_BYTES + 1)),
            listOf(apk.copy(size = 0)), listOf(apk, apk), listOf(apk.copy(digest = "sha512:$hash")),
        ).forEach { assets -> assertNull(ReleasePolicy.select(listOf(release.copy(assets = assets)), 2001)) }
    }

    @Test
    fun `legacy sidecar requires exact asset identity and checksum file name`() {
        val release = release("0.3.0")
        val apk = release.assets.single().copy(digest = null)
        val sidecar = ReleaseAsset("${apk.name}.sha256", "${apk.url}.sha256", 100, true, null)
        val update = ReleasePolicy.select(listOf(release.copy(assets = listOf(apk, sidecar))), 2001)!!
        assertEquals(sidecar.url, update.checksumUrl)
        assertEquals(hash, ReleasePolicy.checksum("$hash  ${apk.name}\n", apk.name))
        assertEquals(hash, ReleasePolicy.checksum("$hash *${apk.name}\r\n", apk.name))
        assertNull(ReleasePolicy.checksum("$hash  other.apk\n", apk.name))
        assertNull(ReleasePolicy.checksum("$hash  ${apk.name}\n$hash  other.apk", apk.name))
    }

    @Test
    fun `redirect policy accepts only HTTPS GitHub storage without embedded credentials`() {
        assertTrue(ReleasePolicy.allowedDownloadUrl("https://release-assets.githubusercontent.com/assets/one?token=public"))
        listOf("http://github.com/a", "https://github.com.evil.test/a", "https://user@github.com/a", "https://github.com:8443/a", "file:///tmp/a").forEach {
            assertFalse(ReleasePolicy.allowedDownloadUrl(it))
        }
    }
}
