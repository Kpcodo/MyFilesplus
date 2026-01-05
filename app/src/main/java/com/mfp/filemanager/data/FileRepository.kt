package com.mfp.filemanager.data

import com.mfp.filemanager.data.trash.TrashManager
import com.mfp.filemanager.data.trash.TrashedFile
import com.mfp.filemanager.data.archive.ArchiveManager

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.os.Process
import android.os.UserHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import kotlinx.coroutines.withContext

data class StorageVolumeInfo(
    val file: File,
    val name: String
)

class FileRepository(private val context: Context) {

    suspend fun getFilesFromPath(path: String): List<FileModel> = withContext(Dispatchers.IO) {
        val directory = File(path)
        if (!directory.exists() || !directory.isDirectory) {
            return@withContext emptyList()
        }

        val files = directory.listFiles()
        files?.mapNotNull { file ->
            val mimeType = if (file.isFile) context.contentResolver.getType(Uri.fromFile(file)) else null
            val itemCount = if (file.isDirectory) file.list()?.size ?: 0 else 0
            FileModel(
                id = file.hashCode().toLong(), // Simple ID
                name = file.name,
                path = file.path,
                size = if (file.isFile) file.length() else 0,
                dateModified = file.lastModified(),
                mimeType = mimeType,
                type = determineFileType(mimeType, file.name),
                isDirectory = file.isDirectory,
                itemCount = itemCount
            )
        }?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
    }

