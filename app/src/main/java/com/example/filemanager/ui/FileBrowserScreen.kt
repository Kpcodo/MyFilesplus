package com.example.filemanager.ui

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
import androidx.compose.material.icons.filled.VideoFile
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
import com.example.filemanager.data.ClipboardOperation
import com.example.filemanager.data.FileModel
import com.example.filemanager.data.FileType
import com.example.filemanager.data.FileUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    viewModel: HomeViewModel,
    path: String,
    onBack: () -> Unit,
    onFileClick: (FileModel) -> Unit,
    onDirectoryClick: (FileModel) -> Unit,
    onSearchClick: () -> Unit
) {
    val files by viewModel.files.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val viewType by viewModel.viewType.collectAsState()
    val iconSize by viewModel.iconSize.collectAsState()
    val clipboardFile by viewModel.clipboardFile.collectAsState()
    val clipboardOperation by viewModel.clipboardOperation.collectAsState()

    var selectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<String>()) }
    var fileToRename by remember { mutableStateOf<FileModel?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
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
                    onDelete = {
                        viewModel.deleteMultipleFiles(selectedItems.toList(), path)
                        selectionMode = false
                        selectedItems = setOf()
                    }
                )
            } else {
                FileBrowserTopAppBar(viewModel, path, onBack, onSearchClick)
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (clipboardFile != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.pasteFile(path) {
                            // Success toast or clear
                        }
                    },
                    icon = { Icon(Icons.Default.ContentPaste, contentDescription = "Paste") },
                    text = { Text("Paste ${if (clipboardOperation == ClipboardOperation.MOVE) "Move" else "Copy"}") }
                )
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { viewModel.loadFiles(path) },
            modifier = Modifier.padding(padding)
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                if (isLoading && files.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    val onMenuAction: (FileModel, String) -> Unit = { file, action ->
                        when (action) {
                            "move" -> viewModel.addToClipboard(file, ClipboardOperation.MOVE)
                            "copy" -> viewModel.addToClipboard(file, ClipboardOperation.COPY)
                            "rename" -> { fileToRename = file }
                            "delete" -> viewModel.deleteFile(file.path) { viewModel.loadFiles(path) }
                            "info" -> {}
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
                        LazyColumn {
                            items(files, key = { it.path }) { file ->
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(backgroundColor)
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
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More Options")
            }
            FileItemMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                onMove = { showMenu = false; onMenuAction("move") },
                onCopy = { showMenu = false; onMenuAction("copy") },
                onRename = { showMenu = false; onMenuAction("rename") },
                onDelete = { showMenu = false; onMenuAction("delete") },
                onInfo = { showMenu = false; onMenuAction("info") },
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
    onBack: () -> Unit,
    onSearchClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val sortOrder by viewModel.sortOrder.collectAsState()
    val isRoot = path == FileUtils.getInternalStoragePath()

    TopAppBar(
        title = { Text(if (isRoot) "Internal Storage" else path.substringAfterLast("/")) },
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
    onDelete: () -> Unit
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
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    )
}

@Composable
fun FileItemMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    onShare: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(text = { Text("Move") }, onClick = onMove)
        DropdownMenuItem(text = { Text("Copy") }, onClick = onCopy)
        DropdownMenuItem(text = { Text("Rename") }, onClick = onRename)
        DropdownMenuItem(text = { Text("Share") }, onClick = onShare)
        DropdownMenuItem(text = { Text("Delete") }, onClick = onDelete)
        DropdownMenuItem(text = { Text("Info") }, onClick = onInfo)
    }
}


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
