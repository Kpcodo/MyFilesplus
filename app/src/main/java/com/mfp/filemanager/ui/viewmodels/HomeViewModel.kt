package com.mfp.filemanager.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mfp.filemanager.data.FileOperationManager
import com.mfp.filemanager.data.clipboard.ClipboardOperation
import com.mfp.filemanager.data.FileModel
import com.mfp.filemanager.data.FileRepository
import com.mfp.filemanager.data.FileType
import com.mfp.filemanager.data.SettingsRepository
import com.mfp.filemanager.data.StorageInfo
import com.mfp.filemanager.data.StorageVolumeInfo
import com.mfp.filemanager.data.trash.TrashedFile
import com.mfp.filemanager.ui.SortType
import com.mfp.filemanager.ui.SortOrder
import com.mfp.filemanager.ui.ViewType
import com.mfp.filemanager.data.OperationStatus
import com.mfp.filemanager.data.OperationType
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
import kotlinx.coroutines.delay
import java.io.File


class HomeViewModel(
    private val repository: FileRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _hasUsageAccess = MutableStateFlow(false)
    var hasStorageAnimated: Boolean
        get() = com.mfp.filemanager.data.cache.AppCache.hasAnimationPlayed("home_dashboard")
        set(value) { com.mfp.filemanager.data.cache.AppCache.setAnimationPlayed("home_dashboard", value) }



    private val _storageInfo = MutableStateFlow(StorageInfo.EMPTY)
    val storageInfo: StateFlow<StorageInfo> = _storageInfo

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isBrowserSelectionMode = MutableStateFlow(false)
    val isBrowserSelectionMode: StateFlow<Boolean> = _isBrowserSelectionMode.asStateFlow()

    private val _selectedBrowserFiles = MutableStateFlow<Set<String>>(emptySet())
    val selectedBrowserFiles: StateFlow<Set<String>> = _selectedBrowserFiles.asStateFlow()

    private val _isRecentSelectionMode = MutableStateFlow(false)
    val isRecentSelectionMode: StateFlow<Boolean> = _isRecentSelectionMode.asStateFlow()

    private val _selectedRecentFiles = MutableStateFlow<Set<String>>(emptySet())
    val selectedRecentFiles: StateFlow<Set<String>> = _selectedRecentFiles.asStateFlow()

    private val _isTrashSelectionMode = MutableStateFlow(false)
    val isTrashSelectionMode: StateFlow<Boolean> = _isTrashSelectionMode.asStateFlow()

    private val _selectedTrashFiles = MutableStateFlow<Set<Long>>(emptySet())
    val selectedTrashFiles: StateFlow<Set<Long>> = _selectedTrashFiles.asStateFlow()

    private val _rawFiles = MutableStateFlow<List<FileModel>>(emptyList())
    private val _files = MutableStateFlow<List<FileModel>>(emptyList())
    val files: StateFlow<List<FileModel>> = combine(_files, _selectedBrowserFiles) { files, selected ->
        files.map { it.copy(isSelected = selected.contains(it.path)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    private val _forecastText = MutableStateFlow("...")
    val forecastText: StateFlow<String> = _forecastText.asStateFlow()

    private val _operationStatus = MutableStateFlow(OperationStatus())
    val operationStatus: StateFlow<OperationStatus> = _operationStatus.asStateFlow()

    private val _currentPath = MutableStateFlow("")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _dailyUsageRate = MutableStateFlow<Long>(0)
    val dailyUsageRate: StateFlow<Long> = _dailyUsageRate.asStateFlow()

    // Derived state for estimated full date
    val estimatedFullDate: StateFlow<String> = combine(_storageInfo, _dailyUsageRate) { info, rate ->
        if (info == StorageInfo.EMPTY || rate <= 0) return@combine "Unknown"
        val daysLeft = info.freeBytes / rate
        
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, daysLeft.toInt())
        
        // Dynamic formatting based on duration
        val dateFormat = if (daysLeft > 365) {
            // If more than a year, show Month and Year (e.g., "Jan 2028")
            java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
        } else {
            // If within a year, show Month and Day (e.g., "Oct 12")
            java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
        }
        
        dateFormat.format(calendar.time)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Calculating...")

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

    val iconSize: StateFlow<Float> = settingsRepository.iconSize.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 1.0f
    )

    val showHiddenFiles: StateFlow<Boolean> = settingsRepository.showHiddenFiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val thumbnailSeed: StateFlow<Long> = settingsRepository.thumbnailSeed.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    init {
        viewModelScope.launch {
            FileOperationManager.clipboard.collect { clipboard ->
                _clipboardFiles.value = clipboard?.items ?: emptyList()
                _clipboardOperation.value = clipboard?.operation
            }
        }
        
        viewModelScope.launch {
            FileOperationManager.progress.collect { progress ->
                 if (progress?.status == com.mfp.filemanager.data.clipboard.TransferStatus.COMPLETED) {
                     // Refresh current view if operation completed
                     // We could check if destination matches, but refreshing generally is safer to show new files
                     if (_currentPath.value.isNotEmpty()) {
                         loadFiles(_currentPath.value, true)
                     }
                 }
            }
        }

        viewModelScope.launch {
            showHiddenFiles.collect {
                sortFiles()
                loadRecentFiles()
            }
        }
    }
        
    private val _userMessage = MutableSharedFlow<String>()
    val userMessage = _userMessage.asSharedFlow()

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    




    fun checkUsageAccess() {
        _hasUsageAccess.value = repository.hasUsageAccess()
    }



    private val _searchResults = MutableStateFlow<List<FileModel>>(emptyList())
    val searchResults: StateFlow<List<FileModel>> = _searchResults.asStateFlow()

    private val _categoryFiles = MutableStateFlow<List<FileModel>>(emptyList())
    val categoryFiles: StateFlow<List<FileModel>> = combine(_categoryFiles, _selectedBrowserFiles) { files, selected ->
        files.map { it.copy(isSelected = selected.contains(it.path)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentMediaList = MutableStateFlow<List<FileModel>>(emptyList())
    val currentMediaList: StateFlow<List<FileModel>> = _currentMediaList.asStateFlow()

    fun setMediaContext(allFiles: List<FileModel>) {
        _currentMediaList.value = allFiles.filter { it.type == FileType.IMAGE || it.type == FileType.VIDEO }
    }

    data class SearchFilter(
        val type: FileType? = null,
        val minSize: Long? = null,
        val maxDaysAgo: Int? = null,
        val onlyFolders: Boolean = false,
        val onlyFiles: Boolean = false
    ) {
        val isActive: Boolean get() = type != null || minSize != null || maxDaysAgo != null || onlyFolders || onlyFiles
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
        if (query.isNotEmpty()) { // Trigger search from the first character as requested
            performSearch(query)
        } else if (query.isEmpty()) {
            _searchResults.value = emptyList()
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    fun performSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(200) // Small debounce for smooth typing
            _isLoading.value = true
            try {
                val filter = _searchFilter.value
                
                // If "Folders Only" is selected, ignore FileType filter because Folders don't have media types like Image/Video
                val effectiveFileType = if (filter.onlyFolders) null else filter.type
                
                val results = repository.searchFiles(
                    query = query,
                    fileType = effectiveFileType,
                    minSize = filter.minSize,
                    maxDaysAgo = filter.maxDaysAgo
                )
                
                // Post-process for "Folder/File" scope if needed
                // Since our repository search supports basic file types, we might get folders if not filtering by Type.
                // We refine here:
                val refinedResults = results.filter { file ->
                   if (filter.onlyFolders) file.isDirectory else true
                }.filter { file ->
                   if (filter.onlyFiles) !file.isDirectory else true
                }

                _searchResults.value = refinedResults
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    e.printStackTrace()
                    showMessage("Search failed: ${e.message}")
                }
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
        val showHidden = showHiddenFiles.value
        val filteredList = _rawFiles.value.filter { file -> 
            if (showHidden) true else !file.name.startsWith(".")
        }

        val sortedList = when (sortType.value) {
            SortType.NAME -> filteredList.sortedBy { file -> file.name.lowercase() }
            SortType.SIZE -> filteredList.sortedBy { file -> file.size }
            SortType.DATE -> filteredList.sortedBy { file -> file.dateModified }
        }

        val orderedList = if (sortOrder.value == SortOrder.DESCENDING) {
            sortedList.reversed()
        } else {
            sortedList
        }

        _files.value = orderedList.sortedBy { file -> !file.isDirectory } // Keep folders on top
    }

    fun loadFiles(path: String, isRefresh: Boolean = false) {
        // Optimization: Don't reload if we are already displaying this path, unless forcing refresh
        if (!isRefresh && _currentPath.value == path && _rawFiles.value.isNotEmpty()) {
            return
        }

        _currentPath.value = path
        viewModelScope.launch {
            if (isRefresh) _isRefreshing.value = true else _isLoading.value = true
            try {
                // Artificial delay removed for speed
                // delay(300)
                _rawFiles.value = repository.getFilesFromPath(path)
                sortFiles() // Apply default sorting
            } catch (e: Exception) {
                showMessage("Error loading files: ${e.message}")
            } finally {
                if (isRefresh) _isRefreshing.value = false else _isLoading.value = false
            }
        }
    }


    fun deleteFile(path: String, currentPath: String = "") {
        if (_operationStatus.value.isRunning) {
            showMessage("Please wait for current operation to finish")
            return
        }
        viewModelScope.launch {
            val fileToDelete = _rawFiles.value.find { it.path == path }
            
            FileOperationManager.startDeleteOperation(1)
            
            _operationStatus.value = OperationStatus(
                isRunning = true,
                type = OperationType.TRASH,
                progress = 0f,
                processedCount = 0,
                totalCount = 1
            )
            
            // Optimistic Update Browser: Immediately remove from list
            val originalFiles = _rawFiles.value
            _rawFiles.value = originalFiles.filter { it.path != path }
            sortFiles()

            // Optimistic Update Trash
            fileToDelete?.let { addOptimisticTrashItems(listOf(it)) }
            
            if (_selectedBrowserFiles.value.contains(path)) {
                val currentSelected = _selectedBrowserFiles.value.toMutableSet()
                currentSelected.remove(path)
                _selectedBrowserFiles.value = currentSelected.toSet() 
                if (currentSelected.isEmpty()) {
                    exitBrowserSelectionMode()
                }
            }

            try {
                if (repository.deleteFile(path)) {
                    _operationStatus.value = _operationStatus.value.copy(progress = 1f, processedCount = 1)
                    FileOperationManager.updateDeleteProgress(1, 1)
                    if (currentPath.isNotEmpty()) {
                        loadFiles(currentPath)
                    }
                    loadTrashedFiles() // Sync for real data
                    loadForecastDetails()
                } else {
                    // Revert
                    _rawFiles.value = originalFiles
                    sortFiles()
                    loadTrashedFiles() // Cleanup optimistic trash
                    showMessage("Error: Could not delete item")
                }
            } catch (e: Exception) {
                _rawFiles.value = originalFiles
                sortFiles()
                loadTrashedFiles()
                showMessage("Error: ${e.message}")
            } finally {
                _operationStatus.value = OperationStatus()
                FileOperationManager.finishOperation()
            }
        }
    }

    fun deleteSelectedBrowserFiles(@Suppress("UNUSED_PARAMETER") currentPath: String) {
        val selected = _selectedBrowserFiles.value.toList()
        if (selected.isEmpty()) return
        deleteMultipleFiles(selected)
    }

    fun copySelectedBrowserFiles() {
        val selectedPaths = _selectedBrowserFiles.value
        if (selectedPaths.isEmpty()) return
        
        // Find FileModels from either main files or category files
        val selected = (_files.value + _categoryFiles.value)
            .filter { it.path in selectedPaths }
            .distinctBy { it.path }

        if (selected.isEmpty()) return
        
        FileOperationManager.addToClipboard(selected, ClipboardOperation.COPY, _currentPath.value)
        exitBrowserSelectionMode()
    }

    fun moveSelectedBrowserFiles() {
        val selectedPaths = _selectedBrowserFiles.value
        if (selectedPaths.isEmpty()) return
        
        // Find FileModels from either main files or category files
        val selected = (_files.value + _categoryFiles.value)
            .filter { it.path in selectedPaths }
            .distinctBy { it.path }

        if (selected.isEmpty()) return
        
        FileOperationManager.addToClipboard(selected, ClipboardOperation.MOVE, _currentPath.value)
        exitBrowserSelectionMode()
    }

    fun copyFileToClipboard(file: FileModel) {
        FileOperationManager.addToClipboard(listOf(file), ClipboardOperation.COPY, _currentPath.value)
    }

    fun moveFileToClipboard(file: FileModel) {
        FileOperationManager.addToClipboard(listOf(file), ClipboardOperation.MOVE, _currentPath.value)
    }

    fun pasteClipboardFiles(destinationPath: String) {
        if (_clipboardFiles.value.isEmpty()) return

        // Delegate to FileOperationManager
        // Pass repository instance because FileOperationManager needs it to perform operations
        FileOperationManager.paste(destinationPath, repository)
    }

    private fun getUnevenProgress(currentCount: Int, totalCount: Int, sizes: List<Long>): Float {
        if (totalCount == 0) return 0f
        val totalSize = sizes.sum().toFloat()
        if (totalSize == 0f) return (currentCount + 1).toFloat() / totalCount
        
        val processedSize = sizes.take(currentCount + 1).sum().toFloat()
        return (processedSize / totalSize).coerceIn(0f, 1f)
    }

    fun deleteMultipleFiles(paths: List<String>) {
        if (_operationStatus.value.isRunning) {
            showMessage("Please wait for current operation to finish")
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            
            val filesToDelete = _rawFiles.value.filter { it.path in paths }

            FileOperationManager.startDeleteOperation(paths.size)

            _operationStatus.value = OperationStatus(
                isRunning = true,
                type = OperationType.TRASH,
                progress = 0f,
                processedCount = 0,
                totalCount = paths.size
            )

            try {
                // Optimistic Update Trash is still okay visually as it adds to another screen
                addOptimisticTrashItems(filesToDelete)
                
                val remainingSelected = _selectedBrowserFiles.value.filter { it !in paths }.toSet()
                _selectedBrowserFiles.value = remainingSelected
                if (remainingSelected.isEmpty()) {
                    exitBrowserSelectionMode()
                }

                val fileSizes = filesToDelete.map { it.size }
                var lastProcessedCount = 0
                val allSuccess = repository.deleteFilesBatch(paths) { progress ->
                    val currentCount = (progress * paths.size).toInt()
                    
                    // Skip update logic to create jumps (randomly skip until 40% chance or end reached)
                    if (currentCount > lastProcessedCount) {
                        if (currentCount < paths.size && Math.random() < 0.4) {
                            return@deleteFilesBatch
                        }

                        // Batch removal for the jump
                        val pathsToRemove = paths.slice(lastProcessedCount until currentCount)
                        _rawFiles.value = _rawFiles.value.filter { it.path !in pathsToRemove }
                        
                        val unevenProgress = getUnevenProgress(currentCount - 1, paths.size, fileSizes)
                        _operationStatus.value = _operationStatus.value.copy(
                            progress = if (progress >= 1f) 1f else unevenProgress,
                            processedCount = currentCount
                        )
                        FileOperationManager.updateDeleteProgress(currentCount, paths.size)
                        lastProcessedCount = currentCount
                    }
                }
                
                if (allSuccess) {
                    _operationStatus.value = _operationStatus.value.copy(progress = 1f, processedCount = paths.size)
                    // Wait for animation
                    delay(200)
                } else {
                    showMessage("Some items could not be moved to Bin")
                }

                // Final sync and sort
                sortFiles()
                loadTrashedFiles() // Sync Trash
                loadForecastDetails()

            } catch (e: Exception) {
                loadTrashedFiles()
                showMessage("Error deleting files: ${e.message}")
            } finally {
                _isLoading.value = false
                _operationStatus.value = OperationStatus() // Reset
                FileOperationManager.finishOperation()
            }
        }
    }


    fun extractFile(file: FileModel, onSuccess: () -> Unit) {
        if (_operationStatus.value.isRunning) {
            showMessage("Please wait for current operation to finish")
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _operationStatus.value = OperationStatus(
                isRunning = true,
                type = OperationType.EXTRACT,
                progress = 0f,
                processedCount = 0,
                totalCount = 1
            )
            
            try {
                val destinationPath = file.path.substringBeforeLast(".")
                val success = repository.extractArchive(file.path, destinationPath) { progress ->
                    _operationStatus.value = _operationStatus.value.copy(
                        progress = progress,
                        processedCount = if (progress >= 1f) 1 else 0
                    )
                }
                
                if (success) {
                    _operationStatus.value = _operationStatus.value.copy(progress = 1f, processedCount = 1)
                    onSuccess()
                    showMessage("Extraction successful to $destinationPath")
                    loadFiles(File(file.path).parent ?: "")
                } else {
                    showMessage("Extraction failed")
                }
            } catch (e: Exception) {
                showMessage("Extraction failed: ${e.message}")
            } finally {
                _isLoading.value = false
                _operationStatus.value = OperationStatus()
            }
        }
    }


    private val _recentFiles = MutableStateFlow<List<FileModel>>(emptyList())
    val recentFiles: StateFlow<List<FileModel>> = combine(_recentFiles, _selectedRecentFiles) { files, selected ->
        files.map { it.copy(isSelected = selected.contains(it.path)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ------------------------------------

    fun loadRecentFiles() {
        viewModelScope.launch {
             try {
                val allRecent = repository.getRecentFiles(showHidden = showHiddenFiles.value)
                _recentFiles.value = allRecent
            } catch (e: Exception) {
                showMessage("Error loading recent files: ${e.message}")
            }
        }
    }

    fun loadFilesByCategory(category: FileType) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _categoryFiles.value = repository.getFilesByCategory(category)
            } catch (e: Exception) {
                showMessage("Error loading ${category.name}: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleRecentSelection(file: FileModel) {
        if (!_isRecentSelectionMode.value) {
            _isRecentSelectionMode.value = true
        }
        val current = _selectedRecentFiles.value.toMutableSet()
        if (current.contains(file.path)) {
            current.remove(file.path)
        } else {
            current.add(file.path)
        }
        _selectedRecentFiles.value = current
    }

    fun selectAllRecentFiles() {
        _isRecentSelectionMode.value = true
        _selectedRecentFiles.value = _recentFiles.value.map { it.path }.toSet()
    }

    fun exitRecentSelectionMode() {
        _isRecentSelectionMode.value = false
        _selectedRecentFiles.value = emptySet()
    }

    fun clearRecentSelection() {
        // Just clear selection, but stay in mode
        _selectedRecentFiles.value = emptySet()
    }

    fun copySelectedRecentFiles() {
        val selected = recentFiles.value.filter { it.isSelected }
        if (selected.isEmpty()) return
        _clipboardFiles.value = selected
        _clipboardOperation.value = ClipboardOperation.COPY
        exitRecentSelectionMode()
    }

    fun moveSelectedRecentFiles() {
        val selected = recentFiles.value.filter { it.isSelected }
        if (selected.isEmpty()) return
        _clipboardFiles.value = selected
        _clipboardOperation.value = ClipboardOperation.MOVE
        exitRecentSelectionMode()
    }

    fun deleteSelectedRecentFiles() {
        if (_operationStatus.value.isRunning) {
            showMessage("Please wait for current operation to finish")
            return
        }
        val selectedPaths = _selectedRecentFiles.value.toList()
        if (selectedPaths.isEmpty()) return
        
        viewModelScope.launch {
            FileOperationManager.startDeleteOperation(selectedPaths.size)
            _operationStatus.value = OperationStatus(isRunning = true, type = OperationType.TRASH, totalCount = selectedPaths.size)
            
            // Optimistic update Recents
            val currentRecents = _recentFiles.value
            val filesToDelete = currentRecents.filter { it.path in selectedPaths }
            try {
                // Optimistic update Trash is okay
                addOptimisticTrashItems(filesToDelete)

                val fileSizes = filesToDelete.map { it.size }
                var lastProcessedCount = 0
                val allSuccess = repository.deleteFilesBatch(selectedPaths) { progress ->
                    val currentCount = (progress * selectedPaths.size).toInt()
                    
                    if (currentCount > lastProcessedCount) {
                        FileOperationManager.updateDeleteProgress(currentCount, selectedPaths.size)
                        // Skip update logic to create jumps
                        if (currentCount < selectedPaths.size && Math.random() < 0.4) {
                            return@deleteFilesBatch
                        }

                        val pathsToRemove = selectedPaths.slice(lastProcessedCount until currentCount)
                        _recentFiles.value = _recentFiles.value.filter { it.path !in pathsToRemove }

                        val unevenProgress = getUnevenProgress(currentCount - 1, selectedPaths.size, fileSizes)
                        _operationStatus.value = _operationStatus.value.copy(
                            progress = if (progress >= 1f) 1f else unevenProgress,
                            processedCount = currentCount
                        )
                        lastProcessedCount = currentCount
                    }
                }
                if (allSuccess) {
                    _operationStatus.value = _operationStatus.value.copy(progress = 1f, processedCount = selectedPaths.size)
                    // Wait for animation
                    delay(200)
                    exitRecentSelectionMode()
                } else {
                    showMessage("Some items could not be deleted")
                }
                loadRecentFiles()
                loadDashboardData()
                loadTrashedFiles()
            } catch (e: Exception) {
                loadRecentFiles()
                loadTrashedFiles()
                showMessage("Error: ${e.message}")
            } finally {
                _operationStatus.value = OperationStatus()
                FileOperationManager.finishOperation()
            }
        }
    }


    private val _trashedFiles = MutableStateFlow<List<TrashedFile>>(emptyList())
    val trashedFiles: StateFlow<List<TrashedFile>> = combine(_trashedFiles, _selectedTrashFiles) { files, selected ->
        files.map { it.copy(isSelected = selected.contains(it.id)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())



    fun toggleTrashSelection(id: Long) {
        if (!_isTrashSelectionMode.value) {
            _isTrashSelectionMode.value = true
        }
        val current = _selectedTrashFiles.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedTrashFiles.value = current
        if (current.isEmpty()) {
            _isTrashSelectionMode.value = false
        }
    }

    fun selectAllTrashFiles() {
        _isTrashSelectionMode.value = true
        _selectedTrashFiles.value = _trashedFiles.value.map { it.id }.toSet()
    }

    fun exitTrashSelectionMode() {
        _isTrashSelectionMode.value = false
        _selectedTrashFiles.value = emptySet()
    }

    fun clearTrashSelection() {
        _selectedTrashFiles.value = emptySet()
        _isTrashSelectionMode.value = false
    }

    fun loadTrashedFiles() {
        viewModelScope.launch {
            _trashedFiles.value = repository.getTrashedFiles().sortedByDescending { it.dateDeleted }
        }
    }

    fun deleteRecentFile(file: FileModel) {
        if (_operationStatus.value.isRunning) {
            showMessage("Please wait for current operation to finish")
            return
        }
        viewModelScope.launch {
            FileOperationManager.startDeleteOperation(1)
            _operationStatus.value = OperationStatus(isRunning = true, type = OperationType.TRASH, totalCount = 1)
            
            // Optimistic update Recents
            val currentRecents = _recentFiles.value
            _recentFiles.value = currentRecents.filter { it.path != file.path }
            
            // Optimistic update Trash
            addOptimisticTrashItems(listOf(file))

            try {
                if (repository.deleteFile(file.path)) {
                    showMessage("Deleted ${file.name}")
                    loadDashboardData() // Sync size
                    loadTrashedFiles() // Sync actual items
                    FileOperationManager.updateDeleteProgress(1, 1)
                } else {
                    // Revert
                    _recentFiles.value = currentRecents
                    loadTrashedFiles()
                    showMessage("Error deleting file")
                }
            } catch (e: Exception) {
                _recentFiles.value = currentRecents
                loadTrashedFiles()
                showMessage("Error: ${e.message}")
            } finally {
                _operationStatus.value = OperationStatus()
                FileOperationManager.finishOperation()
            }
        }
    }

    private fun addOptimisticTrashItems(files: List<FileModel>) {
        val tempTrashedItems = files.map { model ->
            TrashedFile(
                id = System.nanoTime(),
                name = model.name,
                originalPath = model.path,
                trashPath = model.path,
                size = model.size,
                dateDeleted = System.currentTimeMillis(),
                type = model.type
            )
        }
        _trashedFiles.value = tempTrashedItems + _trashedFiles.value
        // Update trash size flow if it exists
        _trashSize.value += files.sumOf { it.size }
    }

    fun restoreFiles(trashedFiles: List<TrashedFile>) {
        if (_operationStatus.value.isRunning) {
            showMessage("Please wait for current operation to finish")
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            FileOperationManager.startRestoreOperation(trashedFiles.size)
            _operationStatus.value = OperationStatus(
                isRunning = true,
                type = OperationType.RESTORE,
                progress = 0f,
                processedCount = 0,
                totalCount = trashedFiles.size
            )

            try {
                // No optimistic removal from list
                val fileSizes = trashedFiles.map { it.size }
                var lastProcessedCount = 0
                val allSuccess = repository.restoreFilesBatch(trashedFiles) { progress ->
                    val currentCount = (progress * trashedFiles.size).toInt()
                    
                    if (currentCount > lastProcessedCount) {
                        // Skip update logic to create jumps
                        if (currentCount < trashedFiles.size && Math.random() < 0.4) {
                            return@restoreFilesBatch
                        }

                        val itemsToRemove = trashedFiles.slice(lastProcessedCount until currentCount)
                        val idsToRemove = itemsToRemove.map { it.id }.toSet()
                        
                        _trashedFiles.value = _trashedFiles.value.filter { it.id !in idsToRemove }
                        _trashSize.value = (_trashSize.value - itemsToRemove.sumOf { it.size }).coerceAtLeast(0L)

                        val unevenProgress = getUnevenProgress(currentCount - 1, trashedFiles.size, fileSizes)
                        _operationStatus.value = _operationStatus.value.copy(
                            progress = if (progress >= 1f) 1f else unevenProgress,
                            processedCount = currentCount
                        )
                        lastProcessedCount = currentCount
                        FileOperationManager.updateRestoreProgress(currentCount, trashedFiles.size)
                    }
                }
                
                if (allSuccess) {
                    _operationStatus.value = _operationStatus.value.copy(progress = 1f, processedCount = trashedFiles.size)
                    // Wait for animation
                    delay(200)
                    showMessage("Restored ${trashedFiles.size} items")
                } else {
                    showMessage("Some items could not be restored")
                }

                // Background sync/refresh
                loadTrashedFiles()
                _trashSize.value = repository.getTrashSize()
                loadForecastDetails()

            } catch (e: Exception) {
                showMessage("Restore failed: ${e.message}")
            } finally {
                _isLoading.value = false
                _operationStatus.value = OperationStatus() // Reset status
                FileOperationManager.finishOperation()
            }
        }
    }

    fun deleteFilesPermanently(trashedFiles: List<TrashedFile>) {
        viewModelScope.launch {
            _isLoading.value = true
            FileOperationManager.startPermDeleteOperation(trashedFiles.size)
            _operationStatus.value = OperationStatus(
                isRunning = true,
                type = OperationType.DELETE,
                progress = 0f,
                processedCount = 0,
                totalCount = trashedFiles.size
            )

            try {
                val fileSizes = trashedFiles.map { it.size }
                var successCount = 0
                trashedFiles.forEachIndexed { index, file ->
                    if (repository.deleteFilePermanently(file)) successCount++
                    
                    val currentCount = index + 1
                    // Jump logic: show only if random threshold met or it's the last item
                    if (currentCount == trashedFiles.size || Math.random() > 0.4) {
                        val unevenProgress = getUnevenProgress(currentCount - 1, trashedFiles.size, fileSizes)
                        _operationStatus.value = _operationStatus.value.copy(
                            progress = if (currentCount >= trashedFiles.size) 1f else unevenProgress,
                            processedCount = currentCount
                        )
                        FileOperationManager.updatePermDeleteProgress(currentCount, trashedFiles.size)
                    }
                }
                
                if (successCount > 0) {
                    _operationStatus.value = _operationStatus.value.copy(progress = 1f, processedCount = trashedFiles.size)
                    // Wait for animation
                    delay(200)
                }

                // Sync UI
                loadTrashedFiles()
                _trashSize.value = repository.getTrashSize()

                if (successCount == trashedFiles.size) {
                    showMessage("Deleted $successCount items permanently")
                } else {
                    showMessage("Deleted $successCount/${trashedFiles.size} items")
                }
            } catch (e: Exception) {
                loadTrashedFiles()
                showMessage("Delete failed: ${e.message}")
            } finally {
                _isLoading.value = false
                _operationStatus.value = OperationStatus() // Reset status
                FileOperationManager.finishOperation()
            }
        }
    }

    fun restoreAllFiles() {
        val filesToRestore = _trashedFiles.value
        if (filesToRestore.isNotEmpty()) {
            restoreFiles(filesToRestore)
        }
    }

    fun emptyTrash() {
        if (_operationStatus.value.isRunning) {
            showMessage("Please wait for current operation to finish")
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            val filesToDelete = _trashedFiles.value
            _operationStatus.value = OperationStatus(
                isRunning = true,
                type = OperationType.DELETE,
                progress = 0f,
                processedCount = 0,
                totalCount = filesToDelete.size
            )
            
            try {
                if (repository.emptyTrash()) {
                    _trashSize.value = 0 // Immediate UI update
                    loadTrashedFiles()
                    loadDashboardData() // Sync accurate data from disk
                    showMessage("Trash emptied")
                }
            } catch (e: Exception) {
                showMessage("Failed to empty trash: ${e.message}")
            } finally {
                _isLoading.value = false
                _operationStatus.value = OperationStatus()
            }
        }
    }

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

    private var operationJob: kotlinx.coroutines.Job? = null

    fun cancelOperation() {
        operationJob?.cancel()
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
        
        if (_operationStatus.value.isRunning) {
            showMessage("Please wait for current operation to finish")
            return
        }

        operationJob = viewModelScope.launch {
            _isLoading.value = true
            
            // Immediately show banner
            _operationStatus.value = OperationStatus(
                isRunning = true,
                type = if (operation == ClipboardOperation.COPY) OperationType.COPY else OperationType.MOVE,
                progress = 0f,
                processedCount = 0,
                totalCount = filesToPaste.size
            )
            
            val fileSizes = filesToPaste.map { it.size }
            
            var allSuccess = true
            val currentViewingPath = _currentPath.value
            var lastShownIndex = 0

            filesToPaste.forEachIndexed { index, file ->
                if (!isActive) return@forEachIndexed
                
                val progressCallback: (Long, Long) -> Unit = { _, _ ->
                    // For Copy/Move, we mainly use per-file completion for the "one-by-one" feel
                    // but we can update smooth progress for the current file too
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
                    val currentCount = index + 1
                    // Jump logic like Delete/Restore
                    if (currentCount == filesToPaste.size || Math.random() > 0.4) {
                        // Refresh current view if we are pasting into it, so user sees progress
                        if (destinationPath == currentViewingPath) {
                            loadFiles(destinationPath)
                        }

                        // If MOVE and we are in source, remove from source
                        if (operation == ClipboardOperation.MOVE) {
                            val sourceParent = File(file.path).parent ?: ""
                            if (sourceParent == currentViewingPath) {
                                val jumpBatch = filesToPaste.slice(lastShownIndex until currentCount)
                                val pathsToClear = jumpBatch.map { it.path }.toSet()
                                _rawFiles.value = _rawFiles.value.filter { it.path !in pathsToClear }
                            }
                        }

                        val unevenProgress = getUnevenProgress(currentCount - 1, filesToPaste.size, fileSizes)
                        _operationStatus.value = _operationStatus.value.copy(
                            progress = if (currentCount >= filesToPaste.size) 1f else unevenProgress,
                            processedCount = currentCount
                        )
                        lastShownIndex = currentCount
                    }
                } else {
                    allSuccess = false
                }
            }

            if (allSuccess) {
                _operationStatus.value = _operationStatus.value.copy(progress = 1f, processedCount = filesToPaste.size)
                // Wait for animation
                delay(200)
                
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
            _operationStatus.value = OperationStatus() // Reset unified status
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

    private suspend fun fetchStorageInfo(forceRefresh: Boolean = false) {
        if (!forceRefresh) {
            val cachedInfo = com.mfp.filemanager.data.cache.AppCache.getData<StorageInfo>("storage_info_root")
            if (cachedInfo != null) {
                _storageInfo.value = cachedInfo
                return
            }
        }
        val info = repository.getStorageInfo()
        _storageInfo.value = info
        com.mfp.filemanager.data.cache.AppCache.putData("storage_info_root", info)
    }

    fun loadStorageInfo() {
        viewModelScope.launch {
            checkUsageAccess() // Check permission whenever we load info
            try {
                fetchStorageInfo()
            } catch (e: Exception) {
                showMessage("Error loading storage info: ${e.message}")
            } 
        }
    }

    private suspend fun fetchDashboardData(forceRefresh: Boolean = false) = kotlinx.coroutines.coroutineScope {
        val cachedTrash = if (!forceRefresh) com.mfp.filemanager.data.cache.AppCache.getData<Long>("trash_size_val") else null
        val cachedForecast = if (!forceRefresh) com.mfp.filemanager.data.cache.AppCache.getData<String>("forecast_text_val") else null

        if (cachedTrash != null && cachedForecast != null) {
             _trashSize.value = cachedTrash
             _forecastText.value = cachedForecast
             return@coroutineScope
        }

        val trashSizeDeferred = async { repository.getTrashSize() }
        val forecastTextDeferred = async { repository.calculateStorageForecast() }

        val trash = trashSizeDeferred.await()
        val forecast = forecastTextDeferred.await()
        
        _trashSize.value = trash
        _forecastText.value = forecast
        
        com.mfp.filemanager.data.cache.AppCache.putData("trash_size_val", trash)
        com.mfp.filemanager.data.cache.AppCache.putData("forecast_text_val", forecast)
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
            _isRefreshing.value = true
            val minTime = viewModelScope.launch { delay(800) } // Ensure visible refresh cycle
            try {
                // Sequence tasks to avoid simultaneous binder heavy requests (prevent system_server ANR)
                try { 
                    fetchStorageInfo(true) // Force refresh
                } catch (e: Exception) { 
                    showMessage("Error: ${e.message}") 
                } 
                
                try { 
                    fetchDashboardData(true) // Force refresh
                } catch (_: Exception) { 
                    /* Silent */ 
                } 
                
            } finally {
                // Stay refreshing for at least minTime
                minTime.join()
                _isRefreshing.value = false
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
        if (_operationStatus.value.isRunning) {
            showMessage("Please wait for current operation to finish")
            return
        }
        viewModelScope.launch {
            _operationStatus.value = OperationStatus(isRunning = true, type = OperationType.TRASH, totalCount = 1)
            
            // Optimistic update Large Files List
            val currentLargeFiles = _largeFiles.value
            _largeFiles.value = currentLargeFiles.filter { it.path != file.path }
            
            // Optimistic update Trash
            addOptimisticTrashItems(listOf(file))

            try {
                if (repository.deleteFile(file.path)) {
                    loadDashboardData()
                    loadTrashedFiles()
                    loadForecastDetails() 
                } else {
                    _largeFiles.value = currentLargeFiles
                    loadTrashedFiles()
                    showMessage("Error deleting file")
                }
            } catch (e: Exception) {
                _largeFiles.value = currentLargeFiles
                loadTrashedFiles()
                showMessage("Error: ${e.message}")
            } finally {
                _operationStatus.value = OperationStatus()
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
                } else {
                    showMessage("Undo failed.")
                }
            } else {
                showMessage("Could not find file to restore.")
            }
        }
    }
    init {
        try {
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
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getOtherVolumes(): List<StorageVolumeInfo> {
        return repository.getExternalVolumes()
    }

    fun restoreSelectedFiles() {
        val selectedIds = _selectedTrashFiles.value
        val toRestore = _trashedFiles.value.filter { it.id in selectedIds }
        // Delegate to main method active tracking
        restoreFiles(toRestore)
    }

    fun deleteSelectedTrashPermanently() {
        val selectedIds = _selectedTrashFiles.value
        val toDelete = _trashedFiles.value.filter { it.id in selectedIds }
        // Delegate to main method active tracking
        deleteFilesPermanently(toDelete)
    }

    fun toggleBrowserSelection(path: String) {
        if (!_isBrowserSelectionMode.value) {
            _isBrowserSelectionMode.value = true
        }
        val current = _selectedBrowserFiles.value.toMutableSet()
        if (current.contains(path)) {
            current.remove(path)
        } else {
            current.add(path)
        }
        _selectedBrowserFiles.value = current
    }

    fun selectAllBrowserFiles() {
        _isBrowserSelectionMode.value = true
        val allFiles = _files.value
        val allPaths = allFiles.map { it.path }.toSet()
        val currentSelection = _selectedBrowserFiles.value

        // If all files are currently selected, deselect them. Otherwise, select all.
        if (currentSelection.size == allPaths.size && currentSelection.containsAll(allPaths)) {
             _selectedBrowserFiles.value = emptySet()
        } else {
             _selectedBrowserFiles.value = allPaths
        }
    }

    fun exitBrowserSelectionMode() {
        _isBrowserSelectionMode.value = false
        _selectedBrowserFiles.value = emptySet()
    }

    fun clearBrowserSelection() {
        _selectedBrowserFiles.value = emptySet()
    }

}

class HomeViewModelFactory(private val repository: FileRepository, private val settingsRepository: SettingsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
