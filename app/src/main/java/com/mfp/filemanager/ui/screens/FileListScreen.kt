package com.mfp.filemanager.ui.screens

import android.content.ContentUris
import android.provider.MediaStore
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.mfp.filemanager.data.clipboard.ClipboardOperation
import com.mfp.filemanager.data.FileModel
import com.mfp.filemanager.ui.viewmodels.HomeViewModel
import com.mfp.filemanager.data.FileType
import com.mfp.filemanager.data.FileUtils
import com.mfp.filemanager.ui.components.DetailedFileItem
import com.mfp.filemanager.ui.components.InlineFileMenu


import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import com.mfp.filemanager.ui.animations.bounceClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    viewModel: HomeViewModel,
    fileType: FileType,
    onBack: () -> Unit,
    onSearchClick: () -> Unit,
    onFileClick: (FileModel) -> Unit
) {
    val files by viewModel.files.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showExtractDialog by remember { mutableStateOf<FileModel?>(null) }
    
    var selectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<String>()) }
    
    // Dialog States
    var showRenameDialog by remember { mutableStateOf<FileModel?>(null) }
    var showInfoDialog by remember { mutableStateOf<FileModel?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val swipeDeleteEnabledSetting by viewModel.swipeDeleteEnabled.collectAsState()
    val isSwipeNavigationEnabled by viewModel.isSwipeNavigationEnabled.collectAsState()
    val swipeDeleteEnabled = swipeDeleteEnabledSetting && !isSwipeNavigationEnabled
    val swipeDeleteDirection by viewModel.swipeDeleteDirection.collectAsState()

    LaunchedEffect(fileType) {
        viewModel.loadFilesByCategory(fileType)
        selectionMode = false
        selectedItems = setOf()
    }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    
    if (showRenameDialog != null) {
        var newName by remember { mutableStateOf(showRenameDialog?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename") },
            text = {
                androidx.compose.material3.TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Name") }
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showRenameDialog?.let { file ->
                        viewModel.renameFile(file, newName) {
                            showRenameDialog = null
                            viewModel.loadFilesByCategory(fileType)
                        }
                    }
                }) { Text("Rename") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showRenameDialog = null }) { Text("Cancel") }
            }
        )
    }

    if (showInfoDialog != null) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = null },
            title = { Text("File Info") },
            text = {
                Column {
                    Text("Name: ${showInfoDialog?.name}")
                    Text("Path: ${showInfoDialog?.path}")
                    Text("Size: ${FileUtils.formatSize(showInfoDialog?.size ?: 0)}")
                    Text("Date: ${FileUtils.formatDate(showInfoDialog?.dateModified ?: 0)}")
                    Text("Type: ${showInfoDialog?.mimeType ?: "Unknown"}")
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showInfoDialog = null }) { Text("Close") }
            }
        )
    }

    if (showExtractDialog != null) {
        AlertDialog(
            onDismissRequest = { showExtractDialog = null },
            title = { Text("Extract File") },
            text = { Text("Do you want to extract ${showExtractDialog?.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val fileToExtract = showExtractDialog
                        showExtractDialog = null
                        if (fileToExtract != null) {
                            viewModel.extractFile(fileToExtract) {
                                viewModel.loadFilesByCategory(fileType)
                            }
                        }
                    }
                ) {
                    Text("Extract")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExtractDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                 SelectionTopAppBar(
                    selectedItemCount = selectedItems.size,
                    onClearSelection = {
                        selectionMode = false
                        selectedItems = setOf()
                    },
                    onSelectAll = {
                        selectedItems = if (selectedItems.size == files.size) {
                            setOf()
                        } else {
                            files.map { it.path }.toSet()
                        }
                    },
                    onCopy = {
                        val selectedFiles = files.filter { it.path in selectedItems }
                        viewModel.addToClipboard(selectedFiles, ClipboardOperation.COPY)
                        selectionMode = false
                        selectedItems = setOf()
                    },
                    onMove = {
                        val selectedFiles = files.filter { it.path in selectedItems }
                        viewModel.addToClipboard(selectedFiles, ClipboardOperation.MOVE)
                        selectionMode = false
                        selectedItems = setOf()
                    },
                    onDelete = {
                        viewModel.deleteFilesAndReloadCategory(selectedItems.toList(), fileType)
                        selectionMode = false
                        selectedItems = setOf()
                    },
                    onBatchRename = {
                        // Batch rename not yet implemented for categories, but needed for compilation
                    }
                 )
            } else {
                TopAppBar(
                    title = { Text(text = fileType.name) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    IconButton(onClick = onSearchClick) {
                         Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        val operationProgress by viewModel.operationProgress.collectAsState()
        
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = { viewModel.loadFilesByCategory(fileType) },
                modifier = Modifier.fillMaxSize()
            ) {
             // ... content ... 
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading && files.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    val onMenuAction: (FileModel, String) -> Unit = { file, action ->
                        when (action) {
                            "move" -> viewModel.addSingleToClipboard(file, ClipboardOperation.MOVE)
                            "copy" -> viewModel.addSingleToClipboard(file, ClipboardOperation.COPY)
                            "rename" -> showRenameDialog = file
                            "delete" -> viewModel.deleteFilesAndReloadCategory(listOf(file.path), fileType)
                            "extract" -> showExtractDialog = file
                            "info" -> showInfoDialog = file
                            "share" -> {
                                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", java.io.File(file.path))
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = file.mimeType ?: "*/*"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, "Share file"))
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 0.dp) // Reset horizontal padding from Box
                    ) {
                        items(files, key = { it.id }) { file ->
                             // ... existing swipe delete item code ...
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (!swipeDeleteEnabled) return@rememberSwipeToDismissBoxState false
                                    
                                    val isCorrectDirection = (swipeDeleteDirection == 0 && value == SwipeToDismissBoxValue.EndToStart) ||
                                            (swipeDeleteDirection == 1 && value == SwipeToDismissBoxValue.StartToEnd)

                                    if (isCorrectDirection) {
                                        viewModel.deleteFilesAndReloadCategory(listOf(file.path), fileType)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "Item moved to Bin",
                                                actionLabel = "Undo",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.undoDelete(file.path) {
                                                    viewModel.loadFilesByCategory(fileType)
                                                }
                                            }
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = swipeDeleteEnabled && swipeDeleteDirection == 1 && !selectionMode,
                                enableDismissFromEndToStart = swipeDeleteEnabled && swipeDeleteDirection == 0 && !selectionMode,
                                backgroundContent = {
                                    val color = if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) Color.Transparent else Color.Red
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(color, RoundedCornerShape(12.dp))
                                            .padding(horizontal = 24.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }
                            ) {
                                DetailedFileItem(
                                    file = file,
                                    isSelected = file.path in selectedItems,
                                    selectionMode = selectionMode,
                                    onClick = {
                                        if (selectionMode) {
                                            selectedItems = if (file.path in selectedItems) selectedItems - file.path else selectedItems + file.path
                                            if (selectedItems.isEmpty()) selectionMode = false
                                        } else {
                                            onFileClick(file)
                                        }
                                    },
                                    onLongClick = {
                                        if (!selectionMode) selectionMode = true
                                        selectedItems = selectedItems + file.path
                                    },
                                    onMenuAction = { action -> onMenuAction(file, action) }
                                )
                            }
                        }
                    }
                }
            }
            }
            
            if (operationProgress is com.mfp.filemanager.ui.viewmodels.HomeViewModel.OperationProgressState.Active) {
                val state = operationProgress as com.mfp.filemanager.ui.viewmodels.HomeViewModel.OperationProgressState.Active
                val title = if (state.operation == ClipboardOperation.COPY) {
                    "Copying ${state.currentFileIndex}/${state.totalFiles}: ${state.file.name}"
                } else {
                    "Moving ${state.currentFileIndex}/${state.totalFiles}: ${state.file.name}"
                }
                
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    com.mfp.filemanager.ui.components.OperationProgressCard(
                        operationTitle = title,
                        speed = "${FileUtils.formatSize(state.speedBytesPerSec)}/s",
                        progress = state.progress,
                        onCancel = { viewModel.cancelOperation() }
                    )
                }
            }
        }
    }
}

