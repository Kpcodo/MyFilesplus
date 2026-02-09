package com.mfp.filemanager

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import com.mfp.filemanager.data.SettingsRepository
import com.mfp.filemanager.utils.CrashHandler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.MainScope

class FileManagerApplication : Application(), ImageLoaderFactory {

    private var settingsRepository: SettingsRepository? = null

    override fun onCreate() {
        super.onCreate()
        try {
            val repo = SettingsRepository(applicationContext)
            settingsRepository = repo
            com.mfp.filemanager.utils.CrashHandler.init(applicationContext)
            
            createNotificationChannel()

            // Apply theme immediately (Synchronously to prevent flickering)
            kotlinx.coroutines.runBlocking {
                val themeMode = repo.themeMode.first()
                val mode = when (themeMode) {
                    1 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                    2 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                    3 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                    else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        setupWorkers()
    }
    
    private fun setupWorkers() {
        try {
            val workManager = androidx.work.WorkManager.getInstance(applicationContext)

            // Trash Cleanup: Run daily
            val trashCleanupRequest = androidx.work.PeriodicWorkRequestBuilder<com.mfp.filemanager.workers.TrashCleanupWorker>(
                24, java.util.concurrent.TimeUnit.HOURS
            ).build()

            workManager.enqueueUniquePeriodicWork(
                "TrashCleanup",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                trashCleanupRequest
            )

            // Update Check: Run every 3 hours
            val updateCheckRequest = androidx.work.PeriodicWorkRequestBuilder<com.mfp.filemanager.workers.UpdateCheckWorker>(
                3, java.util.concurrent.TimeUnit.HOURS
            ).build()

            workManager.enqueueUniquePeriodicWork(
                "UpdateCheck",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                updateCheckRequest
            )
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(applicationContext)
            .components {
                add(VideoFrameDecoder.Factory())
                add(com.mfp.filemanager.utils.AudioAlbumArtFetcher.Factory())
            }
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = "Updates"
            val descriptionText = "Notifications for app updates"
            val importance = android.app.NotificationManager.IMPORTANCE_DEFAULT
            val channel = android.app.NotificationChannel("updates", name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
