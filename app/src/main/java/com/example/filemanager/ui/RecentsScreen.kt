package com.example.filemanager.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.remember
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.filemanager.data.FileModel
import com.example.filemanager.data.FileUtils
import java.io.File

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(
    viewModel: HomeViewModel,
    showTopBar: Boolean = true,
    swipeDeleteEnabled: Boolean = false,
    swipeDeleteDirection: Int = 0, // 0 = Left (EndToStart), 1 = Right (StartToEnd)
    onFileClick: (FileModel) -> Unit
) {
    val recentFiles by viewModel.recentFiles.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.loadRecentFiles()
    }

    val groupedFiles = remember(recentFiles) {
        recentFiles.groupBy { FileUtils.getTimeAgo(it.dateModified) }
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text("Recents") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { viewModel.loadRecentFiles() },
            modifier = Modifier.padding(paddingValues)
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                if (isLoading && recentFiles.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (recentFiles.isEmpty()) {
                    Text(
                        text = "No Recents",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        groupedFiles.forEach { (header, files) ->
                            stickyHeader {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = header,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            
                            items(files, key = { it.id }) { file ->
                                if (swipeDeleteEnabled) {
                                    val currentPath = file.path
                                    val dismissState = rememberSwipeToDismissBoxState(
                                        confirmValueChange = { value ->
                                            if (value != SwipeToDismissBoxValue.Settled) {
                                                // Trigger Delete
                                                viewModel.deleteFile(currentPath) {
                                                     // Refresh done automatically by flow, but we can force it
                                                     viewModel.loadRecentFiles()
                                                     
                                                     // Show Undo Snackbar
                                                     scope.launch {
                                                         val result = snackbarHostState.showSnackbar(
                                                             message = "Item moved to Bin",
                                                             actionLabel = "Undo",
                                                             duration = SnackbarDuration.Short
                                                         )
                                                         if (result == SnackbarResult.ActionPerformed) {
                                                             viewModel.undoDelete(currentPath)
                                                         }
                                                     }
                                                }
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                    )
                                    
                                    // Map Direction
                                    // 0 = Left Swipe (EndToStart), 1 = Right Swipe (StartToEnd)
                                    // We want to ENABLE only the configured direction.
                                    val directions = if (swipeDeleteDirection == 0) {
                                        setOf(SwipeToDismissBoxValue.EndToStart) 
                                    } else {
                                        setOf(SwipeToDismissBoxValue.StartToEnd)
                                    }

                                    SwipeToDismissBox(
                                        state = dismissState,
                                        backgroundContent = {
                                            val direction = dismissState.dismissDirection
                                            val color = if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) Color.Transparent else Color.Red
                                            val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                                            
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(color)
                                                    .padding(horizontal = 24.dp),
                                                contentAlignment = alignment
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = Color.White
                                                )
                                            }
                                        },
                                        enableDismissFromStartToEnd = swipeDeleteDirection == 1,
                                        enableDismissFromEndToStart = swipeDeleteDirection == 0,
                                        content = {
                                            Box(modifier = Modifier
                                                .background(MaterialTheme.colorScheme.surface)
                                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                            ) {
                                                DetailedFileItem(
                                                    file = file,
                                                    isSelected = false,
                                                    selectionMode = false,
                                                    onClick = { onFileClick(file) },
                                                    onLongClick = { /* Optional */ },
                                                    onMenuAction = { /* Optional */ }
                                                )
                                            }
                                        }
                                    )
                                } else {
                                    // Normal Item
                                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                        DetailedFileItem(
                                            file = file,
                                            isSelected = false,
                                            selectionMode = false,
                                            onClick = { onFileClick(file) },
                                            onLongClick = { /* Optional */ },
                                            onMenuAction = { /* Optional */ }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
