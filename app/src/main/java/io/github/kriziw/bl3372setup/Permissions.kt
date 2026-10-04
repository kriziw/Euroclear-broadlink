package io.github.kriziw.bl3372setup

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

object Permissions {
    /** Android 17. Apps targeting it need [ACCESS_LOCAL_NETWORK] for any LAN traffic. */
    private const val API_ANDROID_17 = 37

    const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

    /** Requested together; Android 12+ requires COARSE alongside FINE. */
    val LOCATION = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    val localNetworkRequired: Boolean get() = Build.VERSION.SDK_INT >= API_ANDROID_17

    fun hasLocalNetwork(context: Context): Boolean =
        !localNetworkRequired || isGranted(context, ACCESS_LOCAL_NETWORK)

    /** Precise location is what Android 10+ requires before it reveals the connected SSID. */
    fun hasPreciseLocation(context: Context): Boolean =
        isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun isLocationEnabled(context: Context): Boolean =
        LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java))

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

object SystemScreens {
    /** Opens the system Wi-Fi picker (a panel on Android 10+), falling back to Wi-Fi settings. */
    fun openWifiPicker(context: Context) {
        try {
            context.startActivity(Intent(Settings.Panel.ACTION_WIFI))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
    }

    fun openAppSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
    }

    fun openLocationSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }
}
