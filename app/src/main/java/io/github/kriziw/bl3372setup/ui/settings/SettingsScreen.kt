package io.github.kriziw.bl3372setup.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import io.github.kriziw.bl3372setup.AppLanguage
import io.github.kriziw.bl3372setup.LanguageOption
import io.github.kriziw.bl3372setup.R
import io.github.kriziw.bl3372setup.ui.common.AppScaffold
import io.github.kriziw.bl3372setup.ui.common.BackButton
import io.github.kriziw.bl3372setup.ui.common.LanguageDialog
import io.github.kriziw.bl3372setup.ui.common.ListCard
import io.github.kriziw.bl3372setup.ui.common.PrivacyFooter
import io.github.kriziw.bl3372setup.ui.common.SettingRow

/** App-wide settings: language, updates and the compatibility guide. */
@Composable
fun SettingsScreen(installedVersion: String, onBack: () -> Unit, onUpdates: () -> Unit, onCompatibility: () -> Unit) {
    var language by rememberSaveable { mutableStateOf(false) }
    if (language) LanguageDialog(onDismiss = { language = false })
    val activity = LocalActivity.current
    val current = remember(activity) { activity?.let { AppLanguage.current(it) } ?: LanguageOption.SYSTEM }

    AppScaffold(title = stringResource(R.string.settings_title), navigation = { BackButton(onBack) }) {
        ListCard {
            SettingRow(
                label = stringResource(R.string.language_title),
                value = stringResource(
                    when (current) {
                        LanguageOption.SYSTEM -> R.string.language_system
                        LanguageOption.HUNGARIAN -> R.string.language_hungarian
                        LanguageOption.ENGLISH -> R.string.language_english
                        LanguageOption.SPANISH -> R.string.language_spanish
                        LanguageOption.GERMAN -> R.string.language_german
                    },
                ),
                icon = R.drawable.ic_language,
                onClick = { language = true },
            )
            Divider()
            SettingRow(
                label = stringResource(R.string.updates_title),
                value = stringResource(R.string.updates_installed, installedVersion),
                icon = R.drawable.ic_system_update,
                onClick = onUpdates,
            )
            Divider()
            SettingRow(
                label = stringResource(R.string.compatibility_title),
                value = null,
                icon = R.drawable.ic_info,
                onClick = onCompatibility,
            )
        }
        PrivacyFooter()
    }
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(start = 60.dp), color = MaterialTheme.colorScheme.outlineVariant)
