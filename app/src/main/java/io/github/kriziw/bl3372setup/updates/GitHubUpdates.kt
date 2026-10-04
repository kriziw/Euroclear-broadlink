package io.github.kriziw.bl3372setup.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

/** Public metadata only. No controller details, account tokens or Wi-Fi information are sent. */
class GitHubUpdates(private val connectionFactory: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection }) {
    suspend fun check(installedCode: Long): AppUpdate? = withContext(Dispatchers.IO) {
        val json = JSONArray(readText(ReleasePolicy.API, 2 * 1024 * 1024))
        val releases = (0 until json.length()).map { index ->
            val release = json.getJSONObject(index)
            val assets = release.getJSONArray("assets")
            PublishedRelease(
                release.getString("tag_name"), release.getBoolean("draft"), release.getBoolean("prerelease"),
                release.optString("body").take(20_000),
                (0 until assets.length()).map { assetIndex ->
                    val asset = assets.getJSONObject(assetIndex)
                    ReleaseAsset(
                        asset.getString("name"), asset.getString("browser_download_url"), asset.getLong("size"),
                        asset.optString("state") == "uploaded", asset.optString("digest").takeIf { it != "null" && it.isNotBlank() },
                    )
                },
            )
        }
        ReleasePolicy.select(releases, installedCode)
    }

    suspend fun download(update: AppUpdate, directory: File, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val expected = update.sha256 ?: update.checksumUrl?.let {
            ReleasePolicy.checksum(readText(it, 4096), update.apk.name)
        } ?: throw IOException("Missing valid checksum")
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create update directory")
        val partial = File(directory, "update.part")
        val complete = File(directory, "update.apk")
        complete.delete()
        try {
            val connection = connect(update.apk.url)
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var bytes = 0L
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            bytes += count
                            if (bytes > update.apk.size || bytes > ReleasePolicy.MAX_APK_BYTES) throw IOException("APK exceeds expected size")
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            progress(bytes.toFloat() / update.apk.size)
                        }
                    }
                }
                if (bytes != update.apk.size || digest.digest().toHex() != expected) throw IOException("APK checksum mismatch")
                if (!partial.renameTo(complete)) throw IOException("Cannot store verified APK")
                complete
            } finally {
                connection.disconnect()
            }
        } finally {
            partial.delete()
        }
    }

    private fun readText(url: String, limit: Int): String {
        val connection = connect(url)
        try {
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > limit) throw IOException("Response too large")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            return bytes.toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private fun connect(url: String): HttpURLConnection {
        var next = url
        repeat(6) {
            if (!ReleasePolicy.allowedDownloadUrl(next)) throw IOException("Unexpected update host")
            val connection = connectionFactory(next)
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "BL3372-WiFi-Setup")
            if (URI(next).host == "api.github.com") {
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
            try {
                when (connection.responseCode) {
                    200 -> return connection
                    301, 302, 303, 307, 308 -> {
                        val location = connection.getHeaderField("Location") ?: throw IOException("Missing redirect")
                        next = URI(next).resolve(location).toString()
                        connection.disconnect()
                    }
                    else -> throw IOException("Update request failed: ${connection.responseCode}")
                }
            } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
        }
        throw IOException("Too many redirects")
    }
}

internal fun ByteArray.toHex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }
