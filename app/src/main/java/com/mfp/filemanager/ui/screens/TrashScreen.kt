package com.mfp.filemanager.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import com.mfp.filemanager.R
import com.mfp.filemanager.data.FileModel
import com.mfp.filemanager.data.FileType
import com.mfp.filemanager.data.FileUtils
import com.mfp.filemanager.data.trash.TrashedFile
import com.mfp.filemanager.ui.components.*
import com.mfp.filemanager.ui.viewmodels.HomeViewModel

private enum class DialogType {
    NONE,
    DELETE_SINGLE,
    RESTORE_SINGLE,
    RESTORE_ALL,
    EMPTY_TRASH,
    DELETE_SELECTED
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    viewModel: HomeViewModel,
    showTopBar: Boolean = true,
    onBack: () -> Unit
) {
    val trashedFilesState by viewModel.trashedFiles.collectAsState()
    val loadingState by viewModel.isLoading.collectAsState()
    val operationStatus by viewModel.operationStatus.collectAsState()
    val selectionMode by viewModel.isTrashSelectionMode.collectAsState()
    val selectedFiles by viewModel.selectedTrashFiles.collectAsState()

    var shownDialog by remember { mutableStateOf(DialogType.NONE) }
    var fileToDelete by remember { mutableStateOf<TrashedFile?>(null) }
    var fileToRestore by remember { mutableStateOf<TrashedFile?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadTrashedFiles()
    }

    when (shownDialog) {
        DialogType.RESTORE_SINGLE -> {
            fileToRestore?.let { file ->
                ConfirmationDialog(
                    title = "Restore File",
                    message = "Are you sure you want to restore ${file.name} to its original location?",
                    onConfirm = {
                        viewModel.restoreFiles(listOf(file))
                        shownDialog = DialogType.NONE
                        fileToRestore = null
                    },
                    onDismiss = {
                        shownDialog = DialogType.NONE
                        fileToRestore = null
                    }
                )
            }
        }
        DialogType.DELETE_SINGLE -> {
            fileToDelete?.let { file ->
                ConfirmationDialog(
                    title = stringResource(R.string.delete_permanently_title),
                    message = stringResource(R.string.delete_permanently_message),
                    onConfirm = {
                        viewModel.deleteFilesPermanently(listOf(file))
                        shownDialog = DialogType.NONE
                        fileToDelete = null
                    },
                    onDismiss = { 
                        shownDialog = DialogType.NONE
                        fileToDelete = null
                    }
                )
            }
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
        DialogType.DELETE_SELECTED -> {
            ConfirmationDialog(
                title = stringResource(R.string.delete_permanently_title),
                message = "Are you sure you want to permanently delete the ${selectedFiles.size} selected items?",
                onConfirm = {
                    viewModel.deleteSelectedTrashFilesPermanently()
                    shownDialog = DialogType.NONE
                },
                onDismiss = { shownDialog = DialogType.NONE }
            )
        }
        DialogType.NONE -> { /* Do nothing */ }
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                if (selectionMode) {
                    TrashSelectionTopAppBar(
                        selectedCount = selectedFiles.size,
                        isAllSelected = selectedFiles.size == trashedFilesState.size,
                        onClearSelection = { viewModel.exitTrashSelectionMode() },
                        onSelectAll = {
                            if (selectedFiles.size == trashedFilesState.size) {
                                viewModel.clearTrashSelection()
                            } else {
                                viewModel.selectAllTrashFiles()
                            }
                        },
                        onDeletePermanently = { shownDialog = DialogType.DELETE_SELECTED },
                        onRestore = { viewModel.restoreSelectedTrashFiles() }
                    )
                } else {
                    TrashTopAppBar(
                        onBack = onBack
                    )
                }
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
                if (loadingState && !operationStatus.isRunning) {
                     CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (trashedFilesState.isEmpty() && !loadingState && !operationStatus.isRunning) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.trash_empty_message),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn {
                        items(trashedFilesState, key = { it.id }) { file ->
                            TrashedItem(
                                file = file,
                                isSelected = selectedFiles.contains(file.id),
                                selectionMode = selectionMode,
                                onClick = {
                                    if (selectionMode) {
                                        viewModel.toggleTrashSelection(file.id)
                                    } else {
                                        fileToRestore = file
                                        shownDialog = DialogType.RESTORE_SINGLE
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleTrashSelection(file.id)
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
fun TrashSelectionTopAppBar(
    selectedCount: Int,
    isAllSelected: Boolean,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeletePermanently: () -> Unit,
    onRestore: () -> Unit
) {
    TopAppBar(
        title = { Text("$selectedCount Selected") },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Default.Close, contentDescription = "Clear Selection")
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(
                    imageVector = Icons.Outlined.SelectAll,
                    contentDescription = "Select All",
                    tint = if (isAllSelected) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
            }
            IconButton(onClick = onRestore) {
                Icon(Icons.Default.Restore, contentDescription = "Restore Selected")
            }
            IconButton(onClick = onDeletePermanently) {
                Icon(Icons.Default.DeleteForever, contentDescription = "Delete Selected Permanently")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrashedItem(
    file: TrashedFile,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // Convert TrashedFile to FileModel for the shared component
    val fileModel = remember(file) {
        FileModel(
            id = file.id,
            name = file.name,
            path = file.trashPath, // Use the physical path in trash
            size = file.size,
            dateModified = file.dateDeleted,
            mimeType = null,
            type = file.type,
            isDirectory = false
        )
    }

    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.98f else 1f,
        label = "ItemScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .background(backgroundColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant), // Use variant for better contrast
            contentAlignment = Alignment.Center
        ) {
            FileThumbnail(
                file = fileModel,
                modifier = Modifier.fillMaxSize(),
                iconSize = 0.8f // Slightly smaller icons inside the box
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.basicMarquee()
            )
            Text(
                stringResource(R.string.original_path_label, file.originalPath.substringBeforeLast("/")),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.deleted_date_label, FileUtils.formatDate(file.dateDeleted)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        if (selectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
