package com.mfp.filemanager.ui.screens

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.SubcomposeAsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.request.CachePolicy
import coil.size.Precision
import com.mfp.filemanager.data.clipboard.ClipboardOperation
import com.mfp.filemanager.data.FileModel
import com.mfp.filemanager.data.FileType
import com.mfp.filemanager.data.FileUtils
import com.mfp.filemanager.ui.components.InlineFileMenu
import com.mfp.filemanager.ui.components.FileItemMenu
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import com.mfp.filemanager.ui.animations.bounceClick
import com.mfp.filemanager.ui.animations.animateEnter
import com.mfp.filemanager.ui.viewmodels.HomeViewModel
import com.mfp.filemanager.ui.SortType
import com.mfp.filemanager.ui.SortOrder
import com.mfp.filemanager.ui.ViewType
import androidx.compose.foundation.lazy.itemsIndexed
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

    var selectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<String>()) }
    var fileToRename by remember { mutableStateOf<FileModel?>(null) }
    var fileToDelete by remember { mutableStateOf<FileModel?>(null) }
    var fileToInfo by remember { mutableStateOf<FileModel?>(null) }
    var showBatchRenameDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Load files when the path changes
    LaunchedEffect(path) {
        viewModel.loadFiles(path)
    }

    // Delete Confirmation Dialog
    if (fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete File") },
            text = { Text("Are you sure you want to delete ${fileToDelete?.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        fileToDelete?.let { file ->
                            viewModel.deleteFile(file.path) {
                                viewModel.loadFiles(path)
                            }
                        }
                        fileToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
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
                        viewModel.deleteMultipleFiles(selectedItems.toList(), path)
                        selectionMode = false
                        selectedItems = setOf()
                    },
                    onBatchRename = {
                        showBatchRenameDialog = true
                    }
                )
            } else {
                FileBrowserTopAppBar(viewModel, path, title, onBack, onSearchClick)
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (clipboardFiles.isNotEmpty() && clipboardOperation != null) {
                androidx.compose.material3.BottomAppBar(
                    actions = {
                        TextButton(onClick = { viewModel.clearClipboard() }) { Text("Cancel") }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${clipboardFiles.size} ${if (clipboardFiles.size == 1) "file" else "files"} to ${if (clipboardOperation == ClipboardOperation.COPY) "copy" else "move"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        androidx.compose.material3.Button(
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
        val operationProgress by viewModel.operationProgress.collectAsState()
        
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = { viewModel.loadFiles(path) },
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
                                "delete" -> { fileToDelete = file }
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
                                    Box(modifier = Modifier.animateEnter(delayMillis = index * 50)) {
                                        FileListItem(
                                            file = file,
                                            isSelected = file.path in selectedItems,
                                            selectionMode = selectionMode,
                                            iconSize = iconSize,
                                            isCompact = isCompact,
                                            onClick = {
                                                if (selectionMode) {
                                                    selectedItems = if (file.path in selectedItems) selectedItems - file.path else selectedItems + file.path
                                                } else {
                                                    if (file.isDirectory) onDirectoryClick(file) else onFileClick(file)
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
                                                selectedItems = if (file.path in selectedItems) selectedItems - file.path else selectedItems + file.path
                                            } else {
                                                if (file.isDirectory) onDirectoryClick(file) else onFileClick(file)
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

    if (showBatchRenameDialog) {
        BatchRenameDialog(
            selectedCount = selectedItems.size,
            onDismiss = { showBatchRenameDialog = false },
            onConfirm = { baseName ->
                val selectedFiles = files.filter { it.path in selectedItems }
                viewModel.renameMultipleFiles(selectedFiles, baseName) {
                    viewModel.loadFiles(path)
                    selectionMode = false
                    selectedItems = setOf()
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

    if (fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete File") },
            text = { Text("Are you sure you want to delete ${fileToDelete?.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val file = fileToDelete!!
                        fileToDelete = null
                        viewModel.deleteFile(file.path) {
                            viewModel.loadFiles(path)
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (fileToInfo != null) {
        val file = fileToInfo!!
        AlertDialog(
            onDismissRequest = { fileToInfo = null },
            title = { Text("File Info") },
            text = {
                androidx.compose.foundation.layout.Column {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: FileModel,
    isSelected: Boolean,
    selectionMode: Boolean,
    iconSize: Float,
    isCompact: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuAction: (String) -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .background(backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = if (isCompact) 2.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileThumbnail(file, modifier = Modifier.size(48.dp * iconSize), iconSize = iconSize)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val subtitle = if (file.isDirectory) {
                    "${file.itemCount} items"
                } else {
                    "${FileUtils.formatSize(file.size)} | ${FileUtils.formatDate(file.dateModified)}"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() }, modifier = Modifier.padding(start = 16.dp))
            } else {
                IconButton(onClick = { showMenu = !showMenu }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        modifier = Modifier.bounceClick()
                    )
                }
            }
        }
        
        if (showMenu) {
            InlineFileMenu(
                onMove = { showMenu = false; onMenuAction("move") },
                onCopy = { showMenu = false; onMenuAction("copy") },
                onRename = { showMenu = false; onMenuAction("rename") },
                onDelete = { showMenu = false; onMenuAction("delete") },
                onExtract = if (file.type == FileType.ARCHIVE) { { showMenu = false; onMenuAction("extract") } } else null,
                onInfo = { 
                    onMenuAction("info") 
                    // Keep menu open potentially to avoid race condition, or just debugging.
                    showMenu = false 
                },
                onShare = { showMenu = false; onMenuAction("share") }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(
    file: FileModel,
    isSelected: Boolean,
    selectionMode: Boolean,
    iconSize: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuAction: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // Top Visual Section
            val adjustedIconSize = if (file.isDirectory) iconSize * 2.5f else iconSize
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f) // Square top section
                    .background(if (file.isDirectory) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                FileThumbnail(
                    file = file,
                    modifier = Modifier.size(64.dp * adjustedIconSize),
                    iconSize = adjustedIconSize,
                    transparentBackground = true // New param to remove inner box background if needed
                )
            }

            // Bottom Info Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (file.isDirectory) "${file.itemCount} items" else FileUtils.formatSize(file.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (selectionMode) {
                    Checkbox(checked = isSelected, onCheckedChange = { onClick() })
                } else {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                        }
                        FileItemMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            onMove = { showMenu = false; onMenuAction("move") },
                            onCopy = { showMenu = false; onMenuAction("copy") },
                            onRename = { showMenu = false; onMenuAction("rename") },
                            onDelete = { showMenu = false; onMenuAction("delete") },
                            onExtract = if (file.type == FileType.ARCHIVE) { { showMenu = false; onMenuAction("extract") } } else null,
                            onInfo = { showMenu = false; onMenuAction("info") },
                            onShare = { showMenu = false; onMenuAction("share") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileThumbnail(
    file: FileModel,
    modifier: Modifier = Modifier,
    iconSize: Float = 1.0f,
    transparentBackground: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (transparentBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (file.isDirectory) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = file.name,
                modifier = Modifier.size(32.dp * iconSize),
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            when (file.type) {
                FileType.IMAGE -> {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(file.path))
                            .crossfade(false)
                            .build(),
                        contentDescription = file.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = file.name,
                                modifier = Modifier.size(32.dp * iconSize),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        error = {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = file.name,
                                modifier = Modifier.size(32.dp * iconSize),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
                FileType.VIDEO -> {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(file.path))
                            .videoFrameMillis(0)
                            .size(256)
                            .precision(Precision.INEXACT)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .crossfade(false)
                            .build(),
                        contentDescription = file.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Icon(
                                imageVector = Icons.Default.VideoFile,
                                contentDescription = file.name,
                                modifier = Modifier.size(32.dp * iconSize),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        error = {
                            Icon(
                                imageVector = Icons.Default.VideoFile,
                                contentDescription = file.name,
                                modifier = Modifier.size(32.dp * iconSize),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
                FileType.AUDIO -> {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = file.name,
                        modifier = Modifier.size(32.dp * iconSize),
                        tint = Color(0xFF8E24AA) // Purple for Audio
                    )
                }
                FileType.APK -> {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = file.name,
                        modifier = Modifier.size(32.dp * iconSize),
                        tint = Color(0xFF4CAF50) // Green for Android
                    )
                }
                FileType.DOCUMENT -> {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = file.name,
                        modifier = Modifier.size(32.dp * iconSize),
                        tint = Color(0xFFFBC02D) // Yellow for Docs
                    )
                }
                FileType.ARCHIVE -> {
                    Icon(
                        imageVector = Icons.Default.FolderZip, // Or fallback if not available
                        contentDescription = file.name,
                        modifier = Modifier.size(32.dp * iconSize),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = file.name,
                        modifier = Modifier.size(32.dp * iconSize),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        title = { Text(title ?: if (isRoot) "Internal Storage" else path.substringAfterLast("/")) },
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
    onBatchRename: () -> Unit
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
                Icon(Icons.Default.SelectAll, contentDescription = "Select All")
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy")
            }
            IconButton(onClick = onMove) {
                Icon(Icons.Default.DriveFileMove, contentDescription = "Move")
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
