package com.mfp.filemanager.utils

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Utility to log storage snapshots for AI analysis.
 * Logs are stored in cacheDir/storage_logs/storage_log_{DATE}.jsonl
 */
object StorageAnalysisLogger {

    private const val LOG_DIR_NAME = "storage_logs"
    private const val LOG_FILE_PREFIX = "storage_log_"
    private const val LOG_FILE_EXTENSION = ".jsonl"

    suspend fun logSnapshot(context: Context, totalBytes: Long, freeBytes: Long) {
        withContext(Dispatchers.IO) {
            try {
                val usedBytes = totalBytes - freeBytes
                val timestamp = System.currentTimeMillis()
                
                // Construct Log Entry
                val logEntry = JSONObject().apply {
                    put("timestamp", timestamp)
                    put("total_bytes", totalBytes)
                    put("free_bytes", freeBytes)
                    put("used_bytes", usedBytes)
                    // Add date string for human readability if needed, but timestamp is better for AI
                    put("date_str", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp)))
                }

                // Get/Create Log File for Today
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
                val logDir = File(context.cacheDir, LOG_DIR_NAME)
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }

                val logFile = File(logDir, "$LOG_FILE_PREFIX$today$LOG_FILE_EXTENSION")
                
                // Append log entry as a new line
                logFile.appendText("${logEntry.toString()}\n")
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
