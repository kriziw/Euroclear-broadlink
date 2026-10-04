package io.github.kriziw.bl3372setup.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kriziw.bl3372setup.R
import io.github.kriziw.bl3372setup.broadlink.BroadlinkPackets
import io.github.kriziw.bl3372setup.devices.SavedDevice
import io.github.kriziw.bl3372setup.ui.common.AppBackground
import io.github.kriziw.bl3372setup.ui.common.Hint
import io.github.kriziw.bl3372setup.ui.common.LanguageButton
import io.github.kriziw.bl3372setup.ui.common.PrivacyFooter
import io.github.kriziw.bl3372setup.ui.common.SectionCard

/** The dashboard: remembered softeners, plus entry points to enrol another one. */
@Composable
fun HomeScreen(
    devices: List<SavedDevice>,
    onOpen: (SavedDevice) -> Unit,
    onSetUpNew: () -> Unit,
    onAddExisting: () -> Unit,
) {
    AppBackground {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Image(painter = painterResource(R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(56.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LanguageButton()
        }

        if (devices.isEmpty()) {
            SectionCard(stringResource(R.string.home_empty_title)) {
                Hint(stringResource(R.string.home_empty_text))
            }
        } else {
            devices.forEach { DeviceCard(it, onOpen) }
        }

        SectionCard(stringResource(R.string.home_add_title)) {
            Button(onClick = onSetUpNew, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_set_up_new))
            }
            Hint(stringResource(R.string.home_set_up_new_hint))
            OutlinedButton(onClick = onAddExisting, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_add_existing))
            }
            Hint(stringResource(R.string.home_add_existing_hint))
        }

        PrivacyFooter()
    }
}

@Composable
private fun DeviceCard(device: SavedDevice, onOpen: (SavedDevice) -> Unit) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        modifier = Modifier.fillMaxWidth().clickable { onOpen(device) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Image(painter = painterResource(R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(
                        if (device.deviceType == BroadlinkPackets.DEVTYPE_RUNXIN_BL3372) R.string.device_type_runxin else R.string.device_type_other,
                        "0x%04X".format(device.deviceType),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Hint("${device.lastIp}  ·  ${device.mac}")
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.action_open))
        }
    }
}
