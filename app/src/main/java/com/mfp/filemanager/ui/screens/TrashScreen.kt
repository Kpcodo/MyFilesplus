package com.mfp.filemanager.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Restore
import androidx.compose.ui.res.stringResource
import com.mfp.filemanager.R
import com.mfp.filemanager.ui.viewmodels.HomeViewModel
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
import com.mfp.filemanager.data.FileType
import com.mfp.filemanager.data.FileUtils
import com.mfp.filemanager.data.trash.TrashedFile

private enum class DialogType {
    NONE,
    RESTORE_SELECTED,
    DELETE_SELECTED,
    RESTORE_ALL,
    EMPTY_TRASH
}

@SuppressLint("UnusedContent")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    viewModel: HomeViewModel,
    showTopBar: Boolean = true,
    onBack: () -> Unit
) {
    val trashedFilesState by viewModel.trashedFiles.collectAsState()
    val loadingState by viewModel.isLoading.collectAsState()

    var selectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<TrashedFile>()) }
    var shownDialog by remember { mutableStateOf(DialogType.NONE) }

    LaunchedEffect(Unit) {
        viewModel.loadTrashedFiles()
    }

    when (shownDialog) {
        DialogType.RESTORE_SELECTED -> {
            ConfirmationDialog(
                title = stringResource(R.string.restore_files_title),
                message = stringResource(R.string.restore_files_message),
                onConfirm = {
                    viewModel.restoreFiles(selectedItems.toList())
                    selectionMode = false
                    selectedItems = setOf()
                    shownDialog = DialogType.NONE
                },
                onDismiss = { shownDialog = DialogType.NONE }
            )
        }
        DialogType.DELETE_SELECTED -> {
            ConfirmationDialog(
                title = stringResource(R.string.delete_permanently_title),
                message = stringResource(R.string.delete_permanently_message),
                onConfirm = {
                    viewModel.deleteFilesPermanently(selectedItems.toList())
                    selectionMode = false
                    selectedItems = setOf()
                    shownDialog = DialogType.NONE
                },
                onDismiss = { shownDialog = DialogType.NONE }
            )
        }
        DialogType.RESTORE_ALL -> {
            ConfirmationDialog(
                title = stringResource(R.string.restore_all_title),
                message = stringResource(R.string.restore_all_message),
                onConfirm = {
                    viewModel.restoreAllFiles()
                    shownDialog = DialogType.NONE
                },
                onDismiss = { shownDialog = DialogType.NONE }
            )
        }
        DialogType.EMPTY_TRASH -> {
            ConfirmationDialog(
                title = stringResource(R.string.empty_trash_title),
                message = stringResource(R.string.empty_trash_message),
                onConfirm = {
                    viewModel.emptyTrash()
                    shownDialog = DialogType.NONE
                },
                onDismiss = { shownDialog = DialogType.NONE }
            )
        }
        DialogType.NONE -> { /* Do nothing */ }
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
                    onRestore = { shownDialog = DialogType.RESTORE_SELECTED },
                    onDeleteForever = { shownDialog = DialogType.DELETE_SELECTED }
                )
            } else if (showTopBar) {
                TrashTopAppBar(
                    onBack = onBack
                )
            }
        }

    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Persistent buttons row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(
                    onClick = { shownDialog = DialogType.RESTORE_ALL },
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.restore_all))
                }
                OutlinedButton(
                    onClick = { shownDialog = DialogType.EMPTY_TRASH },
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.delete_all))
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (loadingState) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (trashedFilesState.isEmpty()) {
                    Text(
                        stringResource(R.string.trash_empty_message),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    LazyColumn {
                        items(trashedFilesState, key = { it.id }) { file ->
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
    onBack: () -> Unit
) {
    TopAppBar(
        title = { Text(stringResource(R.string.trash_screen_title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_desc))
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
                    else -> Icons.AutoMirrored.Filled.InsertDriveFile
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
