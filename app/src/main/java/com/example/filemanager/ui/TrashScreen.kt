package com.example.filemanager.ui

import android.content.ContentUris
import android.provider.MediaStore
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.res.stringResource
import com.example.filemanager.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.filemanager.data.FileModel
import com.example.filemanager.data.FileType
import com.example.filemanager.data.FileUtils
import com.example.filemanager.data.TrashedFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    viewModel: HomeViewModel,
    showTopBar: Boolean = true,
    onBack: () -> Unit
) {
    val trashedFiles by viewModel.trashedFiles.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()


    var selectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<TrashedFile>()) }
    var showRestoreConfirmation by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }


    LaunchedEffect(Unit) {
        viewModel.loadTrashedFiles()
    }

    if (showRestoreConfirmation) {
        ConfirmationDialog(
            title = stringResource(R.string.restore_files_title),
            message = stringResource(R.string.restore_files_message),
            onConfirm = {
                selectedItems.forEach { viewModel.restoreFile(it) }
                selectionMode = false
                selectedItems = setOf()
                showRestoreConfirmation = false
            },
            onDismiss = { showRestoreConfirmation = false }
        )
    }

    if (showDeleteConfirmation) {
        ConfirmationDialog(
            title = stringResource(R.string.delete_permanently_title),
            message = stringResource(R.string.delete_permanently_message),
            onConfirm = {
                selectedItems.forEach { viewModel.deleteFilePermanently(it) }
                selectionMode = false
                selectedItems = setOf()
                showDeleteConfirmation = false
            },
            onDismiss = { showDeleteConfirmation = false }
        )
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TrashSelectionTopAppBar(
                    selectedItemCount = selectedItems.size,
                    onClearSelection = {
                        selectionMode = false
                        selectedItems = setOf()
                    },
                    onRestore = { showRestoreConfirmation = true },
                    onDeleteForever = { showDeleteConfirmation = true }
                )
            } else if (showTopBar) {
                TrashTopAppBar(
                    onBack = onBack,
                    onEmptyTrash = {
                        viewModel.emptyTrash()
                    }
                )
            }
        }

    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (trashedFiles.isEmpty()) {
                Text(
                    stringResource(R.string.trash_empty_message),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn {
                    items(trashedFiles, key = { it.id }) { file ->
                        TrashedItem(
                            file = file,
                            isSelected = file in selectedItems,
                            selectionMode = selectionMode,
                            onClick = {
                                if (selectionMode) {
                                    selectedItems = if (file in selectedItems) selectedItems - file else selectedItems + file
                                    if (selectedItems.isEmpty()) selectionMode = false
                                } else {
                                    // Single tap could open restore/delete dialog for that one item
                                }
                            },
                            onLongClick = {
                                if (!selectionMode) selectionMode = true
                                selectedItems = selectedItems + file
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable

fun TrashTopAppBar(
    onBack: () -> Unit,
    onEmptyTrash: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(stringResource(R.string.trash_screen_title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_desc))
            }
        },
        actions = {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options_desc))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_empty_trash)) },
                    onClick = {
                        onEmptyTrash()
                        showMenu = false
                    }
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashSelectionTopAppBar(
    selectedItemCount: Int,
    onClearSelection: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    TopAppBar(
        title = { Text(stringResource(R.string.selected_count, selectedItemCount)) },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.clear_selection_desc))
            }
        },
        actions = {
            IconButton(onClick = onRestore) {
                Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.action_restore))
            }
            IconButton(onClick = onDeleteForever) {
                Icon(Icons.Default.DeleteForever, contentDescription = stringResource(R.string.action_delete_forever))
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrashedItem(
    file: TrashedFile,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            if (file.type == FileType.IMAGE || file.type == FileType.VIDEO) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(java.io.File(file.trashPath))
                        .crossfade(true)
                        .build(),
                    contentDescription = file.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = { CircularProgressIndicator(modifier = Modifier.size(24.dp)) },
                    error = { Icon(Icons.Default.BrokenImage, contentDescription = null) }
                )
            } else {
                val icon = when (file.type) {
                    FileType.AUDIO -> Icons.Default.MusicNote
                    FileType.ARCHIVE -> Icons.Default.FolderZip
                    FileType.DOCUMENT -> Icons.Default.Description
                    else -> Icons.Default.InsertDriveFile
                }
                Icon(icon, contentDescription = file.name, tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                stringResource(R.string.original_path_label, file.originalPath.substringBeforeLast("/")),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
            Text(
                stringResource(R.string.deleted_date_label, FileUtils.formatDate(file.dateDeleted)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selectionMode) {
            Checkbox(checked = isSelected, onCheckedChange = { onClick() }, modifier = Modifier.padding(start = 16.dp))
        }
    }
}
