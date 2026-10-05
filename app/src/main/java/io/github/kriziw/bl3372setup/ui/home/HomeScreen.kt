package io.github.kriziw.bl3372setup.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kriziw.bl3372setup.R
import io.github.kriziw.bl3372setup.appliance.Brand
import io.github.kriziw.bl3372setup.broadlink.BroadlinkPackets
import io.github.kriziw.bl3372setup.devices.SavedDevice
import io.github.kriziw.bl3372setup.ui.common.AppScaffold
import io.github.kriziw.bl3372setup.ui.common.Hint
import io.github.kriziw.bl3372setup.ui.common.IconBadge
import io.github.kriziw.bl3372setup.ui.common.OptionCard

/** The dashboard: remembered softeners, and one entry point to add another. */
@Composable
fun HomeScreen(
    devices: List<SavedDevice>,
    onOpen: (SavedDevice) -> Unit,
    onSetUpNew: () -> Unit,
    onAddExisting: () -> Unit,
    onAddOther: () -> Unit,
    onSettings: () -> Unit,
) {
    var addSheet by rememberSaveable { mutableStateOf(false) }
    AppScaffold(
        title = stringResource(R.string.app_name),
        subtitle = { Hint(stringResource(R.string.home_subtitle)) },
        navigation = {
            Image(
                painterResource(R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier.padding(start = 12.dp, end = 4.dp).size(36.dp),
            )
        },
        actions = {
            IconButton(onClick = onSettings) {
                Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings_title))
            }
        },
        floatingActionButton = {
            if (devices.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { addSheet = true },
                    icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                    text = { Text(stringResource(R.string.home_add_title)) },
                )
            }
        },
    ) {
        if (devices.isEmpty()) {
            EmptyState(onSetUpNew, onAddExisting, onAddOther)
        } else {
            devices.forEach { DeviceCard(it, onOpen) }
            // Room for the floating button over the last card.
            Spacer(Modifier.height(72.dp))
        }
    }
    if (addSheet) {
        AddDeviceSheet(
            onDismiss = { addSheet = false },
            onSetUpNew = { addSheet = false; onSetUpNew() },
            onAddExisting = { addSheet = false; onAddExisting() },
            onAddOther = { addSheet = false; onAddOther() },
        )
    }
}

@Composable
private fun EmptyState(onSetUpNew: () -> Unit, onAddExisting: () -> Unit, onAddOther: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 16.dp),
    ) {
        Image(painterResource(R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(112.dp))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.home_empty_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    AddOptions(onSetUpNew, onAddExisting, onAddOther)
}

@Composable
private fun AddOptions(onSetUpNew: () -> Unit, onAddExisting: () -> Unit, onAddOther: () -> Unit) {
    OptionCard(
        icon = R.drawable.ic_wifi,
        title = stringResource(R.string.home_set_up_new),
        description = stringResource(R.string.home_set_up_new_hint),
        onClick = onSetUpNew,
    )
    OptionCard(
        icon = R.drawable.ic_search,
        title = stringResource(R.string.home_add_existing),
        description = stringResource(R.string.home_add_existing_hint),
        onClick = onAddExisting,
    )
    OptionCard(
        icon = R.drawable.ic_water_drop,
        title = stringResource(R.string.home_add_other),
        description = stringResource(R.string.home_add_other_hint),
        onClick = onAddOther,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDeviceSheet(onDismiss: () -> Unit, onSetUpNew: () -> Unit, onAddExisting: () -> Unit, onAddOther: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).navigationBarsPadding(),
        ) {
            Text(stringResource(R.string.home_add_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
            AddOptions(onSetUpNew, onAddExisting, onAddOther)
        }
    }
}

@Composable
private fun DeviceCard(device: SavedDevice, onOpen: (SavedDevice) -> Unit) {
    Card(
        onClick = { onOpen(device) },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            IconBadge(R.drawable.ic_water_drop)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val brand = Brand.of(device.brand)
                Text(
                    when {
                        brand != null -> device.model ?: brand.displayName
                        else -> stringResource(
                            if (device.deviceType == BroadlinkPackets.DEVTYPE_RUNXIN_BL3372) R.string.device_type_runxin else R.string.device_type_other,
                            "0x%04X".format(device.deviceType),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Hint(device.lastIp)
            }
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.action_open),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
