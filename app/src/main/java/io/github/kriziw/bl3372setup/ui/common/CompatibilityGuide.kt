package io.github.kriziw.bl3372setup.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import io.github.kriziw.bl3372setup.R
import io.github.kriziw.bl3372setup.appliance.BrandCatalogue
import io.github.kriziw.bl3372setup.appliance.BrandSupport
import io.github.kriziw.bl3372setup.runxin.CatalogueEvidence
import io.github.kriziw.bl3372setup.runxin.ControllerCatalogue
import io.github.kriziw.bl3372setup.runxin.CompatibilityStatus
import io.github.kriziw.bl3372setup.runxin.DocumentedController

/** Offline guide; official documentation opens in the browser only on an explicit tap. */
@Composable
fun CompatibilityGuide(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AppScaffold(title = stringResource(R.string.compatibility_title), navigation = { BackButton(onBack) }) {
        SectionCard(title = stringResource(R.string.compatibility_detection_title)) {
            Text(stringResource(R.string.compatibility_detection_text))
        }
        ControllerLibrary()
        OtherBrands()
        SectionCard(title = stringResource(R.string.compatibility_wifi_title)) {
            Text(stringResource(R.string.compatibility_wifi_text))
            TextButton(onClick = { uriHandler.openUri("https://run-xin.com/en/category126.htm") }) {
                Text(stringResource(R.string.compatibility_runxin_manuals))
            }
        }
        SectionCard(title = stringResource(R.string.compatibility_f105_title)) {
            Text(stringResource(R.string.compatibility_f105_text))
            TextButton(onClick = { uriHandler.openUri("https://manufacturervalve.com/pdf/download-center_22.pdf") }) {
                Text(stringResource(R.string.compatibility_f105_manual))
            }
        }
        SectionCard(title = stringResource(R.string.compatibility_other_title)) {
            Text(stringResource(R.string.compatibility_other_text))
        }
        SectionCard(title = stringResource(R.string.compatibility_broadlink_title)) {
            Text(stringResource(R.string.compatibility_broadlink_text))
            TextButton(onClick = { uriHandler.openUri("https://docs.ibroadlink.com/public/appsdk_en/appsdk_05/") }) {
                Text(stringResource(R.string.compatibility_broadlink_docs))
            }
        }
        Hint(stringResource(R.string.compatibility_browser_hint))
    }
}

/** Softeners of other brands from the Home Assistant community review, grouped by how WaterCare relates to them. */
@Composable
private fun OtherBrands() {
    val uriHandler = LocalUriHandler.current
    SectionCard(title = stringResource(R.string.catalogue_other_brands_title)) {
        Text(stringResource(R.string.catalogue_other_brands_hint))
    }
    BrandCatalogue.bySupport().forEach { (support, entries) ->
        SectionCard(
            title = stringResource(
                when (support) {
                    BrandSupport.LOCAL_CONTROL -> R.string.brand_support_control
                    BrandSupport.LOCAL_MONITOR -> R.string.brand_support_monitor
                    BrandSupport.CLOUD_ONLY -> R.string.brand_support_cloud
                    BrandSupport.BLUETOOTH -> R.string.brand_support_bluetooth
                    BrandSupport.DIY -> R.string.brand_support_diy
                },
            ),
        ) {
            entries.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider()
                Text(entry.name, style = MaterialTheme.typography.titleSmall)
                Hint(entry.models)
                TextButton(onClick = { uriHandler.openUri(entry.sourceUrl) }) { Text(stringResource(R.string.catalogue_source)) }
            }
        }
    }
}

@Composable
private fun ControllerLibrary() {
    var query by rememberSaveable { mutableStateOf("") }
    var expandedFamily by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedModel by rememberSaveable { mutableStateOf<String?>(null) }
    val families = ControllerCatalogue.families(query)
    SectionCard(title = stringResource(R.string.catalogue_title)) {
        Text(stringResource(R.string.catalogue_summary))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.catalogue_search)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        if (families.isEmpty()) Text(stringResource(R.string.catalogue_empty))
    }
    CompatibilityStatus.entries.forEach { status ->
        val matchingFamilies = families.mapNotNull { family ->
            family.copy(variants = family.variants.filter { it.compatibility == status }).takeIf { it.variants.isNotEmpty() }
        }
        if (matchingFamilies.isNotEmpty()) {
            val supported = status == CompatibilityStatus.SUPPORTED
            SectionCard(title = stringResource(if (supported) R.string.catalogue_supported else R.string.catalogue_unverified)) {
                Text(stringResource(if (supported) R.string.catalogue_supported_hint else R.string.catalogue_unverified_hint))
                matchingFamilies.forEach { family ->
                    if (!supported) {
                        TextButton(
                            onClick = { expandedFamily = if (expandedFamily == family.name) null else family.name },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(family.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.catalogue_variants), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (supported || expandedFamily == family.name || query.isNotBlank()) {
                        family.variants.forEach { entry ->
                            CatalogueEntry(entry, expandedModel == entry.name) {
                                expandedModel = if (expandedModel == entry.name) null else entry.name
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun CatalogueEntry(entry: DocumentedController, expanded: Boolean, onExpand: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    TextButton(onClick = onExpand, modifier = Modifier.fillMaxWidth()) {
        Text(listOf(entry.name).plus(entry.aliases).joinToString(" / "), modifier = Modifier.weight(1f))
    }
    if (expanded) {
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
}
