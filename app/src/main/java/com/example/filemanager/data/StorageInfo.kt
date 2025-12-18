package com.example.filemanager.data

data class StorageInfo(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val imageBytes: Long,
    val videoBytes: Long,
    val audioBytes: Long,
    val documentBytes: Long, // Add this line
    val otherBytes: Long // usedBytes - (image + video + audio)
)
