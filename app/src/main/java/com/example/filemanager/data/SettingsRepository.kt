package com.example.filemanager.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    val swipeNavigationEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[SWIPE_NAVIGATION_ENABLED] ?: false }

    // Swipe to Delete - Recents
    companion object {
        val THEME_MODE = intPreferencesKey("theme_mode")
        val ACCENT_COLOR = intPreferencesKey("accent_color")
        val ICON_SIZE = floatPreferencesKey("icon_size")
        val SHOW_HIDDEN_FILES = booleanPreferencesKey("show_hidden_files")
        val SHOW_FILE_EXTENSIONS = booleanPreferencesKey("show_file_extensions")
        val VIEW_MODE = intPreferencesKey("view_mode")
        val SEARCH_BLUR_ENABLED = booleanPreferencesKey("search_blur_enabled")
        val SWIPE_NAVIGATION_ENABLED = booleanPreferencesKey("swipe_navigation_enabled")
        val SWIPE_DELETE_ENABLED = booleanPreferencesKey("swipe_delete_enabled")
        val SWIPE_DELETE_DIRECTION = intPreferencesKey("swipe_delete_direction") // 0 = Left, 1 = Right
    }

    val swipeDeleteEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[SWIPE_DELETE_ENABLED] ?: false }

    val swipeDeleteDirection: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[SWIPE_DELETE_DIRECTION] ?: 0 } // Default 0 (Left)

    val viewMode: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[VIEW_MODE] ?: 0 }

    val iconSize: Flow<Float> = context.dataStore.data
        .map { preferences -> preferences[ICON_SIZE] ?: 1.0f }


    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    suspend fun setAccentColor(color: Int) {
        context.dataStore.edit { preferences ->
            preferences[ACCENT_COLOR] = color
        }
    }

    suspend fun setIconSize(size: Float) {
        context.dataStore.edit { preferences ->
            preferences[ICON_SIZE] = size
        }
    }

    suspend fun setShowHiddenFiles(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_HIDDEN_FILES] = show
        }
    }

    suspend fun setShowFileExtensions(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_FILE_EXTENSIONS] = show
        }
    }
    
    suspend fun setViewMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[VIEW_MODE] = mode
        }
    }

    suspend fun setSearchBlurEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SEARCH_BLUR_ENABLED] = enabled
        }
    }

    suspend fun setSwipeNavigationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SWIPE_NAVIGATION_ENABLED] = enabled
        }
    }

    suspend fun setSwipeDeleteEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SWIPE_DELETE_ENABLED] = enabled
        }
    }

    suspend fun setSwipeDeleteDirection(direction: Int) {
        context.dataStore.edit { preferences ->
            preferences[SWIPE_DELETE_DIRECTION] = direction
        }
    }
}
