package com.mfp.filemanager.ui.screens 

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mfp.filemanager.data.FileModel
import com.mfp.filemanager.data.FileType
import com.mfp.filemanager.data.FileUtils
import com.mfp.filemanager.data.clipboard.ClipboardOperation
import com.mfp.filemanager.ui.SortOrder
import com.mfp.filemanager.ui.SortType
import com.mfp.filemanager.ui.ViewType
import com.mfp.filemanager.ui.animations.animateEnter
import com.mfp.filemanager.ui.animations.bounceClick
import com.mfp.filemanager.ui.components.*
import com.mfp.filemanager.ui.viewmodels.HomeViewModel
import com.mfp.filemanager.ui.components.FileListItem
import com.mfp.filemanager.ui.components.FileGridItem
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    viewModel: HomeViewModel,
    path: String,
    title: String? = null, // Add title parameter
    onBack: () -> Unit,
    onFileClick: (FileModel) -> Unit,
    onDirectoryClick: (FileModel) -> Unit,
    onSearchClick: () -> Unit
) {
    val files by viewModel.files.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val viewType by viewModel.viewType.collectAsState()
    val iconSize by viewModel.iconSize.collectAsState()
    val clipboardFiles by viewModel.clipboardFiles.collectAsState()
    val clipboardOperation by viewModel.clipboardOperation.collectAsState()
    val operationStatus by viewModel.operationStatus.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val selectionMode by viewModel.isBrowserSelectionMode.collectAsState()
    val selectedItems by viewModel.selectedBrowserFiles.collectAsState()

    var fileToRename by remember { mutableStateOf<FileModel?>(null) }
    var fileToInfo by remember { mutableStateOf<FileModel?>(null) }
    var showBatchRenameDialog by remember { mutableStateOf(false) }

    val snackBarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Load files when the path changes
    LaunchedEffect(path) {
        viewModel.loadFiles(path)
    }



    Scaffold(
        topBar = {
            if (selectionMode) {
                SelectionTopAppBar(
                    selectedItemCount = selectedItems.size,
                    onClearSelection = {
                        viewModel.exitBrowserSelectionMode()
                    },
                    onSelectAll = {
                        if (selectedItems.size == files.size) {
                            viewModel.clearBrowserSelection()
                        } else {
                            viewModel.selectAllBrowserFiles()
                        }
                    },
                    onCopy = {
                        val selectedFiles = files.filter { it.path in selectedItems }
                        viewModel.addToClipboard(selectedFiles, ClipboardOperation.COPY)
                        viewModel.exitBrowserSelectionMode()
                    },
                    onMove = {
                        val selectedFiles = files.filter { it.path in selectedItems }
                        viewModel.addToClipboard(selectedFiles, ClipboardOperation.MOVE)
                        viewModel.exitBrowserSelectionMode()
                    },
                    onDelete = {
                        viewModel.deleteSelectedBrowserFiles(path)
                    },
                    onBatchRename = {
                        showBatchRenameDialog = true
                    },
                    isAllSelected = selectedItems.size == files.size && files.isNotEmpty()
                )
            } else {
                FileBrowserTopAppBar(viewModel, path, title, onBack, onSearchClick)
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackBarHostState) },
        bottomBar = {
            if (clipboardFiles.isNotEmpty() && clipboardOperation != null) {
                BottomAppBar(
                    actions = {
                        TextButton(onClick = { viewModel.clearClipboard() }) { Text("Cancel") }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${clipboardFiles.size} ${if (clipboardFiles.size == 1) "file" else "files"} to ${if (clipboardOperation == ClipboardOperation.COPY) "copy" else "move"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = {
                                android.util.Log.d("FileBrowserScreen", "${if (clipboardOperation == ClipboardOperation.COPY) "Paste" else "Move Here"} clicked! path=$path, filesCount=${clipboardFiles.size}")
                                viewModel.pasteFile(path)
                            }
                        ) {
                            Text(if (clipboardOperation == ClipboardOperation.COPY) "Paste" else "Move Here")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.loadFiles(path, isRefresh = true) },
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isLoading && files.isEmpty()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        val onMenuAction: (FileModel, String) -> Unit = { file, action ->
                            when (action) {
                                "move" -> viewModel.addSingleToClipboard(file, ClipboardOperation.MOVE)
                                "copy" -> viewModel.addSingleToClipboard(file, ClipboardOperation.COPY)
                                "rename" -> { fileToRename = file }
                                "delete" -> viewModel.deleteFile(file.path, path)
                                "extract" -> viewModel.extractFile(file) { viewModel.loadFiles(path) }
                                "info" -> { 
                                    fileToInfo = file 
                                }
                                "share" -> {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", File(file.path))
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = file.mimeType ?: "*/*"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share file"))
                                }
                            }
                        }

                        if (viewType == ViewType.LIST || viewType == ViewType.COMPACT) {
                            val isCompact = viewType == ViewType.COMPACT
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(files, key = { _, file -> file.path }) { index, file ->
                                    // Cap delay to avoid long waits for items at bottom of list
                                    val delay = (index % 10) * 30 
                                    Box(modifier = Modifier.animateEnter(delayMillis = delay)) {
                                    FileListItem(
                                        file = file,
                                        isSelected = file.path in selectedItems,
                                        selectionMode = selectionMode,
                                        iconSize = iconSize,
                                        isCompact = isCompact,
                                        onClick = {
                                            if (selectionMode) {
                                                viewModel.toggleBrowserSelection(file.path)
                                            } else {
                                                if (file.isDirectory) onDirectoryClick(file) else onFileClick(file)
                                            }
                                        },
                                        onLongClick = {
                                            viewModel.toggleBrowserSelection(file.path)
                                        },
                                        onMenuAction = { action -> onMenuAction(file, action) }
                                    )
                                }
                            }
                            }
                        } else {
                            val minSize = if (viewType == ViewType.LARGE_GRID) 200.dp else 128.dp
                            LazyVerticalGrid(columns = GridCells.Adaptive(minSize = minSize)) {
                                items(files, key = { it.path }) { file ->
                                    FileGridItem(
                                        file = file,
                                        isSelected = file.path in selectedItems,
                                        selectionMode = selectionMode,
                                        iconSize = iconSize,
                                        onClick = {
                                            if (selectionMode) {
                                                viewModel.toggleBrowserSelection(file.path)
                                            } else {
                                                if (file.isDirectory) onDirectoryClick(file) else onFileClick(file)
                                            }
                                        },
                                        onLongClick = {
                                            viewModel.toggleBrowserSelection(file.path)
                                        },
                                        onMenuAction = { action -> onMenuAction(file, action) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBatchRenameDialog) {
        BatchRenameDialog(
            selectedCount = selectedItems.size,
            onDismiss = { showBatchRenameDialog = false },
            onConfirm = { baseName ->
                val selectedFiles = files.filter { it.path in selectedItems }
                viewModel.renameMultipleFiles(selectedFiles, baseName) {
                    viewModel.loadFiles(path)
                    viewModel.exitBrowserSelectionMode()
                }
                showBatchRenameDialog = false
            }
        )
    }

    if (fileToRename != null) {
        RenameDialog(
            file = fileToRename!!,
            onDismiss = { fileToRename = null },
            onConfirm = { newName ->
                viewModel.renameFile(fileToRename!!, newName) {
                    viewModel.loadFiles(path)
                }
                fileToRename = null
            }
        )
    }



    if (fileToInfo != null) {
        val file = fileToInfo!!
        AlertDialog(
            onDismissRequest = { fileToInfo = null },
            title = { Text("File Info") },
            text = {
                Column {
                    Text("Name: ${file.name}")
                    Text("Path: ${file.path}")
                    Text("Size: ${FileUtils.formatSize(file.size)}")
                    Text("Date: ${FileUtils.formatDate(file.dateModified)}")
                    Text("Type: ${file.mimeType ?: "Unknown"}")
                }
            },
            confirmButton = {
                TextButton(onClick = { fileToInfo = null }) {
                    Text("Close")
                }
            }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileBrowserTopAppBar(
    viewModel: HomeViewModel,
    path: String,
    title: String? = null,
    onBack: () -> Unit,
    onSearchClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val sortOrder by viewModel.sortOrder.collectAsState()
    val isRoot = path == FileUtils.getInternalStoragePath()

    TopAppBar(
        title = { 
            Text(
                text = title ?: if (isRoot) "Internal Storage" else path.substringAfterLast("/"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee()
            ) 
        },
        navigationIcon = {
            if (!isRoot) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More Options")
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                // Sorting Options
                DropdownMenuItem(
                    text = { Text("Sort by Name") },
                    onClick = { viewModel.changeSorting(SortType.NAME); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.SortByAlpha, null) }
                )
                DropdownMenuItem(
                    text = { Text("Sort by Date") },
                    onClick = { viewModel.changeSorting(SortType.DATE); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.DateRange, null) }
                )
                DropdownMenuItem(
                    text = { Text("Sort by Size") },
                    onClick = { viewModel.changeSorting(SortType.SIZE); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.DataUsage, null) }
                )
                // Sort Order
                DropdownMenuItem(
                    text = { Text(if (sortOrder == SortOrder.ASCENDING) "Ascending" else "Descending") },
                    onClick = {
                        viewModel.changeSortOrder(if (sortOrder == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING)
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            if (sortOrder == SortOrder.ASCENDING) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            null
                        )
                    }
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopAppBar(
    selectedItemCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onBatchRename: () -> Unit,
    isAllSelected: Boolean = false
) {
    TopAppBar(
        title = { Text("$selectedItemCount selected") },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Default.Close, contentDescription = "Clear Selection")
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = if (isAllSelected) "Unselect All" else "Select All",
                    tint = if (isAllSelected) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy")
            }
            IconButton(onClick = onMove) {
                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move")
            }
            IconButton(onClick = onBatchRename) {
                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Batch Rename")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    )
}

// FileItemMenu Removed - Replaced by InlineFileMenu



@Composable
fun RenameDialog(
    file: FileModel,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember { mutableStateOf(file.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            TextField(
                value = newName,
                onValueChange = { newName = it },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(newName) },
                enabled = newName.isNotEmpty() && newName != file.name
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BatchRenameDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var baseName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Batch Rename") },
        text = {
            Column {
                Text("Rename $selectedCount files as 'Name (1)', 'Name (2)', etc.")
                Spacer(modifier = Modifier.height(16.dp))
                TextField(
                    value = baseName,
                    onValueChange = { baseName = it },
                    label = { Text("Base Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(baseName) },
                enabled = baseName.isNotBlank()
            ) {
                Text("Rename All")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
