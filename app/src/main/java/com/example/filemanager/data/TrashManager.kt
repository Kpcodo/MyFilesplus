package com.example.filemanager.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader
import java.io.FileWriter

class TrashManager(private val context: Context) {

    private val trashDir: File by lazy {
        File(context.getExternalFilesDir(null), ".trash").apply {
            if (!exists()) mkdirs()
        }
    }

    private val metadataFile: File by lazy {
        File(context.getExternalFilesDir(null), "trash_metadata.json")
    }

    private val gson = Gson()

    suspend fun moveToTrash(file: File): Boolean = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext false

        val trashedName = "${System.currentTimeMillis()}_${file.name}"
        val trashedFile = File(trashDir, trashedName)

        try {
            if (file.renameTo(trashedFile)) {
                // Record metadata
                val trashedMetadata = TrashedFile(
                    id = System.currentTimeMillis(),
                    originalPath = file.absolutePath,
                    trashPath = trashedFile.absolutePath,
                    name = file.name,
                    size = trashedFile.length(),
                    dateDeleted = System.currentTimeMillis(),
                    type = determineFileType(file.name),
                    preview = if (
                        determineFileType(file.name) == FileType.IMAGE ||
                        determineFileType(file.name) == FileType.VIDEO
                    ) trashedFile.absolutePath else null
                )
                addMetadata(trashedMetadata)
                return@withContext true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext false
    }

    suspend fun restoreFromTrash(trashedFile: TrashedFile): Boolean = withContext(Dispatchers.IO) {
        val fileInTrash = File(trashedFile.trashPath)
        val originalFile = File(trashedFile.originalPath)

        if (!fileInTrash.exists()) return@withContext false

        // Ensure parent directory exists
        val parentDir = originalFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }

        if (fileInTrash.renameTo(originalFile)) {
            removeMetadata(trashedFile)
            return@withContext true
        }
        return@withContext false
    }

    suspend fun deletePermanently(trashedFile: TrashedFile): Boolean = withContext(Dispatchers.IO) {
        val fileInTrash = File(trashedFile.trashPath)
        if (fileInTrash.exists()) {
            if (fileInTrash.delete()) {
                removeMetadata(trashedFile)
                return@withContext true
            }
        } else {
            // File might be missing but we should clear metadata
            removeMetadata(trashedFile)
            return@withContext true
        }
        return@withContext false
    }
    
    suspend fun emptyTrash(): Boolean = withContext(Dispatchers.IO) {
        try {
            trashDir.listFiles()?.forEach { it.delete() }
            if (metadataFile.exists()) {
                metadataFile.delete()
            }
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun getTrashedFiles(): List<TrashedFile> = withContext(Dispatchers.IO) {
        readMetadata()
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
    
    suspend fun restoreAll(): Boolean = withContext(Dispatchers.IO) {
        val trashedFiles = getTrashedFiles()
        var allSuccess = true
        for (file in trashedFiles) {
            if (!restoreFromTrash(file)) {
                allSuccess = false
            }
        }
        return@withContext allSuccess
    }

    // Copy of helper from Repository, ideally should be in common utils
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
}
