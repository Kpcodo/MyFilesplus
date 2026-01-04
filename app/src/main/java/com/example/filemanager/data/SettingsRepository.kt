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

    val swipeDeleteEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[SWIPE_DELETE_ENABLED] ?: false }

    val swipeDeleteDirection: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[SWIPE_DELETE_DIRECTION] ?: 0 } // Default 0 (Left)

    // Swipe to Delete - Recents
    val trashRetentionDays: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[TRASH_RETENTION_DAYS] ?: 30 } // Default 30 days

    val animationSpeed: Flow<Float> = context.dataStore.data
        .map { preferences -> preferences[ANIMATION_SPEED] ?: 1.0f }

    companion object {
        val THEME_MODE = intPreferencesKey("theme_mode")
        val ACCENT_COLOR = intPreferencesKey("accent_color")
        val ICON_SIZE = floatPreferencesKey("icon_size")
        val SHOW_HIDDEN_FILES = booleanPreferencesKey("show_hidden_files")
        val VIEW_MODE = intPreferencesKey("view_mode")
        val SEARCH_BLUR_ENABLED = booleanPreferencesKey("search_blur_enabled")
        val SWIPE_NAVIGATION_ENABLED = booleanPreferencesKey("swipe_navigation_enabled")
        val SWIPE_DELETE_ENABLED = booleanPreferencesKey("swipe_delete_enabled")
        val SWIPE_DELETE_DIRECTION = intPreferencesKey("swipe_delete_direction") // 0 = Left, 1 = Right
        val TRASH_RETENTION_DAYS = intPreferencesKey("trash_retention_days")
        val ANIMATION_SPEED = floatPreferencesKey("animation_speed")
    }

    val viewMode: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[VIEW_MODE] ?: 0 }


    suspend fun setTrashRetentionDays(days: Int) {
        context.dataStore.edit { preferences ->
            preferences[TRASH_RETENTION_DAYS] = days
        }
    }




    val themeMode: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[THEME_MODE] ?: 0 }

    val accentColor: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[ACCENT_COLOR] ?: 0xFF6650a4.toInt() }

    val showHiddenFiles: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[SHOW_HIDDEN_FILES] ?: false }



    val searchBlurEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[SEARCH_BLUR_ENABLED] ?: true }

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

    suspend fun setAnimationSpeed(speed: Float) {
        context.dataStore.edit { preferences ->
            preferences[ANIMATION_SPEED] = speed
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
