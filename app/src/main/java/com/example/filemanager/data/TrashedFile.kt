package com.example.filemanager.data

data class TrashedFile(
    val id: Long,
    val originalPath: String,
    val trashPath: String,
    val name: String,
    val size: Long,
    val dateDeleted: Long,
    val type: FileType,
    val preview: String? = null // For image/video previews
)
