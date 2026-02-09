package com.mfp.filemanager.data

import com.mfp.filemanager.data.clipboard.ClipboardOperation
import com.mfp.filemanager.data.clipboard.FileClipboard
import com.mfp.filemanager.data.clipboard.FileTransferProgress
import com.mfp.filemanager.data.clipboard.TransferStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

object FileOperationManager {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var transferJob: Job? = null

    private val _activeOperationType = MutableStateFlow(OperationType.NONE)
    val activeOperationType: StateFlow<OperationType> = _activeOperationType.asStateFlow()

    private val _clipboard = MutableStateFlow<FileClipboard?>(null)
    val clipboard: StateFlow<FileClipboard?> = _clipboard.asStateFlow()

    private val _progress = MutableStateFlow<FileTransferProgress?>(null)
    val progress: StateFlow<FileTransferProgress?> = _progress.asStateFlow()

    fun addToClipboard(items: List<FileModel>, operation: ClipboardOperation, sourcePath: String) {
        _clipboard.value = FileClipboard(items, operation, sourcePath)
    }

    fun clearClipboard() {
        _clipboard.value = null
    }

    fun cancelOperation() {
        transferJob?.cancel()
        _progress.value = _progress.value?.copy(status = TransferStatus.CANCELLED)
    }

    fun paste(destinationPath: String, repository: FileRepository) {
        val currentClipboard = _clipboard.value ?: return
        val items = currentClipboard.items
        if (items.isEmpty()) return

        transferJob?.cancel()
        transferJob = scope.launch {
            val totalFiles = items.size
            var completedFiles = 0
            var totalBytes = 0L
            var transferredBytes = 0L

            // Calculate total size for byte-level progress
            // Note: This might take time for large folders, but it's necessary for accurate progress bar.
            // We can do it on the fly or pre-cal.
            try {
                 totalBytes = repository.getTotalSize(items.map { it.path })
            } catch (e: Exception) {
                 e.printStackTrace()
                 // Fallback to sum of file sizes we know
                 totalBytes = items.sumOf { it.size }
            }

            _progress.value = FileTransferProgress(
                totalBytes = totalBytes,
                transferredBytes = 0,
                totalFiles = totalFiles,
                completedFiles = 0,
                currentFileName = "Preparing...",
                status = TransferStatus.STARTING
            )
            
            // Allow UI to pick up the STARTING state
            kotlinx.coroutines.delay(100)

            val op = currentClipboard.operation

            items.forEachIndexed { index, fileModel ->
                _progress.value = _progress.value?.copy(
                    currentFileName = fileModel.name,
                    status = TransferStatus.IN_PROGRESS
                )

                val success = try {
                    if (op == ClipboardOperation.COPY) {
                        repository.copyFile(fileModel.path, destinationPath) { bytes, total ->
                            // Update global bytes
                            // Note: bytes is current file progress.
                            // We need to track accumulated bytes from previous files.
                            // However, copyFile calls back with absolute bytesCopied for THAT file.
                            
                            val previousBytes = transferredBytes // This needs to be carefully managed
                            // Actually, simpler approach:
                            // We can't easily get incremental updates from the callback without knowing the delta.
                            // But repository.copyFile callback is (bytesCopiedSoFar, totalFileSize).
                            
                            // To map to global, we effectively need to know how much we 'added' since last update.
                            // Or, we can just *re-calculate* based on completed files? No.
                            
                            // Let's use a local variable for 'currentFileBytesCopied'
                        }
                        // Refactor: We need repository.copyFile to accept a callback that gives us absolute progress for that file.
                        // We will sum it up.
                        copyFileWrapper(repository, fileModel.path, destinationPath) { currentFileBytes ->
                             val globalTransferred = transferredBytes + currentFileBytes
                             _progress.value = _progress.value?.copy(
                                 transferredBytes = globalTransferred
                             )
                        }
                    } else {
                        moveFileWrapper(repository, fileModel.path, destinationPath) { currentFileBytes ->
                             val globalTransferred = transferredBytes + currentFileBytes
                             _progress.value = _progress.value?.copy(
                                 transferredBytes = globalTransferred
                             )
                        }
                    }
                } catch (e: Exception) {
                    false
                }

                if (success) {
                    completedFiles++
                    
                    // Add the size of the completed file to transferredBytes BASE
                    // So that the next file starts counting from there.
                    // Wait, the wrapper callback provides 'currentFileBytes'.
                    // If we just add it to 'transferredBytes', we need to reset 'transferredBytes' to include this file's full size
                    // and start next one from 0.
                    
                    // Correct Logic:
                    // accumulatedBytes += fileModel.size (or actual copied size)
                    
                    // Actually, getting accurate size of copied directory is hard.
                    // But for files it is easy.
                    
                    val fileSize = if (File(fileModel.path).isDirectory) repository.calculateDirectorySize(File(fileModel.path)) else fileModel.size
                    transferredBytes += fileSize
                    
                    _progress.value = _progress.value?.copy(
                        completedFiles = completedFiles,
                        transferredBytes = transferredBytes
                    )
                }
            }

            _progress.value = _progress.value?.copy(
                status = TransferStatus.COMPLETED,
                transferredBytes = totalBytes,
                completedFiles = totalFiles
            )

            if (op == ClipboardOperation.MOVE) {
                clearClipboard()
            }
        }
    }

