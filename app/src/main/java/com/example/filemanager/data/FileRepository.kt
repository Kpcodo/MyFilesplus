package com.example.filemanager.data

import android.content.ContentResolver
import android.content.Context
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileRepository(private val context: Context) {

    suspend fun getFilesFromPath(path: String): List<FileModel> = withContext(Dispatchers.IO) {
        val rootPath = java.nio.file.Paths.get(path)
        if (!java.nio.file.Files.exists(rootPath) || !java.nio.file.Files.isDirectory(rootPath)) {
            return@withContext emptyList()
        }

        val files = mutableListOf<FileModel>()
        try {
            java.nio.file.Files.newDirectoryStream(rootPath).use { stream ->
                for (entry in stream) {
                    val file = entry.toFile() // Minimal usage for compatibility
                    val isDir = java.nio.file.Files.isDirectory(entry)
                    val mimeType = if (!isDir) context.contentResolver.getType(Uri.fromFile(file)) else null
                    
                    // Optimization: Use Files.list for counting, but cap it or catch exceptions
                    val itemCount = if (isDir) {
                        try {
                            java.nio.file.Files.list(entry).count().toInt() 
                        } catch (e: Exception) { 0 }
                    } else 0
                    
                    // Read attributes efficiently
                    val attrs = java.nio.file.Files.readAttributes(entry, java.nio.file.attribute.BasicFileAttributes::class.java)
                    
                    files.add(FileModel(
                        id = file.hashCode().toLong(),
                        name = file.name,
                        path = file.path,
                        size = if (!isDir) attrs.size() else 0,
                        dateModified = attrs.lastModifiedTime().toMillis(),
                        mimeType = mimeType,
                        type = determineFileType(mimeType, file.name),
                        isDirectory = isDir,
                        itemCount = itemCount
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return@withContext files.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
    }
    


    suspend fun getAverageDailyUsageBytes(): Long = withContext(Dispatchers.IO) {
        // Calculate usage in the last 30 days
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        
        val projection = arrayOf("sum(${MediaStore.Files.FileColumns.SIZE})")
        val selection = "${MediaStore.Files.FileColumns.DATE_MODIFIED} > ?"
        val selectionArgs = arrayOf((thirtyDaysAgo / 1000).toString()) // Date modified is in seconds
        
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
            return@withContext 0L
        }
        
        if (recentUsageBytes <= 0) return@withContext 0L
        return@withContext (recentUsageBytes / 30.0).toLong()
    }

    suspend fun calculateStorageForecast(): String = withContext(Dispatchers.IO) {
        val freeBytes = Environment.getExternalStorageDirectory().freeSpace
        val dailyRate = getAverageDailyUsageBytes()
        
        if (dailyRate <= 0) return@withContext "Stable"

        val daysUntilFull = (freeBytes / dailyRate)

        return@withContext when {
            daysUntilFull < 1 -> "< 1 day"
            daysUntilFull < 30 -> "Full in ~$daysUntilFull days"
            daysUntilFull < 365 -> "Full in ~${daysUntilFull / 30} mo"
            else -> "> 1 yr left"
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

    suspend fun deleteFilePermanently(trashedFile: TrashedFile): Boolean = trashManager.deletePermanently(trashedFile)

    suspend fun emptyTrash(): Boolean = trashManager.emptyTrash()
    
    suspend fun getTrashSize(): Long = trashManager.getTrashedFiles().sumOf { it.size }

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

    private fun scanForEmptyFolders(directory: File, list: MutableList<File>, depth: Int) {
        if (depth > 3) return // Prevent deep scan for performance
        val dirPath = directory.toPath()
        
        // Safety check for Android system folders
        if (directory.name == "Android") return

        try {
            var hasFiles = false
            // Use NIO DirectoryStream to avoid loading all files into memory array
            java.nio.file.Files.newDirectoryStream(dirPath).use { stream ->
                for (entry in stream) {
                    hasFiles = true
                    if (java.nio.file.Files.isDirectory(entry)) {
                        scanForEmptyFolders(entry.toFile(), list, depth + 1)
                    }
                    // If we find files (non-directories), we mark as hasFiles=true (already done)
                    // We continue scanning subdirectories to find nested empty folders, 
                    // but WE DO NOT return early if we want to find nested empty folders?
                    // Actually, if we just want "Empty Folders", a folder containing other folders is NOT empty itself.
                    // But the logic usually is: Find ALL folders that are empty.
                    // So we continue iterating.
                }
            }
            
            // If loop finish and hasFiles is false -> It's truly empty
            if (!hasFiles) {
                list.add(directory)
            }
        } catch (e: Exception) {
            // Permission denied or other error
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
        
        val queryUri = MediaStore.Files.getContentUri("external")
        
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
                 val selection = mimeTypes.joinToString(" OR ") { "${MediaStore.Files.FileColumns.MIME_TYPE} = ?" } + " OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.zip' OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.rar' OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.7z'"
                 selection to null
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
            name.endsWith(".pdf", true) || name.endsWith(".doc", true) || name.endsWith(".docx", true) || name.endsWith(".xls", true) || name.endsWith(".xlsx", true) || name.endsWith(".ppt", true) || name.endsWith(".pptx", true) || name.endsWith(".txt", true) -> FileType.DOCUMENT
            else -> FileType.OTHERS
        }
    }

    suspend fun extractZip(sourcePath: String, destinationPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val inputStream = FileInputStream(sourcePath)
            val zipInputStream = ZipInputStream(BufferedInputStream(inputStream))
            var entry: ZipEntry?

            val destDir = File(destinationPath)
            if (!destDir.exists()) destDir.mkdirs()

            while (zipInputStream.nextEntry.also { entry = it } != null) {
                val currentEntry = entry ?: continue
                val file = File(destDir, currentEntry.name)

                val canonicalPath = file.canonicalPath
                if (!canonicalPath.startsWith(destDir.canonicalPath + File.separator)) {
                    throw SecurityException("Zip Path Traversal Attempt: " + currentEntry.name)
                }

                val dir = if (currentEntry.isDirectory) file else file.parentFile
                if (dir != null && !dir.isDirectory && !dir.mkdirs()) {
                    throw java.io.FileNotFoundException("Failed to ensure directory: " + dir.absolutePath)
                }
                if (currentEntry.isDirectory) continue

                val fileOutputStream = FileOutputStream(file)
                val bufferedOutputStream = BufferedOutputStream(fileOutputStream)
                val buffer = ByteArray(1024)
                var count: Int
                while (zipInputStream.read(buffer).also { count = it } != -1) {
                    bufferedOutputStream.write(buffer, 0, count)
                }
                bufferedOutputStream.close()
                fileOutputStream.close()
            }
            zipInputStream.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
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
        
        val mediaSum = imageBytes + videoBytes + audioBytes + documentBytes
        val otherBytes = if (usedBytes > mediaSum) usedBytes - mediaSum else 0L

        StorageInfo(totalBytes, freeBytes, usedBytes, imageBytes, videoBytes, audioBytes, documentBytes, otherBytes)
    }

    private fun getDocumentSize(): Long {
        var size: Long = 0
        val projection = arrayOf("sum(${MediaStore.Files.FileColumns.SIZE})")
        
        val mimeTypes = arrayOf("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "text/plain")
        val selection = mimeTypes.joinToString(" OR ") { "${MediaStore.Files.FileColumns.MIME_TYPE} = ?" }
        
        val queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        try {
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
