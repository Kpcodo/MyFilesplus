package com.mfp.filemanager.data.cache

import android.graphics.Bitmap
import android.util.LruCache

/**
 * A central in-memory cache for storing essential ephemeral data.
 * This cache is cleared automatically when the application process ends.
 * It manages:
 * 1. Thumbnails (Bitmaps)
 * 2. Storage/AI Analysis Logs
 * 3. Animation and Transition States
 */
object AppCache {
    
    // 1. Thumbnail Cache
    // Allocates ~1/8th of available memory for bitmaps
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8
    
    // Key: File Path, Value: Bitmap
    private val thumbnailCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    // 2. Storage Usage & AI Logs Cache
    // Stores heavy calculation results like folder sizes or AI predictions
    // Key: Identifier (e.g., "storage_analysis_root"), Value: Any Data Object
    private val dataCache = mutableMapOf<String, Any>()

    // 3. Animation & Transition Cache
    // Tracks state of UI animations to prevent redundant replays
    // Key: Animation ID (e.g., "home_dashboard_reveal"), Value: Boolean (Played/Not Played)
    private val animationStateCache = mutableMapOf<String, Boolean>()

    // --- Thumbnail Methods ---
    fun putThumbnail(path: String, bitmap: Bitmap) {
        thumbnailCache.put(path, bitmap)
    }

    fun getThumbnail(path: String): Bitmap? {
        return thumbnailCache.get(path)
    }

    // --- Data/Log Methods ---
    fun putData(key: String, data: Any) {
        dataCache[key] = data
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getData(key: String): T? {
        return dataCache[key] as? T
    }

    // --- Animation Methods ---
    fun setAnimationPlayed(key: String, played: Boolean = true) {
        animationStateCache[key] = played
    }

    fun hasAnimationPlayed(key: String): Boolean {
        return animationStateCache[key] == true
    }

    // --- Event Bus ---
    private val _cacheClearEvents = kotlinx.coroutines.flow.MutableSharedFlow<Unit>()
    val cacheClearEvents = _cacheClearEvents // Public access as Flow

    suspend fun triggerCacheClearEvent() {
        _cacheClearEvents.emit(Unit)
    }

    // --- Lifecycle ---
    fun clear() {
        thumbnailCache.evictAll()
        dataCache.clear()
        animationStateCache.clear()
        android.util.Log.d("AppCache", "All application cache cleared.")
    }
}