    @Suppress("DEPRECATION")
    suspend fun calculateStorageForecast(): String = withContext(Dispatchers.IO) {
        try {
            val totalBytes = Environment.getExternalStorageDirectory().totalSpace
            val freeBytes = Environment.getExternalStorageDirectory().freeSpace

            // Calculate usage in the last 30 days
            val recentUsageBytes = getAverageDailyUsageBytes() * 30

            if (recentUsageBytes <= 0) return@withContext "Stable"
            
            val dailyRate = recentUsageBytes / 30f
            if (dailyRate <= 0) return@withContext "Stable"

            val daysUntilFull = (freeBytes / dailyRate).toLong()

            return@withContext when {
                daysUntilFull < 1 -> "< 1 day"
                daysUntilFull < 30 -> "Full in ~$daysUntilFull days"
                daysUntilFull < 365 -> "Full in ~${daysUntilFull / 30} mo"
                else -> "> 1 yr left"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "Unknown"
        }
    }
    
    private val trashManager = TrashManager(context)

    suspend fun deleteFile(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (file.exists()) {
                return@withContext trashManager.moveToTrash(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext false
    }

    suspend fun getTrashedFiles(): List<TrashedFile> = trashManager.getTrashedFiles()

    suspend fun restoreFile(trashedFile: TrashedFile): Boolean = trashManager.restoreFromTrash(trashedFile)

    suspend fun restoreAllFiles(): Boolean = trashManager.restoreAll()

    fun getExternalVolumes(): List<StorageVolumeInfo> {
        val volumes = mutableListOf<StorageVolumeInfo>()
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as android.os.storage.StorageManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            storageManager.storageVolumes.forEach { volume ->
                if (volume.isRemovable || !volume.isPrimary) {
                    val dir = volume.directory
                    if (dir != null && dir.exists() && dir.canRead()) {
                        val name = volume.getDescription(context)
                        volumes.add(StorageVolumeInfo(dir, name))
                    }
                }
            }
        } else {
            storageManager.storageVolumes.forEach { volume ->
                 if (volume.isRemovable || !volume.isPrimary) {
                     val uuid = volume.uuid
                     if (uuid != null) {
                         val dir = File("/storage/$uuid")
                         if (dir.exists() && dir.canRead()) {
                             val name = volume.getDescription(context)
                             volumes.add(StorageVolumeInfo(dir, name))
                         }
                     }
                 }
            }
        }
        
        return volumes.distinctBy { it.file.absolutePath }
    }

    // Removed unused helper methods to keep code clean since we switched logic
    suspend fun deleteFilePermanently(trashedFile: TrashedFile): Boolean = trashManager.deletePermanently(trashedFile)

    suspend fun emptyTrash(): Boolean = trashManager.emptyTrash()

    suspend fun cleanupExpiredTrash(days: Int): Int = trashManager.cleanupExpiredFiles(days)
    
    suspend fun getTrashSize(): Long = trashManager.getTrashedFiles().sumOf { it.size }

    @Suppress("DEPRECATION")
    suspend fun getEmptyFolders(): List<File> = withContext(Dispatchers.IO) {
        val root = Environment.getExternalStorageDirectory()
        val emptyFolders = mutableListOf<File>()
        try {
            // Limited recursion depth or constrained scan to avoid performance issues
            scanForEmptyFolders(root, emptyFolders, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        emptyFolders
    }

    @Suppress("DEPRECATION")
    suspend fun getJunkSize(): Long = withContext(Dispatchers.IO) {
        var size: Long = 0
        try {
            val root = Environment.getExternalStorageDirectory()
            val junkFiles = mutableListOf<File>()
            scanForJunkFiles(root, junkFiles, 0)
            size = junkFiles.sumOf { it.length() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        size
    }

    @Suppress("DEPRECATION")
    suspend fun clearJunk(): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = Environment.getExternalStorageDirectory()
            val junkFiles = mutableListOf<File>()
            scanForJunkFiles(root, junkFiles, 0)
            
            var allDeleted = true
            junkFiles.forEach { file ->
                if (file.exists()) {
                    // Try simple delete first
                    if (!file.delete()) {
                        allDeleted = false
                    }
                }
            }
            allDeleted
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun scanForJunkFiles(directory: File, list: MutableList<File>, depth: Int) {
        if (depth > 4) return // Limit depth
        val files = directory.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory) {
                // Skip sensitive or system folders
                if (file.name.equals("Android", ignoreCase = true) || 
                    file.name.equals("DCIM", ignoreCase = true) ||
                    file.name.equals("Pictures", ignoreCase = true) ||
                    file.name.startsWith(".")) {
                    continue
                }
                scanForJunkFiles(file, list, depth + 1)
            } else {
                if (isJunkFile(file)) {
                    list.add(file)
                }
            }
        }
    }

    private fun isJunkFile(file: File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".tmp") || 
               name.endsWith(".temp") || 
               name.endsWith(".log") || 
               name.startsWith("thumb") || // classic thumb data
               name == "thumbs.db" ||
               name == ".ds_store"
    }

    // Deprecated/Unused standard cache methods
    suspend fun getCacheSize_Legacy(): Long = 0
    suspend fun clearCache_Legacy(): Boolean = false

    private fun getDirSize(dir: File): Long {
        var size: Long = 0
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) getDirSize(file) else file.length()
            }
        }
        return size
    }

