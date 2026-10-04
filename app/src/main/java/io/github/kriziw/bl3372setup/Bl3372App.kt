package io.github.kriziw.bl3372setup

import android.app.Application
import android.content.Context
import io.github.kriziw.bl3372setup.devices.DeviceStore
import io.github.kriziw.bl3372setup.network.WifiNetworkMonitor

/** Process-wide services shared by the setup and device screens. */
class Bl3372App : Application() {
    val wifiMonitor: WifiNetworkMonitor by lazy { WifiNetworkMonitor(this).also { it.start() } }
    val deviceStore: DeviceStore by lazy { DeviceStore(this) }
}

val Context.app: Bl3372App get() = applicationContext as Bl3372App
