package com.mfp.filemanager.data

import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {
    fun formatSize(size: Long): String {
        val kb = size / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1 -> String.format("%.2f GB", gb)
            mb >= 1 -> String.format("%.2f MB", mb)
            kb >= 1 -> String.format("%.2f KB", kb)
            else -> "$size B"
        }
    }

    fun formatDate(timestamp: Long): String {
        val date = Date(timestamp)
        val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return format.format(date)
    }

    @Suppress("DEPRECATION")
    fun getInternalStoragePath(): String {
        return Environment.getExternalStorageDirectory().absolutePath
    }

    fun getTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        val minute = 60 * 1000L
        val hour = 60 * minute
        val day = 24 * hour
        val month = 30 * day

        return when {
            diff < minute -> "Just now"
            diff < hour -> "${diff / minute} minutes ago"
            diff < day -> "${diff / hour} hours ago"
            diff < 30 * day -> "${diff / day} days ago"
            diff < 12 * month -> "${diff / month} months ago"
            else -> "Long time ago"
        }
    }
    fun openFile(context: android.content.Context, file: com.mfp.filemanager.data.FileModel) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", File(file.path))
            val mimeType = if (file.type == com.mfp.filemanager.data.FileType.APK) "application/vnd.android.package-archive" else file.mimeType
            
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Open file"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isTextFile(file: com.mfp.filemanager.data.FileModel): Boolean {
        val mime = file.mimeType ?: ""
        val name = file.name.lowercase()
        return mime.startsWith("text/") || 
               mime == "application/json" || 
               mime == "application/xml" ||
               name.endsWith(".log") ||
               name.endsWith(".txt") ||
               name.endsWith(".json") ||
               name.endsWith(".xml") ||
               name.endsWith(".kt") ||
               name.endsWith(".java") ||
               name.endsWith(".py") ||
               name.endsWith(".js") ||
               name.endsWith(".html") ||
               name.endsWith(".css") ||
               name.endsWith(".md") ||
               name.endsWith(".gradle") ||
               name.endsWith(".kts")
    }
}
