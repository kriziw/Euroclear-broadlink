package io.github.kriziw.bl3372setup.updates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class UpdateFailure { CHECK, DOWNLOAD, INCOMPATIBLE, INSTALL }
data class UpdateUiState(
    val installedName: String,
    val automatic: Boolean,
    val checking: Boolean = false,
    val checked: Boolean = false,
    val update: AppUpdate? = null,
    val announce: Boolean = false,
    val progress: Float? = null,
    val file: File? = null,
    val failure: UpdateFailure? = null,
)

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("app_updates", 0)
    private val installed = UpdateInstaller.installedVersion(application)
    private val source = GitHubUpdates()
    private var downloadJob: Job? = null
    private val directory = File(application.cacheDir, "updates")
    private val _state = MutableStateFlow(UpdateUiState(installed.first, preferences.getBoolean("automatic", true)))
    val state = _state.asStateFlow()

    init {
        if (_state.value.automatic) check(automatic = true)
    }

    fun automatic(enabled: Boolean) {
        preferences.edit { putBoolean("automatic", enabled) }
        _state.update { it.copy(automatic = enabled, announce = if (enabled) it.announce else false) }
    }

    fun dismiss() {
        _state.value.update?.let { preferences.edit { putString("dismissed", it.version.name) } }
        _state.update { it.copy(announce = false) }
    }

    fun hideAnnouncement() = _state.update { it.copy(announce = false) }

    fun check(automatic: Boolean = false) {
        if (_state.value.checking || downloadJob?.let { !it.isCompleted } == true) return
        _state.update { it.copy(checking = true, failure = null) }
        viewModelScope.launch {
            try {
                val update = source.check(installed.second)
                _state.update { old ->
                    old.copy(
                        checking = false, checked = true, update = update,
                        file = old.file?.takeIf { old.update == update && it.exists() },
                        announce = automatic && old.automatic && update != null && preferences.getString("dismissed", null) != update.version.name,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(checking = false, failure = UpdateFailure.CHECK) }
            }
        }
    }

    fun download() {
        val update = _state.value.update ?: return
        if (downloadJob?.let { !it.isCompleted } == true || _state.value.checking) return
        _state.update { it.copy(progress = 0f, file = null, failure = null) }
        downloadJob = viewModelScope.launch {
            try {
                val file = source.download(update, directory) { progress -> _state.update { it.copy(progress = progress) } }
                withContext(Dispatchers.IO) { UpdateInstaller.validate(getApplication(), file, update) }
                _state.update { it.copy(progress = null, file = file) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IncompatibleInstallation) {
                withContext(Dispatchers.IO) { File(directory, "update.apk").delete() }
                _state.update { it.copy(progress = null, failure = UpdateFailure.INCOMPATIBLE) }
            } catch (_: Exception) {
                withContext(Dispatchers.IO) { File(directory, "update.apk").delete() }
                _state.update { it.copy(progress = null, failure = UpdateFailure.DOWNLOAD) }
            } finally {
                _state.update { it.copy(progress = null) }
            }
        }
    }

    fun cancelDownload() = downloadJob?.cancel()
    fun installFailed() = _state.update { it.copy(failure = UpdateFailure.INSTALL) }
}