    private suspend fun copyFileWrapper(
        repository: FileRepository, 
        source: String, 
        dest: String, 
        onProgress: (Long) -> Unit
    ): Boolean {
        return repository.copyFile(source, dest) { current, total ->
            onProgress(current)
        }
    }

    private suspend fun moveFileWrapper(
        repository: FileRepository, 
        source: String, 
        dest: String, 
        onProgress: (Long) -> Unit
    ): Boolean {
        return repository.moveFile(source, dest) { current, total ->
            onProgress(current)
        }
    }

    fun startDeleteOperation(totalFiles: Int) {
        _activeOperationType.value = OperationType.TRASH
        _progress.value = FileTransferProgress(
            totalBytes = 0,
            transferredBytes = 0,
            totalFiles = totalFiles,
            completedFiles = 0,
            currentFileName = "Deleting...",
            status = TransferStatus.STARTING
        )
    }

    fun updateDeleteProgress(processedFiles: Int, totalFiles: Int) {
        _progress.value = _progress.value?.copy(
            completedFiles = processedFiles,
            totalFiles = totalFiles,
            status = TransferStatus.IN_PROGRESS
        )
    }

    fun startRestoreOperation(totalFiles: Int) {
        _activeOperationType.value = OperationType.RESTORE
        _progress.value = FileTransferProgress(
            totalBytes = 0,
            transferredBytes = 0,
            totalFiles = totalFiles,
            completedFiles = 0,
            currentFileName = "Restoring...",
            status = TransferStatus.STARTING
        )
    }

    fun updateRestoreProgress(processedFiles: Int, totalFiles: Int) {
        _progress.value = _progress.value?.copy(
            completedFiles = processedFiles,
            totalFiles = totalFiles,
            status = TransferStatus.IN_PROGRESS
        )
    }

    fun startPermDeleteOperation(totalFiles: Int) {
        _activeOperationType.value = OperationType.DELETE
        _progress.value = FileTransferProgress(
            totalBytes = 0,
            transferredBytes = 0,
            totalFiles = totalFiles,
            completedFiles = 0,
            currentFileName = "Deleting Permanently...",
            status = TransferStatus.STARTING
        )
    }

    fun updatePermDeleteProgress(processedFiles: Int, totalFiles: Int) {
        _progress.value = _progress.value?.copy(
            completedFiles = processedFiles,
            totalFiles = totalFiles,
            status = TransferStatus.IN_PROGRESS
        )
    }

    fun finishOperation() {
        _progress.value = _progress.value?.copy(
            status = TransferStatus.COMPLETED
        )
        // Reset type after a delay or immediately? 
        // Logic in MainActivity handles "COMPLETED" status to hide UI.
        // We set type to NONE after a short delay so UI has time to read "COMPLETED" state with correct Icon?
        // Actually MainActivity reads type on every progress update.
        // If we set NONE immediately, it might flash default icon.
        // But MainActivity maps Clipboard -> Type.
        // If we use activeOperationType, we should keep it until next start or explicitly clear.
        
        scope.launch {
            kotlinx.coroutines.delay(500)
            _activeOperationType.value = OperationType.NONE
        }
    }
}
