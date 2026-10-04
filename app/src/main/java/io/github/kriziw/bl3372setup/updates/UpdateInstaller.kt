package io.github.kriziw.bl3372setup.updates

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class UpdateFileProvider : FileProvider()

object UpdateInstaller {
    @Suppress("DEPRECATION")
    fun installedVersion(context: Context): Pair<String, Long> =
        context.packageManager.getPackageInfo(context.packageName, 0).let { (it.versionName ?: "") to it.longVersionCode }

    /** Refuse debug/foreign builds and APKs which could not update this installation. */
    @Suppress("DEPRECATION")
    fun validate(context: Context, file: File, update: AppUpdate) {
        val manager = context.packageManager
        val installed = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val archive = manager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw IOException("Invalid APK")
        val expected = ReleasePolicy.RELEASE_CERT_SHA256
        fun signatures(info: android.content.pm.PackageInfo) = info.signingInfo?.apkContentsSigners?.map {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toHex()
        }
        if (signatures(installed) != listOf(expected)) throw IncompatibleInstallation()
        if (archive.packageName != context.packageName || archive.longVersionCode != update.version.code ||
            archive.versionName != update.version.name || archive.longVersionCode <= installed.longVersionCode ||
            signatures(archive) != listOf(expected)
        ) throw IOException("APK identity mismatch")
    }

    fun open(context: Context, file: File, update: AppUpdate) {
        validate(context, file, update)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            clipData = ClipData.newRawUri("APK", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}

class IncompatibleInstallation : IOException("Installed app uses a different signing certificate")
