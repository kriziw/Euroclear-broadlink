package io.github.kriziw.bl3372setup.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.kriziw.bl3372setup.R
import io.github.kriziw.bl3372setup.runxin.CatalogueEvidence
import io.github.kriziw.bl3372setup.runxin.ControllerCatalogue

/** Offline guide; official documentation opens in the browser only on an explicit tap. */
@Composable
fun CompatibilityGuide(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AppBackground(topBar = {
        Row(Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.cd_back))
            }
            Text(stringResource(R.string.compatibility_title), style = MaterialTheme.typography.titleLarge)
        }
    }) {
        SectionCard(stringResource(R.string.compatibility_detection_title)) {
            Text(stringResource(R.string.compatibility_detection_text))
        }
        ControllerLibrary()
        SectionCard(stringResource(R.string.compatibility_wifi_title)) {
            Text(stringResource(R.string.compatibility_wifi_text))
            TextButton(onClick = { uriHandler.openUri("https://run-xin.com/en/category126.htm") }) {
                Text(stringResource(R.string.compatibility_runxin_manuals))
            }
        }
        SectionCard(stringResource(R.string.compatibility_f105_title)) {
            Text(stringResource(R.string.compatibility_f105_text))
            TextButton(onClick = { uriHandler.openUri("https://manufacturervalve.com/pdf/download-center_22.pdf") }) {
                Text(stringResource(R.string.compatibility_f105_manual))
            }
        }
        SectionCard(stringResource(R.string.compatibility_other_title)) {
            Text(stringResource(R.string.compatibility_other_text))
        }
        SectionCard(stringResource(R.string.compatibility_broadlink_title)) {
            Text(stringResource(R.string.compatibility_broadlink_text))
            TextButton(onClick = { uriHandler.openUri("https://docs.ibroadlink.com/public/appsdk_en/appsdk_05/") }) {
                Text(stringResource(R.string.compatibility_broadlink_docs))
            }
        }
        Hint(stringResource(R.string.compatibility_browser_hint))
    }
}

@Composable
private fun ControllerLibrary() {
    val uriHandler = LocalUriHandler.current
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val matches = ControllerCatalogue.search(query)
    SectionCard(stringResource(R.string.catalogue_title)) {
        Text(stringResource(
            R.string.catalogue_summary,
            ControllerCatalogue.entries.count { it.protocolProfileId != null },
            ControllerCatalogue.entries.count { it.protocolProfileId == null },
        ))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.catalogue_search)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        if (matches.isEmpty()) Text(stringResource(R.string.catalogue_empty))
        matches.forEach { entry ->
            TextButton(
                onClick = { expanded = if (expanded == entry.name) null else entry.name },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    listOf(entry.name).plus(entry.aliases).joinToString(" / "),
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(
                    if (entry.protocolProfileId != null) R.string.catalogue_local else R.string.catalogue_reference,
                ), style = MaterialTheme.typography.labelSmall)
            }
            if (expanded == entry.name) {
                Text(stringResource(when (entry.evidence) {
                    CatalogueEvidence.LOCAL_PROTOCOL -> R.string.catalogue_local_detail
                    CatalogueEvidence.WIFI_DIRECTORY -> R.string.catalogue_wifi_detail
                    CatalogueEvidence.INSPECTED_MANUAL -> R.string.catalogue_manual_detail
                    CatalogueEvidence.DIRECTORY -> R.string.catalogue_directory_detail
                }))
                TextButton(onClick = { uriHandler.openUri(entry.sourceUrl) }) {
                    Text(stringResource(R.string.catalogue_source))
                }
            }
            HorizontalDivider()
        }
    }
}
