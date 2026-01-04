package com.example.filemanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.filemanager.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

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
