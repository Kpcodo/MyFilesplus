package com.mfp.filemanager.data.archive

import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class ArchiveManager {

    suspend fun extractArchive(sourceFile: File, destinationDir: File): Boolean {
        return try {
            val bis = BufferedInputStream(FileInputStream(sourceFile))
            val ais: ArchiveInputStream<*> = ArchiveStreamFactory().createArchiveInputStream(bis)

            extractEntry(ais, destinationDir)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun extractEntry(ais: ArchiveInputStream<*>, destinationDir: File) {
        ais.use { input ->
            var entry: ArchiveEntry? = input.nextEntry
            while (entry != null) {
                val outputFile = File(destinationDir, entry.name)
                
                // Security check for Zip Slip vulnerability
                if (!outputFile.canonicalPath.startsWith(destinationDir.canonicalPath + File.separator)) {
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
                entry = input.nextEntry
            }
        }
    }
}
