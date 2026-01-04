package com.mfp.filemanager.data

import java.util.Date

enum class FileType {
    IMAGE, VIDEO, AUDIO, DOCUMENT, APK, DOWNLOAD, ARCHIVE, OTHERS, UNKNOWN
}

data class FileModel(
    val id: Long,
    val name: String,
    val path: String,
    val size: Long,
    val dateModified: Long,
    val mimeType: String?,
    val type: FileType,
    val isDirectory: Boolean,
    val itemCount: Int = 0
) {
    val date: Date get() = Date(dateModified)
}
