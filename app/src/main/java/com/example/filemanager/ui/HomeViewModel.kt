package com.example.filemanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.filemanager.data.ClipboardOperation
import com.example.filemanager.data.FileModel
import com.example.filemanager.data.FileRepository
import com.example.filemanager.data.FileType
import com.example.filemanager.data.SettingsRepository
import com.example.filemanager.data.StorageInfo
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
import java.io.File

class HomeViewModel(
    private val repository: FileRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val _storageInfo = MutableStateFlow<StorageInfo?>(null)
    val storageInfo: StateFlow<StorageInfo?> = _storageInfo

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _rawFiles = MutableStateFlow<List<FileModel>>(emptyList())

    private val _files = MutableStateFlow<List<FileModel>>(emptyList())
    val files: StateFlow<List<FileModel>> = _files.asStateFlow()

    private val _ghostFiles = MutableStateFlow<List<File>>(emptyList())
    val ghostFiles: StateFlow<List<File>> = _ghostFiles.asStateFlow()

    private val _forecastText = MutableStateFlow("...")
    val forecastText: StateFlow<String> = _forecastText.asStateFlow()

    private val _dailyUsageRate = MutableStateFlow<Long>(0)
    val dailyUsageRate: StateFlow<Long> = _dailyUsageRate.asStateFlow()

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

    private val _clipboardFile = MutableStateFlow<FileModel?>(null)
    val clipboardFile: StateFlow<FileModel?> = _clipboardFile.asStateFlow()

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

    private val _searchResults = MutableStateFlow<List<FileModel>>(emptyList())
    val searchResults: StateFlow<List<FileModel>> = _searchResults.asStateFlow()

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
                _searchResults.value = repository.searchFiles(query)
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
            if (repository.deleteFile(path)) {
                onFinished()
            }
        }
    }

    fun deleteMultipleFiles(paths: List<String>, currentPath: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                paths.forEach { repository.deleteFile(it) }
                loadFiles(currentPath) // Refresh the file list
            } catch (e: Exception) {
                showMessage("Error deleting files: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteFilesAndReloadCategory(paths: List<String>, type: FileType) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                paths.forEach { repository.deleteFile(it) }
                _files.value = repository.getFilesByCategory(type)
            } catch (e: Exception) {
                showMessage("Error deleting files: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun extractFile(file: FileModel, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val destinationPath = file.path.substringBeforeLast(".")
                val success = repository.extractZip(file.path, destinationPath)
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

    private val _trashedFiles = MutableStateFlow<List<com.example.filemanager.data.TrashedFile>>(emptyList())
    // val trashedFiles: StateFlow<List<com.example.filemanager.data.TrashedFile>> = _trashedFiles.asStateFlow() // Replaced by filtered flow

    private val _trashSearchQuery = MutableStateFlow("")
    val trashSearchQuery: StateFlow<String> = _trashSearchQuery.asStateFlow()

    val trashedFiles: StateFlow<List<com.example.filemanager.data.TrashedFile>> = _trashSearchQuery
        .combine(_trashedFiles) { query, files ->
            if (query.isBlank()) {
                files
            } else {
                files.filter { it.name.contains(query, ignoreCase = true) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateTrashSearchQuery(query: String) {
        _trashSearchQuery.value = query
    }

    fun loadTrashedFiles() {
        viewModelScope.launch {
            _trashedFiles.value = repository.getTrashedFiles().sortedByDescending { it.dateDeleted }
        }
    }

    fun restoreFile(trashedFile: com.example.filemanager.data.TrashedFile) {
        viewModelScope.launch {
            if (repository.restoreFile(trashedFile)) {
                loadTrashedFiles()
            }
        }
    }

    fun deleteFilePermanently(trashedFile: com.example.filemanager.data.TrashedFile) {
        viewModelScope.launch {
            if (repository.deleteFilePermanently(trashedFile)) {
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

    fun loadStorageInfo() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Artificial delay removed for speed
                // delay(300)
                _storageInfo.value = repository.getStorageInfo()
            } catch (e: Exception) {
                showMessage("Error loading storage info: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addToClipboard(file: FileModel, operation: ClipboardOperation) {
        _clipboardFile.value = file
        _clipboardOperation.value = operation
        val opName = if (operation == ClipboardOperation.COPY) "Copied" else "Moved"
        showMessage("$opName to clipboard. Go to destination and Paste.")
    }

    fun pasteFile(destinationPath: String, onComplete: () -> Unit) {
        val fileToPaste = _clipboardFile.value ?: return
        val operation = _clipboardOperation.value ?: return

        viewModelScope.launch {
            _isLoading.value = true
            val success = try {
                when (operation) {
                    ClipboardOperation.COPY -> repository.copyFile(fileToPaste.path, destinationPath)
                    ClipboardOperation.MOVE -> repository.moveFile(fileToPaste.path, destinationPath)
                }
            } catch (_: Exception) {
                false
            }

            if (success) {
                if (operation == ClipboardOperation.MOVE) {
                    _clipboardFile.value = null
                    _clipboardOperation.value = null
                }
                loadFiles(destinationPath) 
                onComplete()
                showMessage("File pasted successfully")
            } else {
                showMessage("Failed to paste file")
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

    private val _trashSize = MutableStateFlow<Long>(0)
    val trashSize: StateFlow<Long> = _trashSize.asStateFlow()

    private val _emptyFoldersCount = MutableStateFlow(0)
    val emptyFoldersCount: StateFlow<Int> = _emptyFoldersCount.asStateFlow()

    private val _largeFilesCount = MutableStateFlow(0)

    fun loadDashboardData() {
        viewModelScope.launch {
            val trashSizeDeferred = async { repository.getTrashSize() }
            val emptyFoldersDeferred = async { repository.getEmptyFolders() }
            val largeFilesDeferred = async { repository.getLargeFiles().size }
            val forecastTextDeferred = async { repository.calculateStorageForecast() }

            _trashSize.value = trashSizeDeferred.await()
            val emptyFolders = emptyFoldersDeferred.await()
            _ghostFiles.value = emptyFolders
            _emptyFoldersCount.value = emptyFolders.size
            _largeFilesCount.value = largeFilesDeferred.await()
            _forecastText.value = forecastTextDeferred.await()
        }
    }

    fun deleteGhostFile(file: File) {
        viewModelScope.launch {
            if (repository.deleteFile(file.path)) {
                _ghostFiles.value = _ghostFiles.value.filter { it.path != file.path }
                _emptyFoldersCount.value = _ghostFiles.value.size
                // determine if we should subtract from trash size? No, `deleteFile` moves to trash, so trash size increases.
                // We should reload trash size.
                _trashSize.value = repository.getTrashSize()
            }
        }
    }

    fun deleteAllGhostFiles() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _ghostFiles.value.forEach { file ->
                    repository.deleteFile(file.path)
                }
                _ghostFiles.value = emptyList()
                _emptyFoldersCount.value = 0
                _trashSize.value = repository.getTrashSize()
            } catch (e: Exception) {
                showMessage("Error deleting ghost files: ${e.message}")
            } finally {
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

    fun renameFile(file: FileModel, newName: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (repository.renameFile(file.path, newName)) {
                onSuccess()
                // Refresh specific lists if needed, or just the current path
                // For now, simpler to just let the caller trigger a reload or doing it here if we tracked current path better in VM
                showMessage("File renamed successfully")
            } else {
                showMessage("Failed to rename file")
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


