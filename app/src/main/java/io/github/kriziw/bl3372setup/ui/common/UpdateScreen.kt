package io.github.kriziw.bl3372setup.ui.common

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import io.github.kriziw.bl3372setup.R
import io.github.kriziw.bl3372setup.updates.UpdateFailure
import io.github.kriziw.bl3372setup.updates.UpdateInstaller
import io.github.kriziw.bl3372setup.updates.UpdateViewModel

@Composable
fun UpdateScreen(model: UpdateViewModel, onBack: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val browser = LocalUriHandler.current
    val install = {
        val current = model.state.value
        val file = current.file
        val update = current.update
        if (file != null && update != null) {
            try { UpdateInstaller.open(context, file, update) } catch (_: Exception) { model.installFailed() }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) install()
    }
    AppScaffold(title = stringResource(R.string.updates_title), navigation = { BackButton(onBack) }) {
        SectionCard {
            Text(stringResource(R.string.updates_installed, state.installedName))
            Hint(stringResource(R.string.updates_privacy))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().toggleable(state.automatic, role = Role.Switch, onValueChange = model::automatic),
            ) {
                Text(stringResource(R.string.updates_automatic), modifier = Modifier.weight(1f))
                Switch(checked = state.automatic, onCheckedChange = null)
            }
            OutlinedButton(onClick = { model.check() }, enabled = !state.checking && state.progress == null, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.checking) R.string.updates_checking else R.string.updates_check))
            }
            if (state.checked && !state.checking && state.update == null && state.failure == null) {
                Text(stringResource(R.string.updates_current))
            }
            state.failure?.let { failure ->
                Text(stringResource(when (failure) {
                    UpdateFailure.CHECK -> R.string.updates_check_failed
                    UpdateFailure.DOWNLOAD -> R.string.updates_download_failed
                    UpdateFailure.INCOMPATIBLE -> R.string.updates_incompatible
                    UpdateFailure.INSTALL -> R.string.updates_install_failed
                }), color = MaterialTheme.colorScheme.error)
            }
        }
        state.update?.let { update ->
            SectionCard(title = stringResource(R.string.updates_available, update.version.name)) {
                Text(stringResource(R.string.updates_download_size, update.apk.size / (1024f * 1024f)))
                TextButton(onClick = { browser.openUri(update.releaseUrl) }) { Text(stringResource(R.string.updates_release_notes)) }
                if (update.notes.isNotBlank()) Text(update.notes, style = MaterialTheme.typography.bodySmall)
                when {
                    state.progress != null -> {
                        LinearProgressIndicator(progress = { state.progress ?: 0f }, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.updates_progress, ((state.progress ?: 0f) * 100).toInt()))
                        TextButton(onClick = model::cancelDownload) { Text(stringResource(R.string.updates_cancel)) }
                    }
                    state.file != null -> {
                        Hint(stringResource(R.string.updates_install_hint))
                        Button(onClick = {
                            if (context.packageManager.canRequestPackageInstalls()) install()
                            else try {
                                permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()))
                            } catch (_: Exception) { model.installFailed() }
                        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.updates_install)) }
                    }
                    else -> Button(onClick = model::download, enabled = !state.checking, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.updates_download))
                    }
                }
            }
        }
    }
}
