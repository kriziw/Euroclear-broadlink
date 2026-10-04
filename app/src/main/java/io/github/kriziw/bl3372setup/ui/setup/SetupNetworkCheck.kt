package io.github.kriziw.bl3372setup.ui.setup

import io.github.kriziw.bl3372setup.network.isSetupApSsid

/** SSID and Internet detection are hints. The user confirms the active Wi-Fi connection. */
enum class ApCheck {
    NO_WIFI, RECOGNISED_NAME, DIFFERENT_NAME, HAS_INTERNET,
    NEEDS_MANUAL_CONFIRMATION, CONFIRMED_MANUALLY;

    val isConfirmed: Boolean get() = this == CONFIRMED_MANUALLY
}

fun assessSetupNetwork(hasWifi: Boolean, ssid: String?, hasInternet: Boolean, userConfirmed: Boolean): ApCheck = when {
    !hasWifi -> ApCheck.NO_WIFI
    userConfirmed -> ApCheck.CONFIRMED_MANUALLY
    ssid != null && isSetupApSsid(ssid) -> ApCheck.RECOGNISED_NAME
    hasInternet -> ApCheck.HAS_INTERNET
    ssid != null -> ApCheck.DIFFERENT_NAME
    else -> ApCheck.NEEDS_MANUAL_CONFIRMATION
}
