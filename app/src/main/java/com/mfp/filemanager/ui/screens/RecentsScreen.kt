package com.mfp.filemanager.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mfp.filemanager.data.FileModel
import com.mfp.filemanager.ui.components.DetailedFileItem
import com.mfp.filemanager.ui.viewmodels.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onFileClick: (FileModel) -> Unit
) {
    val recentFiles by viewModel.recentFiles.collectAsState()
    val selectedFiles by viewModel.selectedRecentFiles.collectAsState()
    val selectionMode by viewModel.isRecentSelectionMode.collectAsState()
    val swipeDeleteEnabled by viewModel.swipeDeleteEnabled.collectAsState()

    // Handle back press to clear selection if in mode
    // (Note: To handle system back button, we'd need BackHandler, ignoring for now as per minimal change scope, but good UX would include it)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (selectionMode) {
                        Text("${selectedFiles.size} Selected")
                    } else {
                        Text("Recent Files")
                    }
                },
                navigationIcon = {
                    if (selectionMode) {
                        IconButton(onClick = { viewModel.exitRecentSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Selection")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = { 
                            if (selectedFiles.size == recentFiles.size) {
                                viewModel.clearRecentSelection()
                            } else {
                                viewModel.selectAllRecentFiles()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.SelectAll,
                                contentDescription = if (selectedFiles.size == recentFiles.size) "Unselect All" else "Select All",
                                tint = if (selectedFiles.size == recentFiles.size) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                        IconButton(onClick = { viewModel.deleteSelectedRecentFiles() }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete Selected")
                        }
                    }
                },
                colors = if (selectionMode) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                } else {
                    TopAppBarDefaults.topAppBarColors()
                }
            )
        }
    ) { innerPadding ->
        if (recentFiles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("No recent files found")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 80.dp) // Extra padding for FAB or bottom nav if any
            ) {
                itemsIndexed(
                    items = recentFiles,
                    key = { _, file -> file.path }
                ) { index, file ->
                    val isSelected = selectedFiles.contains(file.path)
                    
                    // Swipe to Delete (Only active when NOT in selection mode)
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                viewModel.deleteRecentFile(file)
                                true
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
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        },
                        enableDismissFromStartToEnd = false,
                        enableDismissFromEndToStart = !selectionMode && swipeDeleteEnabled,
                        content = {
                             DetailedFileItem(
                                file = file,
                                isSelected = isSelected,
                                selectionMode = selectionMode,
                                onClick = { 
                                    if (selectionMode) {
                                        viewModel.toggleRecentSelection(file)
                                    } else {
                                        onFileClick(file)
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleRecentSelection(file)
                                    // Feedback?
                                },
                                onMenuAction = {},
                                allowDelete = false, // Handled by swipe/selection
                                showMenuButton = false,
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface) // Ensure opacity
                            )
                        }
                    )

                    if (index < recentFiles.lastIndex && !selectionMode) {
                         HorizontalDivider(
                             modifier = Modifier.padding(start = 72.dp),
                             color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}
