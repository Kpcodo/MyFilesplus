package com.mfp.filemanager.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mfp.filemanager.data.clipboard.ClipboardOperation
import com.mfp.filemanager.data.FileModel
import com.mfp.filemanager.data.FileRepository
import com.mfp.filemanager.data.FileType
import com.mfp.filemanager.data.FileUtils
import com.mfp.filemanager.data.SettingsRepository
import com.mfp.filemanager.data.StorageInfo
import com.mfp.filemanager.data.StorageVolumeInfo
import com.mfp.filemanager.data.trash.TrashedFile
import com.mfp.filemanager.ui.SortType
import com.mfp.filemanager.ui.SortOrder
import com.mfp.filemanager.ui.ViewType
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.io.File


class HomeViewModel(
    private val repository: FileRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _hasUsageAccess = MutableStateFlow(false)



    private val _storageInfo = MutableStateFlow<StorageInfo?>(null)
    val storageInfo: StateFlow<StorageInfo?> = _storageInfo

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _rawFiles = MutableStateFlow<List<FileModel>>(emptyList())

    private val _files = MutableStateFlow<List<FileModel>>(emptyList())
    val files: StateFlow<List<FileModel>> = _files.asStateFlow()


    private val _forecastText = MutableStateFlow("...")
    val forecastText: StateFlow<String> = _forecastText.asStateFlow()

    private val _dailyUsageRate = MutableStateFlow<Long>(0)
    val dailyUsageRate: StateFlow<Long> = _dailyUsageRate.asStateFlow()

    // Derived state for estimated full date
    val estimatedFullDate: StateFlow<String> = combine(_storageInfo, _dailyUsageRate) { info, rate ->
        if (info == null || rate <= 0) return@combine "Unknown"
        val daysLeft = info.freeBytes / rate
        if (daysLeft > 365 * 5) return@combine "More than 5 years"
        
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, daysLeft.toInt())
        val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
        dateFormat.format(calendar.time)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Calculating...")

    val swipeDeleteEnabled: StateFlow<Boolean> = settingsRepository.swipeDeleteEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val swipeDeleteDirection: StateFlow<Int> = settingsRepository.swipeDeleteDirection.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0 // 0 = Left
    )

    val isSwipeNavigationEnabled: StateFlow<Boolean> = settingsRepository.swipeNavigationEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    private val _largeFiles = MutableStateFlow<List<FileModel>>(emptyList())
    val largeFiles: StateFlow<List<FileModel>> = _largeFiles.asStateFlow()

    private val _sortType = MutableStateFlow(SortType.NAME)
    val sortType: StateFlow<SortType> = _sortType.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.ASCENDING)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    val viewType: StateFlow<ViewType> = settingsRepository.viewMode
        .map { mode ->
            when (mode) {
                1 -> ViewType.GRID
                2 -> ViewType.COMPACT
                3 -> ViewType.LARGE_GRID
                else -> ViewType.LIST
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ViewType.LIST
        )

    private val _clipboardFiles = MutableStateFlow<List<FileModel>>(emptyList())
    val clipboardFiles: StateFlow<List<FileModel>> = _clipboardFiles.asStateFlow()

    private val _clipboardOperation = MutableStateFlow<ClipboardOperation?>(null)
    val clipboardOperation: StateFlow<ClipboardOperation?> = _clipboardOperation.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage = _userMessage.asSharedFlow()

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    val iconSize: StateFlow<Float> = settingsRepository.iconSize.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 1.0f
    )



    fun checkUsageAccess() {
        _hasUsageAccess.value = repository.hasUsageAccess()
    }

    sealed class NavigationEvent {
        object RequestUsageAccess : NavigationEvent()
    }

    private val _searchResults = MutableStateFlow<List<FileModel>>(emptyList())
    val searchResults: StateFlow<List<FileModel>> = _searchResults.asStateFlow()

    data class SearchFilter(
        val type: FileType? = null,
        val minSize: Long? = null,
        val maxDaysAgo: Int? = null
    ) {
        val isActive: Boolean get() = type != null || minSize != null || maxDaysAgo != null
    }

    private val _searchFilter = MutableStateFlow(SearchFilter())
    val searchFilter: StateFlow<SearchFilter> = _searchFilter.asStateFlow()

    fun updateSearchFilter(filter: SearchFilter) {
        _searchFilter.value = filter
        if (_searchQuery.value.isNotEmpty()) {
            performSearch(_searchQuery.value)
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.length >= 2) { // Debounce/Limit search triggers if needed, currently immediate
            performSearch(query)
        } else if (query.isEmpty()) {
            _searchResults.value = emptyList()
        }
    }

    fun performSearch(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val filter = _searchFilter.value
                _searchResults.value = repository.searchFiles(
                    query = query,
                    fileType = filter.type,
                    minSize = filter.minSize,
                    maxDaysAgo = filter.maxDaysAgo
                )
            } catch (e: Exception) {
                e.printStackTrace()
                showMessage("Search failed: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

    private fun showMessage(message: String) {
        viewModelScope.launch {
            _userMessage.emit(message)
        }
    }

    fun changeSorting(sortType: SortType) {
        _sortType.value = sortType
        sortFiles()
    }

    fun changeSortOrder(sortOrder: SortOrder) {
        _sortOrder.value = sortOrder
        sortFiles()
    }

    private fun sortFiles() {
        val sortedList = when (sortType.value) {
            SortType.NAME -> _rawFiles.value.sortedBy { file -> file.name.lowercase() }
            SortType.SIZE -> _rawFiles.value.sortedBy { file -> file.size }
            SortType.DATE -> _rawFiles.value.sortedBy { file -> file.dateModified }
        }

        val orderedList = if (sortOrder.value == SortOrder.DESCENDING) {
            sortedList.reversed()
        } else {
            sortedList
        }

        _files.value = orderedList.sortedBy { file -> !file.isDirectory } // Keep folders on top
    }

    fun loadFiles(path: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Artificial delay removed for speed
                // delay(300)
                _rawFiles.value = repository.getFilesFromPath(path)
                sortFiles() // Apply default sorting
            } catch (e: Exception) {
                showMessage("Error loading files: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadFilesByCategory(type: FileType) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _files.value = repository.getFilesByCategory(type)
            } catch (e: Exception) {
                showMessage("Error loading files by category: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteFile(path: String, onFinished: () -> Unit) {
        viewModelScope.launch {
            // Optimistic Update: Immediately remove from list if present
            val currentFiles = _rawFiles.value
            _rawFiles.value = currentFiles.filter { it.path != path }
            sortFiles()
            
            if (repository.deleteFile(path)) {
                onFinished()
                showMessage("Moved to Bin")
            } else {
                // Revert if failed
                _rawFiles.value = currentFiles
                sortFiles()
                showMessage("Error: Could not delete item")
            }
        }
    }

    fun deleteMultipleFiles(paths: List<String>, currentPath: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val currentFiles = _rawFiles.value
            try {
                // Optimistic update
                _rawFiles.value = currentFiles.filter { it.path !in paths }
                sortFiles()

                var successCount = 0
                paths.forEach { 
                    if (repository.deleteFile(it)) successCount++ 
                }
                
                // Refresh list eventually to be sure, but without loading spinner
                _rawFiles.value = repository.getFilesFromPath(currentPath)
                sortFiles()

                if (successCount == paths.size) {
                    showMessage("Deleted $successCount items")
                } else {
                    showMessage("Deleted $successCount/${paths.size} items")
                }
            } catch (e: Exception) {
                // Revert
                _rawFiles.value = currentFiles
                sortFiles()
                showMessage("Error deleting files: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteFilesAndReloadCategory(paths: List<String>, type: FileType) {
        viewModelScope.launch {
            // Optimistic Update: Immediately remove from list
            val currentList = _files.value
            _files.value = currentList.filter { it.path !in paths }

            // Perform deletion in background
            try {
                paths.forEach { repository.deleteFile(it) }
                // Silent refresh to ensure consistency, but don't show loading
                val freshList = repository.getFilesByCategory(type)
                _files.value = freshList
            } catch (e: Exception) {
                // Revert on error
                _files.value = currentList
                showMessage("Error deleting files: ${e.message}")
            }
        }
    }

    fun extractFile(file: FileModel, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val destinationPath = file.path.substringBeforeLast(".")
                val success = repository.extractArchive(file.path, destinationPath)
                if (success) {
                    onSuccess()
                    showMessage("Extraction successful to $destinationPath")
                } else {
                    showMessage("Extraction failed")
                }
            } catch (e: Exception) {
                showMessage("Extraction failed: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private val _recentFiles = MutableStateFlow<List<FileModel>>(emptyList())
    val recentFiles: StateFlow<List<FileModel>> = _recentFiles.asStateFlow()

    fun loadRecentFiles() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _recentFiles.value = repository.getRecentFiles()
            } catch (e: Exception) {
                showMessage("Error loading recent files: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private val _trashedFiles = MutableStateFlow<List<com.mfp.filemanager.data.trash.TrashedFile>>(emptyList())
    val trashedFiles: StateFlow<List<com.mfp.filemanager.data.trash.TrashedFile>> = _trashedFiles.asStateFlow()

    fun loadTrashedFiles() {
        viewModelScope.launch {
            _trashedFiles.value = repository.getTrashedFiles().sortedByDescending { it.dateDeleted }
        }
    }

    fun restoreFiles(trashedFiles: List<com.mfp.filemanager.data.trash.TrashedFile>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                trashedFiles.forEach { repository.restoreFile(it) }
                loadTrashedFiles()
                _trashSize.value = repository.getTrashSize()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteFilesPermanently(trashedFiles: List<com.mfp.filemanager.data.trash.TrashedFile>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                trashedFiles.forEach { repository.deleteFilePermanently(it) }
                loadTrashedFiles()
                _trashSize.value = repository.getTrashSize()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun restoreAllFiles() {
        viewModelScope.launch {
            if (repository.restoreAllFiles()) {
                loadTrashedFiles()
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            if (repository.emptyTrash()) {
                _trashSize.value = 0 // Immediate UI update
                loadTrashedFiles()
                loadDashboardData() // Sync accurate data from disk
            }
        }
    }

    // loadStorageInfo replaced/moved to combine strongly with dashboard data


    fun clearClipboard() {
        _clipboardFiles.value = emptyList()
        _clipboardOperation.value = null
    }

    fun addToClipboard(files: List<FileModel>, operation: ClipboardOperation) {
        _clipboardFiles.value = files
        _clipboardOperation.value = operation
        val count = files.size
        val message = if (operation == ClipboardOperation.COPY) {
             "Added $count ${if (count == 1) "file" else "files"} to copy. Navigate to destination."
        } else {
             "Added $count ${if (count == 1) "file" else "files"} to move. Navigate to destination."
        }
        showMessage(message)
    }

    fun addSingleToClipboard(file: FileModel, operation: ClipboardOperation) {
        addToClipboard(listOf(file), operation)
    }

    sealed interface OperationProgressState {
        object Idle : OperationProgressState
        data class Active(
            val file: FileModel,
            val operation: ClipboardOperation,
            val progress: Float, // 0f to 1f
            val bytesTransferred: Long,
            val totalBytes: Long,
            val startTime: Long,
            val speedBytesPerSec: Long,
            val currentFileIndex: Int = 0,
            val totalFiles: Int = 1
        ) : OperationProgressState
    }

    private val _operationProgress = MutableStateFlow<OperationProgressState>(OperationProgressState.Idle)
    val operationProgress: StateFlow<OperationProgressState> = _operationProgress.asStateFlow()

    private var operationJob: kotlinx.coroutines.Job? = null

    fun cancelOperation() {
        operationJob?.cancel()
        _operationProgress.value = OperationProgressState.Idle
        _isLoading.value = false
    }

    fun pasteFile(destinationPath: String, onComplete: () -> Unit = {}) {
        val filesToPaste = _clipboardFiles.value
        android.util.Log.d("HomeViewModel", "pasteFile: destinationPath=$destinationPath, filesCount=${filesToPaste.size}")
        if (filesToPaste.isEmpty()) {
            android.util.Log.w("HomeViewModel", "pasteFile: No files in clipboard")
            return
        }
        val operation = _clipboardOperation.value
        android.util.Log.d("HomeViewModel", "pasteFile: operation=$operation")
        if (operation == null) {
            android.util.Log.w("HomeViewModel", "pasteFile: No operation set")
            return
        }
        
        if (_operationProgress.value is OperationProgressState.Active) {
            android.util.Log.w("HomeViewModel", "pasteFile: Operation already in progress")
            return
        }

        operationJob = viewModelScope.launch {
            _isLoading.value = true
            val startTime = System.currentTimeMillis()
            var totalBytesCopied = 0L
            val totalBatchSize = filesToPaste.sumOf { it.size }
            
            var allSuccess = true
            
            filesToPaste.forEachIndexed { index, file ->
                if (!isActive) return@forEachIndexed
                
                var lastUpdate = 0L
                var fileBytesCopied = 0L
                
                val progressCallback: (Long, Long) -> Unit = { copied, total ->
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 300) {
                        fileBytesCopied = copied
                        val overallCopied = totalBytesCopied + copied
                        val elapsedSec = (now - startTime) / 1000f
                        val speed = if (elapsedSec > 0) (overallCopied / elapsedSec).toLong() else 0L
                        
                        _operationProgress.value = OperationProgressState.Active(
                            file = file,
                            operation = operation,
                            progress = if (totalBatchSize > 0) overallCopied.toFloat() / totalBatchSize else 0f,
                            bytesTransferred = overallCopied,
                            totalBytes = totalBatchSize,
                            startTime = startTime,
                            speedBytesPerSec = speed,
                            currentFileIndex = index + 1,
                            totalFiles = filesToPaste.size
                        )
                        lastUpdate = now
                    }
                }

                val success = try {
                    when (operation) {
                        ClipboardOperation.COPY -> repository.copyFile(file.path, destinationPath, progressCallback)
                        ClipboardOperation.MOVE -> repository.moveFile(file.path, destinationPath, progressCallback)
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    false
                }
                
                if (success) {
                    totalBytesCopied += file.size
                } else {
                    allSuccess = false
                }
            }

            _operationProgress.value = OperationProgressState.Idle
            
            if (allSuccess) {
                if (operation == ClipboardOperation.MOVE) {
                    clearClipboard()
                }
                loadFiles(destinationPath) 
                onComplete()
                showMessage("${if (operation == ClipboardOperation.COPY) "Copied" else "Moved"} ${filesToPaste.size} files successfully")
            } else {
                if (isActive) {
                    showMessage("Some files failed to transfer")
                } else {
                    showMessage("Operation cancelled")
                }
                loadFiles(destinationPath)
            }
            _isLoading.value = false
        }
    }

    fun renameFile(file: FileModel, newName: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (repository.renameFile(file.path, newName)) {
                loadFiles(File(file.path).parent ?: "")
                onSuccess()
            }
        }
    }

    fun renameMultipleFiles(files: List<FileModel>, baseName: String, onFinished: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                var successCount = 0
                files.forEachIndexed { index, file ->
                    val ext = file.name.substringAfterLast(".", "")
                    val newName = if (ext.isNotEmpty()) {
                        "$baseName (${index + 1}).$ext"
                    } else {
                        "$baseName (${index + 1})"
                    }
                    if (repository.renameFile(file.path, newName)) {
                        successCount++
                    }
                }
                showMessage("Renamed $successCount/${files.size} items")
                onFinished()
            } catch (e: Exception) {
                showMessage("Error renaming: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private val _trashSize = MutableStateFlow<Long>(0)
    val trashSize: StateFlow<Long> = _trashSize.asStateFlow()


    private val _largeFilesCount = MutableStateFlow(0)

    private suspend fun fetchStorageInfo() {
        _storageInfo.value = repository.getStorageInfo()
    }

    fun loadStorageInfo() {
        viewModelScope.launch {
            checkUsageAccess() // Check permission whenever we load info
            _isLoading.value = true
            try {
                fetchStorageInfo()
            } catch (e: Exception) {
                showMessage("Error loading storage info: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchDashboardData() = kotlinx.coroutines.coroutineScope {
        val trashSizeDeferred = async { repository.getTrashSize() }
        val forecastTextDeferred = async { repository.calculateStorageForecast() }

        _trashSize.value = trashSizeDeferred.await()
        _forecastText.value = forecastTextDeferred.await()
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            try {
                fetchDashboardData()
            } catch (_: Exception) {
                // Log or ignore, silent update
            }
        }
    }

    fun refreshHomeData() {
        viewModelScope.launch {
            _isLoading.value = true
            val minTime = launch { kotlinx.coroutines.delay(800) } // Ensure visible refresh cycle
            try {
                val job1 = launch { 
                    try { fetchStorageInfo() } catch (e: Exception) { showMessage("Error: ${e.message}") } 
                }
                val job2 = launch { 
                    try { fetchDashboardData() } catch (_: Exception) { /* Silent */ } 
                }
                
                // Wait for data
                job1.join()
                job2.join()
                
            } finally {
                // Stay loading for at least minTime
                minTime.join()
                _isLoading.value = false
            }
        }
    }

    fun loadForecastDetails() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Fetch average daily usage
                _dailyUsageRate.value = repository.getAverageDailyUsageBytes()
                // Fetch large files (e.g., > 100MB)
                _largeFiles.value = repository.getLargeFiles(100 * 1024 * 1024)
            } catch (e: Exception) {
                showMessage("Error loading forecast details: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteLargeFile(file: FileModel) {
        viewModelScope.launch {
            if (repository.deleteFile(file.path)) {
                _largeFiles.value = _largeFiles.value.filter { it.path != file.path }
                _largeFilesCount.value = _largeFiles.value.size
                _trashSize.value = repository.getTrashSize()
                loadForecastDetails() 
            }
        }
    }



    fun undoDelete(originalPath: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            // Simplified: Find in trash and restore. 
            // This assumes the file's originalPath is unique enough.
            val trashedFile = repository.getTrashedFiles().find { it.originalPath == originalPath }
            if (trashedFile != null) {
                if (repository.restoreFile(trashedFile)) {
                    onSuccess()
                    showMessage("File restored.")
                } else {
                    showMessage("Undo failed.")
                }
            } else {
                showMessage("Could not find file to restore.")
            }
        }
    }
    init {
        loadStorageInfo()
        loadDashboardData()
        
        viewModelScope.launch {
            try {
                settingsRepository.trashRetentionDays.collect { days ->
                    try {
                        val deletedCount = repository.cleanupExpiredTrash(days)
                        if (deletedCount > 0) {
                            loadTrashedFiles()
                            _trashSize.value = repository.getTrashSize()
                        }
                    } catch (e: Exception) {
                        // Log error for trash cleanup, prevent crash
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getOtherVolumes(): List<StorageVolumeInfo> {
        return repository.getExternalVolumes()
    }
}

class HomeViewModelFactory(private val repository: FileRepository, private val settingsRepository: SettingsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // Assuming repository.context is available or we pass application context higher up.
            // But since Factory is usually created in Activity/Fragment, we can pass context there or use repository's context if exposed.
            // Ideally ViewModel shouldn't hold context, but for starting activity it's needed here or should be handled by UI event.
            // Let's rely on the UI to handle the event or pass the application context safely.
            // Refactoring: Instead of injecting Context to ViewModel, let's expose a clear signal (SingleLiveEvent or similar) for UI to handle Intent.
            // However, to keep it simple as per request, I will rely on repository context if accessible (it is private).
            // BETTER: Use `AndroidViewModel` which has application context, OR just emit an event.
            // Let's stick to emitting an event or just passing context in Factory construction. 
            // Factory construction update is harder.
            // I'll revert the constructor change and use a SharedFlow for the generic "SideEffect".
            return HomeViewModel(repository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
