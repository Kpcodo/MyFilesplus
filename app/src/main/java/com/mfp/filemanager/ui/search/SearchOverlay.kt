package com.mfp.filemanager.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import com.mfp.filemanager.data.FileModel
import com.mfp.filemanager.ui.viewmodels.HomeViewModel

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
        enter = fadeIn() + slideInVertically(initialOffsetY = { -40 }),
        exit = fadeOut(animationSpec = tween(durationMillis = 150))
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
                        onQueryChange = {
                            viewModel.updateSearchQuery(it)
                            if (it.isNotEmpty()) { // Trigger search from the first character as requested
                                viewModel.performSearch(it)
                            } else if (it.isEmpty()) {
                                viewModel.clearSearch()
                            }
                        },
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
                    
                    if (isLoading) {
                        Box(modifier = Modifier.height(100.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {} // Consume clicks to prevent closing
                        ) {
                            itemsIndexed(results, key = { _, file -> file.id }) { index, file ->
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { 
                                                keyboardController?.hide()
                                                onFileClick(file)
                                                onClose()
                                            }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Thumbnail Card
                                        Surface(
                                            modifier = Modifier.size(52.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            shadowElevation = 2.dp
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                               com.mfp.filemanager.ui.components.FileThumbnail(
                                                    file = file,
                                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                                ) 
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.width(16.dp))
                                        
                                        // Filename only
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                    
                                    if (index < results.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 68.dp), // Align with text
                                            color = Color.White.copy(alpha = 0.15f),
                                            thickness = 0.5.dp
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
