package com.mfp.filemanager.data.trash
import com.mfp.filemanager.data.FileType

import android.content.Context
import android.media.MediaScannerConnection
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TrashManager(private val context: Context) {
    
    private val processingMutex = Mutex()

    private val trashDir: File by lazy {
        File(context.getExternalFilesDir(null), ".trash").apply {
            if (!exists()) mkdirs()
        }
    }

    private val metadataFile: File by lazy {
        File(context.getExternalFilesDir(null), "trash_metadata.json")
    }

    private val gson = Gson()

    suspend fun moveToTrash(file: File): Boolean = moveToTrashBatch(listOf(file))

    suspend fun moveToTrashBatch(files: List<File>, onProgress: ((Float) -> Unit)? = null): Boolean = withContext(Dispatchers.IO) {
        val successfulItems = mutableListOf<TrashedFile>()
        val pathsToScan = mutableListOf<String>()
        val overallResults = mutableListOf<Boolean>()

        val total = files.size
        files.forEachIndexed { index, file ->
            val result = moveToTrashInternal(file)
            if (result != null) {
                successfulItems.add(result)
                pathsToScan.add(file.absolutePath)
                overallResults.add(true)
            } else {
                overallResults.add(false)
            }
            onProgress?.invoke((index + 1).toFloat() / total)
        }

        if (successfulItems.isNotEmpty()) {
            processingMutex.withLock {
                val currentMetadata = readMetadata().toMutableList()
                currentMetadata.addAll(successfulItems)
                writeMetadata(currentMetadata)
            }
            scanFiles(pathsToScan)
        }
        
        return@withContext overallResults.all { it }
    }

    private fun moveToTrashInternal(file: File): TrashedFile? {
        if (!file.exists()) return null

        val trashedName = "${java.util.UUID.randomUUID()}_${file.name}"
        val trashedFile = File(trashDir, trashedName)

        val fileType = determineFileType(file.name)
        val fileSize = if (file.isDirectory) calculateSize(file) else file.length()

        try {
            if (file.renameTo(trashedFile)) {
                return TrashedFile(
                    id = System.nanoTime(),
                    name = file.name,
                    originalPath = file.absolutePath,
                    trashPath = trashedFile.absolutePath,
                    size = fileSize,
                    dateDeleted = System.currentTimeMillis(),
                    type = fileType,
                    preview = if (fileType == FileType.IMAGE || fileType == FileType.VIDEO) trashedFile.absolutePath else null
                )
            } else {
                if (file.isDirectory) {
                    file.copyRecursively(trashedFile, overwrite = true)
                } else {
                    file.copyTo(trashedFile, overwrite = true)
                }
                
                val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
                if (deleted) {
                    return TrashedFile(
                        id = System.nanoTime(),
                        name = file.name,
                        originalPath = file.absolutePath,
                        trashPath = trashedFile.absolutePath,
                        size = fileSize,
                        dateDeleted = System.currentTimeMillis(),
                        type = fileType,
                        preview = if (fileType == FileType.IMAGE || fileType == FileType.VIDEO) trashedFile.absolutePath else null
                    )
                } else {
                    if (!file.isDirectory && deleteViaContentResolver(file)) {
                         return TrashedFile(
                            id = System.nanoTime(),
                            name = file.name,
                            originalPath = file.absolutePath,
                            trashPath = trashedFile.absolutePath,
                            size = fileSize,
                            dateDeleted = System.currentTimeMillis(),
                            type = fileType,
                            preview = if (fileType == FileType.IMAGE || fileType == FileType.VIDEO) trashedFile.absolutePath else null
                        )
                    }
                    if (trashedFile.exists()) trashedFile.deleteRecursively()
                    return null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (trashedFile.exists()) trashedFile.deleteRecursively()
            return null
        }
    }

    private fun calculateSize(file: File): Long {
        if (!file.isDirectory) return file.length()
        return file.listFiles()?.sumOf { calculateSize(it) } ?: 0L
    }

    suspend fun restoreFromTrash(trashedFile: TrashedFile): Boolean = restoreBatch(listOf(trashedFile))

    suspend fun restoreBatch(trashedFiles: List<TrashedFile>, onProgress: ((Float) -> Unit)? = null): Boolean = withContext(Dispatchers.IO) {
        processingMutex.withLock {
            val currentMetadata = readMetadata().toMutableList()
            val pathsToScan = mutableListOf<String>()
            val results = mutableListOf<Boolean>()
    
            val total = trashedFiles.size
            trashedFiles.forEachIndexed { index, trashedFile ->
                val fileInTrash = File(trashedFile.trashPath)
                val originalFile = File(trashedFile.originalPath)
    
                if (!fileInTrash.exists()) {
                    currentMetadata.removeAll { it.id == trashedFile.id }
                    results.add(false)
                    return@forEachIndexed
                }
    
                val parentDir = originalFile.parentFile
                if (parentDir != null && !parentDir.exists()) parentDir.mkdirs()
    
                try {
                    if (fileInTrash.renameTo(originalFile)) {
                        pathsToScan.add(originalFile.absolutePath)
                        currentMetadata.removeAll { it.id == trashedFile.id }
                        results.add(true)
                    } else {
                        // Fallback
                        val success = if (fileInTrash.isDirectory) {
                            fileInTrash.copyRecursively(originalFile, overwrite = true)
                        } else {
                            fileInTrash.copyTo(originalFile, overwrite = true)
                            true
                        }
                        if (success && fileInTrash.deleteRecursively()) {
                            pathsToScan.add(originalFile.absolutePath)
                            currentMetadata.removeAll { it.id == trashedFile.id }
                            results.add(true)
                        } else {
                            results.add(false)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    results.add(false)
                }
                onProgress?.invoke((index + 1).toFloat() / total)
            }
    
            if (pathsToScan.isNotEmpty() || results.any { it }) {
                writeMetadata(currentMetadata)
                scanFiles(pathsToScan)
            }
            return@withLock results.all { it }
        }
    }

    suspend fun deletePermanently(trashedFile: TrashedFile): Boolean = withContext(Dispatchers.IO) {
        processingMutex.withLock {
            val fileInTrash = File(trashedFile.trashPath)
            if (fileInTrash.exists()) {
                if (fileInTrash.deleteRecursively()) {
                    removeMetadata(trashedFile)
                    return@withLock true
                }
            } else {
                removeMetadata(trashedFile)
                return@withLock true
            }
            return@withLock false
        }
    }
    
    suspend fun emptyTrash(): Boolean = withContext(Dispatchers.IO) {
        processingMutex.withLock {
            try {
                trashDir.listFiles()?.forEach { it.deleteRecursively() }
                if (metadataFile.exists()) {
                    metadataFile.delete()
                }
                return@withLock true
            } catch (e: Exception) {
                e.printStackTrace()
                return@withLock false
            }
        }
    }

    suspend fun getTrashedFiles(): List<TrashedFile> = withContext(Dispatchers.IO) {
        processingMutex.withLock {
            readMetadata()
        }
    }

    private fun addMetadata(item: TrashedFile) {
        val list = readMetadata().toMutableList()
        list.add(item)
        writeMetadata(list)
    }

    private fun removeMetadata(item: TrashedFile) {
        val list = readMetadata().toMutableList()
        list.removeAll { it.id == item.id }
        writeMetadata(list)
    }

    private fun readMetadata(): List<TrashedFile> {
        if (!metadataFile.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<TrashedFile>>() {}.type
            FileReader(metadataFile).use { reader ->
                gson.fromJson(reader, type) ?: emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun writeMetadata(list: List<TrashedFile>) {
        try {
            FileWriter(metadataFile).use { writer ->
                gson.toJson(list, writer)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    suspend fun restoreAll(): Boolean {
        return restoreBatch(getTrashedFiles())
    }

    suspend fun cleanupExpiredFiles(retentionDays: Int): Int = withContext(Dispatchers.IO) {
        if (retentionDays < 0) return@withContext 0 // Feature disabled
        
        val retentionMillis = retentionDays * 24 * 60 * 60 * 1000L
        val cutoffTime = System.currentTimeMillis() - retentionMillis
        val trashedFiles = getTrashedFiles()
        var deletedCount = 0
        
        for (file in trashedFiles) {
            if (file.dateDeleted < cutoffTime) {
                if (deletePermanently(file)) {
                    deletedCount++
                }
            }
        }
        return@withContext deletedCount
    }

    private fun deleteViaContentResolver(file: File): Boolean {
        return try {
            val contentResolver = context.contentResolver
            val externalUri = android.provider.MediaStore.Files.getContentUri("external")
            val selection = "${android.provider.MediaStore.Files.FileColumns.DATA} = ?"
            val selectionArgs = arrayOf(file.absolutePath)
            
            val rowsDeleted = contentResolver.delete(externalUri, selection, selectionArgs)
            rowsDeleted > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun determineFileType(name: String): FileType {
        return when {
            name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) || name.endsWith(".png", true) || name.endsWith(".gif", true) || name.endsWith(".bmp", true) || name.endsWith(".webp", true) -> FileType.IMAGE
            name.endsWith(".mp4", true) || name.endsWith(".mkv", true) || name.endsWith(".webm", true) || name.endsWith(".3gp", true) -> FileType.VIDEO
            name.endsWith(".mp3", true) || name.endsWith(".wav", true) || name.endsWith(".m4a", true) || name.endsWith(".ogg", true) || name.endsWith(".flac", true) -> FileType.AUDIO
            name.endsWith(".apk", true) -> FileType.APK
            name.endsWith(".zip", true) || name.endsWith(".rar", true) || name.endsWith(".7z", true) -> FileType.ARCHIVE
            name.endsWith(".pdf", true) || name.endsWith(".doc", true) || name.endsWith(".docx", true) || name.endsWith(".xls", true) || name.endsWith(".xlsx", true) || name.endsWith(".ppt", true) || name.endsWith(".pptx", true) || name.endsWith(".txt", true) -> FileType.DOCUMENT
            else -> FileType.UNKNOWN
        }
    }

    private fun scanFile(path: String) {
        scanFiles(listOf(path))
    }

    private fun scanFiles(paths: List<String>) {
        if (paths.isEmpty()) return
        try {
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
