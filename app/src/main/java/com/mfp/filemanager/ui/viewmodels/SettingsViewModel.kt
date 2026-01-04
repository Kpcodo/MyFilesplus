package com.mfp.filemanager.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mfp.filemanager.data.GitHubRelease
import com.mfp.filemanager.data.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.ktor.client.plugins.onDownload
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class SettingsState(
    val themeMode: Int = 0,
    val accentColor: Int = 0xFF6650a4.toInt(),
    val iconSize: Float = 1.0f,
    val showHiddenFiles: Boolean = false,
    val viewMode: Int = 0,
    val isBlurEnabled: Boolean = true,
    val isSwipeNavigationEnabled: Boolean = false,
    val swipeDeleteEnabled: Boolean = false,
    val swipeDeleteDirection: Int = 0, // 0=Left, 1=Right
    val trashRetentionDays: Int = 30,
    val animationSpeed: Float = 1.0f
)

sealed class UpdateCheckState {
    object Idle : UpdateCheckState()
    object Checking : UpdateCheckState()
    data class UpdateAvailable(val release: GitHubRelease) : UpdateCheckState()
    data class Downloading(val progress: Float) : UpdateCheckState()
    data class DownloadFinished(val file: File) : UpdateCheckState()
    object UpToDate : UpdateCheckState()
    data class Error(val message: String) : UpdateCheckState()
}

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    // Ktor Client
    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    private val _updateState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateState: StateFlow<UpdateCheckState> = _updateState.asStateFlow()

    // CONSTANTS
    private val GITHUB_OWNER = "Kpcodo"
    private val GITHUB_REPO = "MyFilesplus"

    fun checkForUpdates(currentVersion: String) {
        viewModelScope.launch {
            _updateState.value = UpdateCheckState.Checking
            try {
                // Using GitHub Public API - Rate limited to 60/hr/IP
                val url = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
                val release: GitHubRelease = httpClient.get(url).body()
                
                // Simple version comparison: strict string equality or semantic versioning?
                // For simplicity, we assume tag_name matches version name (e.g., "v1.0.0" vs "1.0.0")
                // We'll strip 'v' prefix if present for robust comparison
                val remoteVersion = release.tagName.removePrefix("v")
                val localVersion = currentVersion.removePrefix("v")

                // A real semantic version comparison library is better, but this handles simple cases
                if (remoteVersion != localVersion) {
                    _updateState.value = UpdateCheckState.UpdateAvailable(release)
                } else {
                    _updateState.value = UpdateCheckState.UpToDate
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _updateState.value = UpdateCheckState.Error("Check failed: ${e.message}")
            }
        }
    }

    fun downloadUpdate(release: GitHubRelease, context: Context) {
        viewModelScope.launch {
            val apkAsset = release.assets.find { it.name.endsWith(".apk") }
            if (apkAsset == null) {
                _updateState.value = UpdateCheckState.Error("No APK found in release")
                return@launch
            }

            _updateState.value = UpdateCheckState.Downloading(0f)
            
            try {
                val bytes = httpClient.get(apkAsset.downloadUrl) {
                    onDownload { bytesSentTotal, contentLength ->
                        if (contentLength > 0) {
                            val progress = bytesSentTotal.toFloat() / contentLength
                            _updateState.value = UpdateCheckState.Downloading(progress)
                        }
                    }
                }.body<ByteArray>()
                
                val file = File(context.getExternalFilesDir(null), apkAsset.name)
                file.writeBytes(bytes)

                _updateState.value = UpdateCheckState.DownloadFinished(file)
                installApk(context, file)
            } catch (e: Exception) {
                e.printStackTrace()
                _updateState.value = UpdateCheckState.Error("Download failed: ${e.message}")
            }
        }
    }

    fun installApk(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _updateState.value = UpdateCheckState.Error("Install failed: ${e.message}")
        }
    }

    fun resetUpdateState() {
        _updateState.value = UpdateCheckState.Idle
    }

    val settingsState: StateFlow<SettingsState> = combine(
        repository.themeMode,
        repository.accentColor,
        repository.iconSize,
        repository.showHiddenFiles
    ) { theme, accent, size, hidden ->
        SettingsState(theme, accent, size, hidden)
    }.combine(repository.viewMode) { settings, view ->
        settings.copy(viewMode = view)
    }.combine(repository.searchBlurEnabled) { settings, blur ->
        settings.copy(isBlurEnabled = blur)
    }.combine(repository.swipeNavigationEnabled) { settings, swipe ->
        settings.copy(isSwipeNavigationEnabled = swipe)
    }.combine(repository.swipeDeleteEnabled) { settings, del ->
        settings.copy(swipeDeleteEnabled = del)
    }.combine(repository.swipeDeleteDirection) { settings, dir ->
        settings.copy(swipeDeleteDirection = dir)
    }.combine(repository.trashRetentionDays) { settings, days ->
        settings.copy(trashRetentionDays = days)
    }.combine(repository.animationSpeed) { settings, speed ->
        settings.copy(animationSpeed = speed)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsState()
    )

    fun setThemeMode(mode: Int) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun setAccentColor(color: Int) {
        viewModelScope.launch {
            repository.setAccentColor(color)
        }
    }

    fun setIconSize(size: Float) {
        viewModelScope.launch {
            repository.setIconSize(size)
        }
    }

    fun resetIconSize() {
        viewModelScope.launch {
            repository.setIconSize(1.0f)
        }
    }

    fun toggleShowHiddenFiles(show: Boolean) {
        viewModelScope.launch {
            repository.setShowHiddenFiles(show)
        }
    }

    fun setViewMode(mode: Int) {
        viewModelScope.launch {
            repository.setViewMode(mode)
        }
    }

    fun setAnimationSpeed(speed: Float) {
        viewModelScope.launch {
            repository.setAnimationSpeed(speed)
        }
    }

    fun toggleBlurEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSearchBlurEnabled(enabled)
        }
    }

    fun toggleSwipeNavigation(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSwipeNavigationEnabled(enabled)
        }
    }

    fun toggleSwipeDelete(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSwipeDeleteEnabled(enabled)
        }
    }

    fun setSwipeDeleteDirection(direction: Int) {
        viewModelScope.launch {
            repository.setSwipeDeleteDirection(direction)
        }
    }
    
    fun setTrashRetentionDays(days: Int) {
        viewModelScope.launch {
            repository.setTrashRetentionDays(days)
        }
    }

    override fun onCleared() {
        super.onCleared()
        httpClient.close()
    }
}

class SettingsViewModelFactory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
