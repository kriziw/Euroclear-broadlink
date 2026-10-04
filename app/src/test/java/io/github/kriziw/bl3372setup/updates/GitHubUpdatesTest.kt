package io.github.kriziw.bl3372setup.updates

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest

class GitHubUpdatesTest {
    private val payload = "test APK bytes".toByteArray()
    private val hash = MessageDigest.getInstance("SHA-256").digest(payload).toHex()
    private fun update(size: Long = payload.size.toLong(), digest: String = hash) = AppUpdate(
        AppVersion.parse("0.3.0")!!,
        ReleaseAsset("bl3372-wifi-setup-0.3.0.apk", "${ReleasePolicy.REPOSITORY}/releases/download/v0.3.0/bl3372-wifi-setup-0.3.0.apk", size, true, "sha256:$digest"),
        digest, null, "",
    )

    private fun response(bytes: ByteArray, code: Int = 200, redirect: String? = null) = object : HttpURLConnection(URL("https://github.com")) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getInputStream() = ByteArrayInputStream(bytes)
        override fun getHeaderField(name: String?) = if (name == "Location") redirect else null
    }

    @Test
    fun `verified download is atomically made available and reports progress`() = runBlocking {
        val directory = Files.createTempDirectory("updates-test").toFile()
        try {
            var progress = 0f
            val file = GitHubUpdates { response(payload) }.download(update(), directory) { progress = it }
            assertArrayEquals(payload, file.readBytes())
            assertEquals(1f, progress)
            assertFalse(directory.resolve("update.part").exists())
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun `wrong checksum truncated and oversized downloads cannot produce an installable file`() = runBlocking {
        listOf(update(digest = "0".repeat(64)), update(size = payload.size + 1L), update(size = payload.size - 1L)).forEach { candidate ->
            val directory = Files.createTempDirectory("updates-test").toFile()
            try {
                try {
                    GitHubUpdates { response(payload) }.download(candidate, directory) {}
                    fail("Expected verification failure")
                } catch (_: IOException) { /* Expected. */ }
                assertFalse(directory.resolve("update.part").exists())
                assertFalse(directory.resolve("update.apk").exists())
            } finally { directory.deleteRecursively() }
        }
    }

    @Test
    fun `foreign redirect and HTTP failure leave no APK`() = runBlocking {
        listOf(response(payload, 302, "https://evil.test/apk"), response(payload, 403)).forEach { connection ->
            val directory = Files.createTempDirectory("updates-test").toFile()
            try {
                try {
                    GitHubUpdates { connection }.download(update(), directory) {}
                    fail("Expected HTTP failure")
                } catch (_: IOException) { /* Expected. */ }
                assertFalse(directory.resolve("update.apk").exists())
            } finally { directory.deleteRecursively() }
        }
    }

    @Test
    fun `cancelled download removes its partial file`() = runBlocking {
        val directory = Files.createTempDirectory("updates-test").toFile()
        try {
            val job = launch {
                GitHubUpdates { response(payload) }.download(update(), directory) { cancel() }
            }
            job.join()
            assertTrue(job.isCancelled)
            assertFalse(directory.resolve("update.part").exists())
            assertFalse(directory.resolve("update.apk").exists())
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun `legacy sidecar is fetched and validated before downloading APK`() = runBlocking {
        val directory = Files.createTempDirectory("updates-test").toFile()
        try {
            val candidate = update().copy(sha256 = null, checksumUrl = "${update().apk.url}.sha256")
            val calls = mutableListOf<String>()
            val source = GitHubUpdates { url ->
                calls.add(url)
                response(if (url.endsWith(".sha256")) "$hash  ${candidate.apk.name}\n".toByteArray() else payload)
            }
            assertArrayEquals(payload, source.download(candidate, directory) {}.readBytes())
            assertEquals(listOf(candidate.checksumUrl, candidate.apk.url), calls)
        } finally { directory.deleteRecursively() }
    }
}
