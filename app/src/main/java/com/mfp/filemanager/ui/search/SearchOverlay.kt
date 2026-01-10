package com.mfp.filemanager.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.mfp.filemanager.data.clipboard.ClipboardOperation
import com.mfp.filemanager.data.FileModel
import com.mfp.filemanager.ui.viewmodels.HomeViewModel
import com.mfp.filemanager.ui.components.DetailedFileItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchOverlay(
    viewModel: HomeViewModel,
    isVisible: Boolean,
    onClose: () -> Unit,
    onFileClick: (FileModel) -> Unit
) {
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val filter by viewModel.searchFilter.collectAsState()
    
    var showFilterDialog by remember { mutableStateOf(false) }
    
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isVisible) {
        if (isVisible) {
            focusRequester.requestFocus()
        } else {
            keyboardController?.hide()
            viewModel.clearSearch()
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandHorizontally(expandFrom = Alignment.CenterHorizontally),
        exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.CenterHorizontally)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClose() } // Click outside to close
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Docked Search Bar
                Box(
                    modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { 
                            // Consume clicks on the bar/area
                        } 
                ) {
                    SearchBar(
                        query = query,
                        onQueryChange = { viewModel.updateSearchQuery(it) },
                        onSearch = { 
                            viewModel.performSearch(it)
                            keyboardController?.hide()
                        },
                        active = false, // Always docked
                        onActiveChange = { },
                        placeholder = { Text("Search files...") },
                        leadingIcon = {
                            IconButton(onClick = onClose) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                                IconButton(onClick = { showFilterDialog = true }) {
                                    Icon(
                                        Icons.Default.FilterList, 
                                        contentDescription = "Filter",
                                        tint = if (filter.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (query.isEmpty()) {
                                    Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.padding(end = 8.dp))
                                }
                            }
                        },
                        modifier = Modifier.focusRequester(focusRequester),
                        content = {} // Empty content block for docked mode
                    )
                }

                // Results list (Floating below bar)
                if (query.isNotEmpty() || results.isNotEmpty() || isLoading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .weight(1f, fill = false) // Allow it to shrink if specific results are few? No, logic needs to be safe.
                            // Better: use weight(1f) but inside a scope that allows height control?
                            // Actually, just let it take available space up to some padding.
                            .padding(bottom = 16.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {}, // Consume clicks
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        tonalElevation = 4.dp
                    ) {
                        if (isLoading) {
                            Box(modifier = Modifier.height(100.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                            ) {
                                items(results, key = { it.id }) { file ->
                                    DetailedFileItem(
                                        file = file,
                                        isSelected = false,
                                        selectionMode = false,
                                        onClick = { 
                                            onFileClick(file)
                                            onClose()
                                        },
                                        onLongClick = { },
                                        onMenuAction = { action -> 
                                            when (action) {
                                                "move" -> viewModel.addSingleToClipboard(file, ClipboardOperation.MOVE)
                                                "copy" -> viewModel.addSingleToClipboard(file, ClipboardOperation.COPY)
                                                "delete" -> viewModel.deleteFile(file.path) { viewModel.performSearch(query) }
                                                "extract" -> viewModel.extractFile(file) { viewModel.performSearch(query) }
                                            }
                                        }
                                    )}
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilterDialog) {
        SearchFilterDialog(
            initialFilter = filter,
            onDismiss = { showFilterDialog = false },
            onApply = { 
                viewModel.updateSearchFilter(it)
                showFilterDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchFilterDialog(
    initialFilter: HomeViewModel.SearchFilter,
    onDismiss: () -> Unit,
    onApply: (HomeViewModel.SearchFilter) -> Unit
) {
    var selectedType by remember { mutableStateOf(initialFilter.type) }
    var minSizeMb by remember { mutableStateOf(if (initialFilter.minSize != null) (initialFilter.minSize!! / (1024 * 1024)).toString() else "") }
    var maxDaysAgo by remember { mutableStateOf(initialFilter.maxDaysAgo?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search Filters") },
        text = {
            Column {
                Text("File Type", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
                ) {
                    val types = com.mfp.filemanager.data.FileType.values().filter { it != com.mfp.filemanager.data.FileType.UNKNOWN }
                    types.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = if (selectedType == type) null else type },
                            label = { Text(type.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = minSizeMb,
                    onValueChange = { if (it.all { char -> char.isDigit() }) minSizeMb = it },
                    label = { Text("Min Size (MB)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = maxDaysAgo,
                    onValueChange = { if (it.all { char -> char.isDigit() }) maxDaysAgo = it },
                    label = { Text("Max Days Ago") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(HomeViewModel.SearchFilter(
                    type = selectedType,
                    minSize = minSizeMb.toLongOrNull()?.let { it * 1024 * 1024 },
                    maxDaysAgo = maxDaysAgo.toIntOrNull()
                ))
            }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = {
                onApply(HomeViewModel.SearchFilter()) // Clear filters
            }) { Text("Clear All") }
        }
    )
}
