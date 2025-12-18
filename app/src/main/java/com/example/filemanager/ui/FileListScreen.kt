package com.example.filemanager.ui

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
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.VideoFile
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
import com.example.filemanager.data.ClipboardOperation
import com.example.filemanager.data.FileModel
import com.example.filemanager.data.FileType
import com.example.filemanager.data.FileUtils
import com.example.filemanager.ui.components.FileItemMenu

import androidx.compose.material3.pulltorefresh.PullToRefreshBox

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
                    onDelete = {
                        viewModel.deleteFilesAndReloadCategory(selectedItems.toList(), fileType)
                        selectionMode = false
                        selectedItems = setOf()
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
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { viewModel.loadFilesByCategory(fileType) },
            modifier = Modifier.padding(paddingValues)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading && files.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    val onMenuAction: (FileModel, String) -> Unit = { file, action ->
                        when (action) {
                            "move" -> viewModel.addToClipboard(file, ClipboardOperation.MOVE)
                            "copy" -> viewModel.addToClipboard(file, ClipboardOperation.COPY)
                            "rename" -> showRenameDialog = file
                            "delete" -> viewModel.deleteFilesAndReloadCategory(listOf(file.path), fileType)
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
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        items(files, key = { it.id }) { file -> 
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailedFileItem(
    file: FileModel, 
    isSelected: Boolean, 
    selectionMode: Boolean, 
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuAction: (String) -> Unit,
    allowDelete: Boolean = true
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            if (file.type == FileType.IMAGE || file.type == FileType.VIDEO || file.type == FileType.AUDIO) {
                val imageModel = ContentUris.withAppendedId(
                    MediaStore.Files.getContentUri("external"),
                    file.id
                )

                val errorIcon = when (file.type) {
                    FileType.VIDEO -> Icons.Default.VideoFile
                    FileType.AUDIO -> Icons.Default.MusicNote
                    else -> Icons.Default.Description
                }

                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageModel)
                        .crossfade(true)
                        .build(),
                    contentDescription = file.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = { CircularProgressIndicator(modifier = Modifier.size(24.dp)) },
                    error = {
                        Icon(
                            imageVector = errorIcon,
                            contentDescription = file.name,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            } else {
                val icon = when (file.type) {
                    FileType.ARCHIVE -> Icons.Default.FolderZip
                    else -> Icons.Default.Description
                }
                Icon(icon, contentDescription = file.name, tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${FileUtils.formatSize(file.size)} • ${FileUtils.formatDate(file.dateModified)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (selectionMode) {
             Checkbox(
                 checked = isSelected, 
                 onCheckedChange = { onClick() },
                 modifier = Modifier.padding(start = 8.dp)
             )
        } else {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                }
                FileItemMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    onMove = { showMenu = false; onMenuAction("move") },
                    onCopy = { showMenu = false; onMenuAction("copy") },
                    onRename = { showMenu = false; onMenuAction("rename") },
                    onDelete = if (allowDelete) { { showMenu = false; onMenuAction("delete") } } else null,
                    onInfo = { showMenu = false; onMenuAction("info") },
                    onShare = { showMenu = false; onMenuAction("share") }
                )
            }
        }
    }
}
