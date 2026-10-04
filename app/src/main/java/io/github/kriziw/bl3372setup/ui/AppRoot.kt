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
import io.github.kriziw.bl3372setup.ui.device.DeviceRoute
import io.github.kriziw.bl3372setup.ui.home.HomeScreen
import io.github.kriziw.bl3372setup.ui.common.CompatibilityGuide
import io.github.kriziw.bl3372setup.ui.common.UpdateScreen
import io.github.kriziw.bl3372setup.updates.UpdateViewModel
import io.github.kriziw.bl3372setup.ui.setup.SetupRoute
import io.github.kriziw.bl3372setup.ui.setup.SetupStep

private const val HOME = "home"
private const val SETUP = "setup"
private const val ADD_EXISTING = "add"
private const val DEVICE = "device/"
private const val COMPATIBILITY = "compatibility"
private const val UPDATES = "updates"

/**
 * Minimal navigation: home dashboard, setup wizard (from the start or straight to "find device"),
 * and one screen per saved device. With exactly one saved device the app opens on it directly.
 */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val store = context.app.deviceStore
    val devices by store.devices.collectAsStateWithLifecycle()
    val defaultName = stringResource(R.string.device_default_name)
    var route by rememberSaveable { mutableStateOf(devices.singleOrNull()?.let { DEVICE + it.mac } ?: HOME) }

    var guideReturn by rememberSaveable { mutableStateOf(HOME) }
    val openCompatibility = { guideReturn = route; route = COMPATIBILITY }
    val updates: UpdateViewModel = viewModel()
    val updateState by updates.state.collectAsStateWithLifecycle()
    var updateReturn by rememberSaveable { mutableStateOf(HOME) }
    val openUpdates = { updates.hideAnnouncement(); updateReturn = route; route = UPDATES }
    BackHandler(enabled = route != HOME) {
        route = when (route) { COMPATIBILITY -> guideReturn; UPDATES -> updateReturn; else -> HOME }
    }

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
        route == UPDATES -> UpdateScreen(updates, onBack = { route = updateReturn })
        route == COMPATIBILITY -> CompatibilityGuide(onBack = { route = guideReturn })
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
                route = DEVICE + found.mac
            },
            onExit = { route = HOME },
        )
        route.startsWith(DEVICE) && store.get(route.removePrefix(DEVICE)) != null ->
            DeviceRoute(mac = route.removePrefix(DEVICE), onBack = { route = HOME }, onCompatibility = openCompatibility)
        else -> HomeScreen(
            devices = devices,
            onOpen = { route = DEVICE + it.mac },
            onSetUpNew = { route = SETUP },
            onAddExisting = { route = ADD_EXISTING },
            onCompatibility = openCompatibility,
            onUpdates = openUpdates,
        )
    }
}