    private fun deleteDirContents(dir: File) {
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                file.deleteRecursively()
            }
        }
    }

    private fun scanForEmptyFolders(directory: File, list: MutableList<File>, depth: Int) {
        if (depth > 3) return // Prevent deep scan for performance
        val files = directory.listFiles()
        if (files == null) return

        if (files.isEmpty() && directory.name != "Android") { // Don't delete Android system folder
            list.add(directory)
        } else {
            for (file in files) {
                if (file.isDirectory) {
                    scanForEmptyFolders(file, list, depth + 1)
                }
            }
        }
    }

    suspend fun getLargeFiles(thresholdBytes: Long = 100 * 1024 * 1024): List<FileModel> = withContext(Dispatchers.IO) {
        val files = mutableListOf<FileModel>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        val selection = "${MediaStore.Files.FileColumns.SIZE} > ?"
        val selectionArgs = arrayOf(thresholdBytes.toString())
        val sortOrder = "${MediaStore.Files.FileColumns.SIZE} DESC"
        
        val queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }
        
        context.contentResolver.query(queryUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
             files.addAll(mapCursorToFiles(cursor))
        }
        files
    }

    suspend fun getRecentFiles(limit: Int = 20): List<FileModel> = withContext(Dispatchers.IO) {
        val files = mutableListOf<FileModel>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE
        )

        // val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} != ${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE}"
        // Removing filter to include ALL file types (Docs, APKs, etc.)
        val selection: String? = null
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        val queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, null)
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Files.FileColumns.DATE_MODIFIED))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit * 10) // Fetch more to allow for filtering
            }
            context.contentResolver.query(queryUri, projection, queryArgs, null)?.use { cursor ->
                files.addAll(mapCursorToFiles(cursor))
            }
        } else {
            context.contentResolver.query(
                queryUri,
                projection,
                selection,
                null,
                "$sortOrder LIMIT ${limit * 10}" // Fetch more to allow for filtering
            )?.use { cursor ->
                files.addAll(mapCursorToFiles(cursor))
            }
        }
        
        // Filter out OTHERS and UNKNOWN types, then take the requested limit
        return@withContext files.filter { it.type != FileType.OTHERS && it.type != FileType.UNKNOWN }.take(limit)
    }

    suspend fun searchFiles(query: String): List<FileModel> = withContext(Dispatchers.IO) {
        val files = mutableListOf<FileModel>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE
        )

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        
        val queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        context.contentResolver.query(queryUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            files.addAll(mapCursorToFiles(cursor))
        }
        
        return@withContext files
    }

    suspend fun getFilesByCategory(category: FileType): List<FileModel> = withContext(Dispatchers.IO) {
        val files = mutableListOf<FileModel>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE
        )

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        
        val queryUri: Uri
        val selection: String?
        val selectionArgs: Array<String>?

        if (category == FileType.DOWNLOAD && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            queryUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
            selection = null
            selectionArgs = null
        } else {
            queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Files.getContentUri("external")
            }
            val (catSelection, catSelectionArgs) = getSelectionForCategory(category)
            selection = catSelection
            selectionArgs = catSelectionArgs
        }

        context.contentResolver.query(queryUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            files.addAll(mapCursorToFiles(cursor))
        }
        
        return@withContext files
    }
    
    private fun mapCursorToFiles(cursor: Cursor): List<FileModel> {
        val files = mutableListOf<FileModel>()
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val pathColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
        val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val name = cursor.getString(nameColumn) ?: "Unknown"
            val path = if(pathColumn != -1) cursor.getString(pathColumn) ?: "" else ""
            val size = cursor.getLong(sizeColumn)
            val date = cursor.getLong(dateColumn) * 1000 // Convert seconds to milliseconds
            val mimeType = cursor.getString(mimeColumn) ?: "*/*"
            
            files.add(FileModel(id, name, path, size, date, mimeType, determineFileType(mimeType, name), false, 0))
        }
        return files
    }

    private fun getSelectionForCategory(category: FileType): Pair<String, Array<String>?> {
        return when (category) {
            FileType.IMAGE -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
            FileType.VIDEO -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            FileType.AUDIO -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString())
            FileType.DOCUMENT -> {
                val mimeTypes = arrayOf("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "text/plain")
                val selection = mimeTypes.joinToString(" OR ") { "${MediaStore.Files.FileColumns.MIME_TYPE} = ?" }
                "($selection)" to mimeTypes
            }
            FileType.APK -> "${MediaStore.Files.FileColumns.DATA} LIKE '%.apk'" to null
            FileType.ARCHIVE -> {
                 val mimeTypes = arrayOf("application/zip", "application/x-rar-compressed", "application/x-7z-compressed")
                 val selection = mimeTypes.joinToString(" OR ") { "${MediaStore.Files.FileColumns.MIME_TYPE} = ?" }
                 "($selection)" to mimeTypes
            }
            FileType.DOWNLOAD -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                "${MediaStore.Files.FileColumns.DATA} LIKE ?" to arrayOf("%/Download/%")
            } else {
                "1=0" to null
            }
            FileType.OTHERS -> {
                // "Others" = NOT Image AND NOT Video AND NOT Audio
                // This is a simplification.
                val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} != ? AND ${MediaStore.Files.FileColumns.MEDIA_TYPE} != ? AND ${MediaStore.Files.FileColumns.MEDIA_TYPE} != ? AND ${MediaStore.Files.FileColumns.MEDIA_TYPE} != ?"
                val args = arrayOf(
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_NONE.toString()
                )
                selection to args
            }
            else -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} != ${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE}" to null
        }
    }

    private fun determineFileType(mimeType: String?, name: String): FileType {
        if (mimeType != null) {
            when {
                mimeType.startsWith("image/") -> return FileType.IMAGE
                mimeType.startsWith("video/") -> return FileType.VIDEO
                mimeType.startsWith("audio/") -> return FileType.AUDIO
                mimeType == "application/vnd.android.package-archive" -> return FileType.APK
                mimeType == "application/zip" || mimeType == "application/x-rar-compressed" || mimeType == "application/x-7z-compressed" -> return FileType.ARCHIVE
                mimeType.startsWith("application/") || mimeType.startsWith("text/") -> {
                     // Refine document check if needed, or allow it to fall through
                     if (name.endsWith(".apk", true)) return FileType.APK
                     return FileType.DOCUMENT
                }
            }
        }

        // Fallback to extension check
        return when {
            name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) || name.endsWith(".png", true) || name.endsWith(".gif", true) || name.endsWith(".bmp", true) || name.endsWith(".webp", true) -> FileType.IMAGE
            name.endsWith(".mp4", true) || name.endsWith(".mkv", true) || name.endsWith(".webm", true) || name.endsWith(".3gp", true) -> FileType.VIDEO
            name.endsWith(".mp3", true) || name.endsWith(".wav", true) || name.endsWith(".m4a", true) || name.endsWith(".ogg", true) || name.endsWith(".flac", true) -> FileType.AUDIO
            name.endsWith(".apk", true) -> FileType.APK
            name.endsWith(".zip", true) || name.endsWith(".rar", true) || name.endsWith(".7z", true) -> FileType.ARCHIVE
            name.endsWith(".pdf", true) || name.endsWith(".doc", true) || name.endsWith(".docx", true) || name.endsWith(".xls", true) || name.endsWith(".xlsx", true) || name.endsWith(".ppt", true) || name.endsWith(".pptx", true) || name.endsWith("txt", true) -> FileType.DOCUMENT
            else -> FileType.OTHERS
        }
    }

    private val archiveManager = ArchiveManager()

    suspend fun extractArchive(sourcePath: String, destinationPath: String): Boolean = withContext(Dispatchers.IO) {
        archiveManager.extractArchive(File(sourcePath), File(destinationPath))
    }

    @Suppress("DEPRECATION")
    suspend fun getStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        try {
            val path = Environment.getExternalStorageDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val freeBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - freeBytes

            val imageBytes = getCategorySize(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE)
            val videoBytes = getCategorySize(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
            val audioBytes = getCategorySize(MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO)
            val documentBytes = getDocumentSize()
            val appBytes = getAppSize()
            val archiveBytes = getArchiveSize()
            
            val mediaSum = imageBytes + videoBytes + audioBytes + documentBytes + appBytes + archiveBytes
            val otherBytes = if (usedBytes > mediaSum) usedBytes - mediaSum else 0L

            StorageInfo(totalBytes, freeBytes, usedBytes, imageBytes, videoBytes, audioBytes, documentBytes, appBytes, archiveBytes, otherBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            // Return empty/zero info on error
            StorageInfo(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        }
    }

    private fun getAppSize(): Long {
        var size: Long = 0
        try {
            val pm = context.packageManager
            val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                pm.getInstalledApplications(0)
            }
            
            // Check for Usage Access Permission (API 23+)
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
            val hasPermission = mode == android.app.AppOpsManager.MODE_ALLOWED

            if (hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Precise Calculation using StorageStatsManager (API 26+)
                val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                val userHandle = Process.myUserHandle()
                val storageUuid = android.os.storage.StorageManager.UUID_DEFAULT // Internal Storage

                installedApps.forEach { appInfo ->
                     // removed FLAG_SYSTEM check to include all apps
                     try {
                         val stats = storageStatsManager.queryStatsForPackage(storageUuid, appInfo.packageName, userHandle)
                         size += stats.appBytes + stats.dataBytes + stats.cacheBytes
                     } catch (e: Exception) {
                         // Fallback individually
                         val file = File(appInfo.sourceDir)
                         if (file.exists()) size += file.length()
                     }
                }
            } else {
                     // Fallback: APK Size only
                 installedApps.forEach { appInfo ->
                    // Include all apps, system or user
                    val file = File(appInfo.sourceDir)
                    if (file.exists()) {
                         size += file.length()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return size
    }

    private fun getArchiveSize(): Long {
        var size: Long = 0
        val projection = arrayOf("sum(${MediaStore.Files.FileColumns.SIZE})")
        val (selection, selectionArgs) = getSelectionForCategory(FileType.ARCHIVE)
        
        val queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        try {
            context.contentResolver.query(queryUri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    size = cursor.getLong(0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return size
    }

    private fun getDocumentSize(): Long {
        var size: Long = 0
        val projection = arrayOf("sum(${MediaStore.Files.FileColumns.SIZE})")
        
        // Expanded Document Types
        val mimeTypes = arrayOf(
            "application/pdf", 
            "application/msword", 
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // docx
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // xlsx
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation", // pptx
            "text/plain",
            "text/rtf",
            "text/html", // optional
            "application/epub+zip"
        )
        val selection = mimeTypes.joinToString(" OR ") { "${MediaStore.Files.FileColumns.MIME_TYPE} = ?" }
        
        val queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        try {
             // Pass mimeTypes as args for the ? placeholders
            context.contentResolver.query(queryUri, projection, selection, mimeTypes, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    size = cursor.getLong(0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return size
    }

    suspend fun renameFile(path: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            val newFile = File(file.parent, newName)
            if (newFile.exists()) return@withContext false
            return@withContext file.renameTo(newFile)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun copyFile(sourcePath: String, destPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(sourcePath)
            val destFile = File(destPath, sourceFile.name)
            if (destFile.exists()) return@withContext false // Or handle overwrite/rename
            
            sourceFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun moveFile(sourcePath: String, destPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(sourcePath)
            val destFile = File(destPath, sourceFile.name)
            if (destFile.exists()) return@withContext false
            
            // Try atomic move first
            if (sourceFile.renameTo(destFile)) {
                return@withContext true
            }

            // Fallback to Copy-Delete
            if (copyFile(sourcePath, destPath)) {
                // Determine if we should delete strictly or via repository.
                // For a move operation, we simply want the source gone. 
                // bypassing TrashManager to avoid cluttering trash with moved files.
                return@withContext sourceFile.deleteRecursively()
            }
            return@withContext false

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun getAverageDailyUsageBytes(): Long = withContext(Dispatchers.IO) {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        val projection = arrayOf("sum(${MediaStore.Files.FileColumns.SIZE})")
        val selection = "${MediaStore.Files.FileColumns.DATE_MODIFIED} > ?"
        val selectionArgs = arrayOf((thirtyDaysAgo / 1000).toString())
        val queryUri = MediaStore.Files.getContentUri("external")
        var recentUsageBytes: Long = 0
        try {
            context.contentResolver.query(queryUri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    recentUsageBytes = cursor.getLong(0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (recentUsageBytes > 0) recentUsageBytes / 30 else 0
    }


    fun hasUsageAccess(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getCategorySize(mediaType: Int): Long {
        var size: Long = 0
        val projection = arrayOf("sum(${MediaStore.Files.FileColumns.SIZE})")
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        val selectionArgs = arrayOf(mediaType.toString())
        
        val queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        try {
            context.contentResolver.query(queryUri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    size = cursor.getLong(0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return size
    }
}
