package io.github.kriziw.bl3372setup.updates

import java.net.URI

data class AppVersion(val name: String, val code: Long) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion) = code.compareTo(other.code)

    companion object {
        fun parse(tag: String): AppVersion? {
            val name = tag.removePrefix("v")
            if (!name.matches(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"))) return null
            val parts = name.split('.').map { it.toLongOrNull() ?: return null }
            if (parts[0] > 2147 || parts[1] > 999 || parts[2] > 999) return null
            val code = parts[0] * 1_000_000 + parts[1] * 1_000 + parts[2]
            return if (code <= Int.MAX_VALUE) AppVersion(name, code) else null
        }
    }
}

data class ReleaseAsset(val name: String, val url: String, val size: Long, val uploaded: Boolean, val digest: String?)
data class PublishedRelease(val tag: String, val draft: Boolean, val prerelease: Boolean, val notes: String, val assets: List<ReleaseAsset>)
data class AppUpdate(val version: AppVersion, val apk: ReleaseAsset, val sha256: String?, val checksumUrl: String?, val notes: String) {
    val releaseUrl get() = apk.url.substringBeforeLast('/').replace("/releases/download/", "/releases/tag/")
}

/** Only this repository's stable, versioned APK assets are update candidates. */
object ReleasePolicy {
    const val REPOSITORY = "https://github.com/kriziw/Euroclear-broadlink"
    const val API = "https://api.github.com/repos/kriziw/Euroclear-broadlink/releases?per_page=100"
    const val MAX_APK_BYTES = 100L * 1024 * 1024
    const val RELEASE_CERT_SHA256 = "d49dc49229e6e7b900439aee820169ffd8ba13b0704a409fabed849777790a0b"

    fun select(releases: List<PublishedRelease>, installedCode: Long): AppUpdate? = releases.mapNotNull { release ->
        if (release.draft || release.prerelease) return@mapNotNull null
        val version = AppVersion.parse(release.tag) ?: return@mapNotNull null
        if (version.code <= installedCode) return@mapNotNull null
        val name = "bl3372-wifi-setup-${version.name}.apk"
        val expectedUrl = "$REPOSITORY/releases/download/${release.tag}/$name"
        val apk = release.assets.singleOrNull {
            it.name == name && it.url == expectedUrl && it.uploaded && it.size in 1..MAX_APK_BYTES
        } ?: return@mapNotNull null
        val digest = apk.digest?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")
            ?.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }?.lowercase()
        val checksum = release.assets.singleOrNull {
            it.name == "$name.sha256" && it.url == "$expectedUrl.sha256" && it.uploaded && it.size in 1..4096
        }
        // Older GitHub assets can omit digest; the release workflow also publishes a sidecar.
        if (digest == null && checksum == null) return@mapNotNull null
        AppUpdate(version, apk, digest, checksum?.url, release.notes)
    }.maxByOrNull { it.version }

    fun checksum(text: String, expectedName: String): String? {
        val match = Regex("^([a-fA-F0-9]{64})[ \\t]+\\*?([^\\r\\n]+)[\\r\\n]*$").matchEntire(text) ?: return null
        return match.groupValues[1].lowercase().takeIf { match.groupValues[2] == expectedName }
    }

    /** GitHub asset redirects use its release storage/CDN; never allow HTTP or arbitrary hosts. */
    fun allowedDownloadUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.userInfo == null && uri.port == -1 && uri.host in setOf(
            "github.com", "api.github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com",
        )
    }.getOrDefault(false)
}
