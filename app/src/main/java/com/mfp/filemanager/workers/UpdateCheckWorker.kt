package com.mfp.filemanager.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mfp.filemanager.data.SettingsRepository
import kotlinx.coroutines.flow.first
import android.util.Log

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mfp.filemanager.data.GitHubRelease
import com.mfp.filemanager.MainActivity
import com.mfp.filemanager.R
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class UpdateCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val githubOwner = "Kpcodo"
    private val githubRepo = "MyFilesplus"

    override suspend fun doWork(): Result {
        val settingsRepository = SettingsRepository(applicationContext)
        val autoUpdateEnabled = settingsRepository.autoUpdateEnabled.first()

        if (autoUpdateEnabled) {
            val httpClient = HttpClient(Android) {
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    })
                }
            }

            try {
                Log.d("UpdateCheckWorker", "Checking for updates...")
                
                // Get current version
                val packageInfo = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
                val currentVersion = packageInfo.versionName ?: "1.0.0"

                // Fetch latest release
                val url = "https://api.github.com/repos/$githubOwner/$githubRepo/releases/latest"
                val release: GitHubRelease = httpClient.get(url).body()
                
                // Compare versions
                val remoteVersion = release.tagName.removePrefix("v")
                val localVersion = currentVersion.removePrefix("v")

                if (remoteVersion != localVersion) {
                    sendUpdateNotification(release)
                }
                
                settingsRepository.setLastUpdateCheckTime(System.currentTimeMillis())
                return Result.success()
            } catch (e: Exception) {
                e.printStackTrace()
                return Result.retry()
            } finally {
                httpClient.close()
            }
        }
        return Result.success()
    }

    private fun sendUpdateNotification(release: GitHubRelease) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, "updates")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure this exists, fallback to standard icon if needed
            .setContentTitle("Update Available")
            .setContentText("Version ${release.tagName} is available.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            // Check for permission if Android 13+ (POST_NOTIFICATIONS)
            // For now assuming permission is granted or user handles it, as requesting it from worker is hard.
            // In a real app we'd check ContextCompat.checkSelfPermission
            with(NotificationManagerCompat.from(applicationContext)) {
                notify(1001, builder.build())
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
