package com.mfp.filemanager.data.archive

import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ArchiveManager {

    suspend fun extractArchive(sourceFile: File, destinationDir: File, onProgress: ((Float) -> Unit)? = null): Boolean = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) return@withContext false
        
        // Create destination directory if it doesn't exist
        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            return@withContext false
        }

        var success = false
        try {
            val bis = BufferedInputStream(FileInputStream(sourceFile))
            val ais: ArchiveInputStream<*> = ArchiveStreamFactory().createArchiveInputStream(bis)

            success = extractEntry(ais, destinationDir, onProgress)
        } catch (e: Exception) {
            e.printStackTrace()
            success = false
        } finally {
            if (!success) {
                // Optional: Cleanup partial extraction? 
                // Only if we created the destinationDir just now or it's a specific subfolder.
                // For safety, we only cleanup if we are sure.
            }
        }
        return@withContext success
    }

    private fun extractEntry(ais: ArchiveInputStream<*>, destinationDir: File, onProgress: ((Float) -> Unit)?): Boolean {
        return ais.use { input ->
            var entry: ArchiveEntry? = input.nextEntry
            var entriesProcessed = 0
            // We don't know the total entries without scanning twice, but we can report processed count.
            
            while (entry != null) {
                val outputFile = File(destinationDir, entry.name)
                
                // Security check for Zip Slip vulnerability
                val canonicalDestinationPath = destinationDir.canonicalPath
                val canonicalOutputPath = outputFile.canonicalPath
                if (!canonicalOutputPath.startsWith(canonicalDestinationPath + File.separator) && canonicalOutputPath != canonicalDestinationPath) {
                    throw SecurityException("Zip Path Traversal Attempt: " + entry.name)
                }

                if (entry.isDirectory) {
                    if (!outputFile.isDirectory && !outputFile.mkdirs()) {
                        throw java.io.IOException("Failed to create directory $outputFile")
                    }
                } else {
                    val parent = outputFile.parentFile
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw java.io.IOException("Failed to create directory $parent")
                    }
                    
                    FileOutputStream(outputFile).buffered().use { ops ->
                        val buffer = ByteArray(8192)
                        var len: Int
                        while (input.read(buffer).also { len = it } != -1) {
                            ops.write(buffer, 0, len)
                        }
                    }
                }
                entriesProcessed++
                // If we can't get total, we can't easily do a Float [0,1] progress without pre-scanning.
                // For now, simple progress pulse.
                onProgress?.invoke(0.5f) 
                
                entry = input.nextEntry
            }
            true
        }
    }
}
