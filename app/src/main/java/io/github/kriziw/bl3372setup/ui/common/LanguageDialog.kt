package io.github.kriziw.bl3372setup.ui.common

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.kriziw.bl3372setup.AppLanguage
import io.github.kriziw.bl3372setup.LanguageOption
import io.github.kriziw.bl3372setup.R

/** A globe button that opens the [LanguageDialog]. */
@Composable
fun LanguageButton() {
    var open by rememberSaveable { mutableStateOf(false) }
    FilledIconButton(
        onClick = { open = true },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Icon(painterResource(R.drawable.ic_language), contentDescription = stringResource(R.string.cd_language))
    }
    if (open) LanguageDialog(onDismiss = { open = false })
}

/**
 * Lets the user pick the app language. Language names are shown in their own language so they
 * can be found whatever the current UI language is.
 */
@Composable
fun LanguageDialog(onDismiss: () -> Unit) {
    val activity = LocalActivity.current ?: return
    val current = remember { AppLanguage.current(activity) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                LanguageOption.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == current,
                                role = Role.RadioButton,
                                onClick = {
                                    onDismiss()
                                    AppLanguage.set(activity, option)
                                },
                            )
                            .padding(vertical = 8.dp),
                    ) {
                        RadioButton(selected = option == current, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                stringResource(
                                    when (option) {
                                        LanguageOption.SYSTEM -> R.string.language_system
                                        LanguageOption.HUNGARIAN -> R.string.language_hungarian
                                        LanguageOption.ENGLISH -> R.string.language_english
                                    },
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (option == LanguageOption.SYSTEM) Hint(stringResource(R.string.language_system_hint))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
