package com.mfp.filemanager.data

enum class OperationType {
    NONE, TRASH, RESTORE, EXTRACT, DELETE, COPY, MOVE
}

data class OperationStatus(
    val isRunning: Boolean = false,
    val type: OperationType = OperationType.NONE,
    val progress: Float = 0f,
    val processedCount: Int = 0,
    val totalCount: Int = 0
)
