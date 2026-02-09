package com.mfp.filemanager.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mfp.filemanager.data.FileRepository
import com.mfp.filemanager.data.SettingsRepository
import kotlinx.coroutines.flow.first

class TrashCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val settingsRepository = SettingsRepository(applicationContext)
        val fileRepository = FileRepository(applicationContext)

        val retentionDays = settingsRepository.trashRetentionDays.first()
        if (retentionDays > 0) {
            try {
                fileRepository.cleanupExpiredTrash(retentionDays)
            } catch (e: Exception) {
                e.printStackTrace()
                return Result.retry()
            }
        }
        return Result.success()
    }
}
