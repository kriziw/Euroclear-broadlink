package io.github.kriziw.bl3372setup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kriziw.bl3372setup.R
import io.github.kriziw.bl3372setup.app
import io.github.kriziw.bl3372setup.devices.SavedDevice
import io.github.kriziw.bl3372setup.ui.appliance.AddApplianceRoute
import io.github.kriziw.bl3372setup.ui.appliance.ApplianceRoute
import io.github.kriziw.bl3372setup.ui.appliance.ApplianceWifiSetupRoute
import io.github.kriziw.bl3372setup.appliance.Brand
import io.github.kriziw.bl3372setup.ui.device.DeviceRoute
import io.github.kriziw.bl3372setup.ui.home.HomeScreen
import io.github.kriziw.bl3372setup.ui.common.CompatibilityGuide
import io.github.kriziw.bl3372setup.ui.common.UpdateScreen
import io.github.kriziw.bl3372setup.ui.settings.SettingsScreen
import io.github.kriziw.bl3372setup.updates.UpdateViewModel
import io.github.kriziw.bl3372setup.ui.setup.SetupRoute
import io.github.kriziw.bl3372setup.ui.setup.SetupStep

private const val HOME = "home"
private const val SETUP = "setup"
private const val ADD_EXISTING = "add"
private const val ADD_APPLIANCE = "add-brand"
private const val APPLIANCE_WIFI = "brand-wifi/"
private const val DEVICE = "device/"
private const val SETTINGS = "settings"
private const val COMPATIBILITY = "compatibility"
private const val UPDATES = "updates"

/**
 * Minimal navigation over a small back stack: home, setup wizard (from the start or straight to
 * "find device"), one screen per saved device, and settings with its two sub-screens. With exactly
 * one saved device the app opens on it directly, with home underneath.
 */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val store = context.app.deviceStore
    val devices by store.devices.collectAsStateWithLifecycle()
    val defaultName = stringResource(R.string.device_default_name)
    var stack by rememberSaveable {
        mutableStateOf(listOfNotNull(HOME, devices.singleOrNull()?.let { DEVICE + it.mac }))
    }
    val route = stack.last()
    val open = { next: String -> stack = stack + next }
    val back = { if (stack.size > 1) stack = stack.dropLast(1) }
    BackHandler(enabled = stack.size > 1) { back() }

    val updates: UpdateViewModel = viewModel()
    val updateState by updates.state.collectAsStateWithLifecycle()
    val openUpdates = { updates.hideAnnouncement(); open(UPDATES) }
    val openCompatibility = { open(COMPATIBILITY) }
    val openSettings = { open(SETTINGS) }
    // The add-brand screen's brand and address, kept while its Wi-Fi setup screen is open.
    var addBrand by rememberSaveable { mutableStateOf<String?>(null) }
    var addHost by rememberSaveable { mutableStateOf<String?>(null) }

    if (updateState.announce && updateState.update != null && route != UPDATES) {
        AlertDialog(
            onDismissRequest = updates::dismiss,
            title = { Text(stringResource(R.string.updates_available, updateState.update!!.version.name)) },
            text = { Text(stringResource(R.string.updates_notice)) },
            confirmButton = { TextButton(onClick = openUpdates) { Text(stringResource(R.string.updates_view)) } },
            dismissButton = { TextButton(onClick = updates::dismiss) { Text(stringResource(R.string.updates_later)) } },
        )
    }

    when {
        route == UPDATES -> UpdateScreen(updates, onBack = back)
        route == COMPATIBILITY -> CompatibilityGuide(onBack = back)
        route == SETTINGS -> SettingsScreen(
            installedVersion = updateState.installedName,
            onBack = back,
            onUpdates = openUpdates,
            onCompatibility = openCompatibility,
        )
        route == SETUP || route == ADD_EXISTING -> SetupRoute(
            initialStep = if (route == SETUP) SetupStep.CONNECT else SetupStep.FIND,
            savedMacs = devices.mapTo(HashSet()) { it.mac },
            onOpenDevice = { found ->
                store.save(
                    SavedDevice(
                        mac = found.mac,
                        // The module's own name is the vendor's Chinese default; use a readable one.
                        name = defaultName +
                            if (devices.isEmpty()) "" else " ${devices.size + 1}",
                        lastIp = found.address.hostAddress.orEmpty(),
                        deviceType = found.deviceType,
                    ),
                )
                // The wizard is finished; going back from the device returns home.
                stack = listOf(HOME, DEVICE + found.mac)
            },
            onExit = back,
        )
        route.startsWith(APPLIANCE_WIFI) && Brand.of(route.removePrefix(APPLIANCE_WIFI)) != null -> ApplianceWifiSetupRoute(
            brand = Brand.of(route.removePrefix(APPLIANCE_WIFI))!!,
            onBack = back,
            onDone = { host ->
                if (host != null) addHost = host
                back()
            },
        )
        route == ADD_APPLIANCE -> AddApplianceRoute(
            initialBrand = addBrand,
            initialHost = addHost,
            onWifiSetup = { brand -> addBrand = brand.id; open(APPLIANCE_WIFI + brand.id) },
            onBack = { addBrand = null; addHost = null; back() },
            // Adding is finished; going back from the device returns home.
            onSaved = { id -> addBrand = null; addHost = null; stack = listOf(HOME, DEVICE + id) },
        )
        route.startsWith(DEVICE) && store.get(route.removePrefix(DEVICE))?.brand != null -> ApplianceRoute(
            id = route.removePrefix(DEVICE),
            onBack = back,
            onCompatibility = openCompatibility,
            onSettings = openSettings,
        )
        route.startsWith(DEVICE) && store.get(route.removePrefix(DEVICE)) != null -> DeviceRoute(
            mac = route.removePrefix(DEVICE),
            onBack = back,
            onCompatibility = openCompatibility,
            onSettings = openSettings,
        )
        else -> HomeScreen(
            devices = devices,
            onOpen = { open(DEVICE + it.mac) },
            onSetUpNew = { open(SETUP) },
            onAddExisting = { open(ADD_EXISTING) },
            onAddOther = { addBrand = null; addHost = null; open(ADD_APPLIANCE) },
            onSettings = openSettings,
        )
    }
}
