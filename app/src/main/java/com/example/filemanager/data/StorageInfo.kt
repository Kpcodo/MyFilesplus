package com.example.filemanager.data

data class StorageInfo(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val imageBytes: Long,
    val videoBytes: Long,
    val audioBytes: Long,
    val documentBytes: Long,
    val appBytes: Long,
    val archiveBytes: Long,
    val otherBytes: Long // usedBytes - (image + video + audio + docs + apps)
)
