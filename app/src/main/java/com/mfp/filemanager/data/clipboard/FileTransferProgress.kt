package com.mfp.filemanager.data.clipboard

data class FileTransferProgress(
    val totalBytes: Long,
    val transferredBytes: Long,
    val totalFiles: Int,
    val completedFiles: Int,
    val currentFileName: String,
    val status: TransferStatus = TransferStatus.IN_PROGRESS
)

enum class TransferStatus {
    STARTING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}
