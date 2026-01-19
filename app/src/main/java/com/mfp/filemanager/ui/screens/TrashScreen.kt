package com.mfp.filemanager.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.mfp.filemanager.R
import com.mfp.filemanager.ui.viewmodels.HomeViewModel
import com.mfp.filemanager.data.FileType
import com.mfp.filemanager.data.FileUtils
import com.mfp.filemanager.data.trash.TrashedFile

private enum class DialogType {
    NONE,
    DELETE_SINGLE,
    RESTORE_SINGLE,
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
    val swipeDeleteEnabled by viewModel.swipeDeleteEnabled.collectAsState()

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
        DialogType.NONE -> { /* Do nothing */ }
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
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
                             // Swipe to Delete (Permanently) - EndToStart
                             val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        fileToDelete = file
                                        shownDialog = DialogType.DELETE_SINGLE
                                        false 
                                    } else {
                                        false
                                    }
                                }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        Color.Transparent
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(color)
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteForever,
                                            contentDescription = "Delete Forever",
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                },
                                enableDismissFromStartToEnd = false,
                                enableDismissFromEndToStart = swipeDeleteEnabled, // Only delete forever
                                content = {
                                    TrashedItem(
                                        file = file,
                                        onClick = {
                                             fileToRestore = file
                                             shownDialog = DialogType.RESTORE_SINGLE
                                        }
                                    )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrashedItem(
    file: TrashedFile,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
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
                    FileType.ARCHIVE -> Icons.Default.Folder
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
    }
}
