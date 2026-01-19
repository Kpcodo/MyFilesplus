package com.mfp.filemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mfp.filemanager.data.FileModel
import com.mfp.filemanager.ui.viewmodels.HomeViewModel
import com.mfp.filemanager.data.FileUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupRecommendationsScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit
) {
    val largeFiles by viewModel.largeFiles.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Selection state: Set of Strings (Paths)
    val selectedPaths = remember { mutableStateListOf<String>() }

    // Helper to toggle selection
    fun toggleSelection(path: String) {
        if (selectedPaths.contains(path)) {
            selectedPaths.remove(path)
        } else {
            selectedPaths.add(path)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cleanup Recommendations") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedPaths.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        // Delete logic
                        // Only files (Large Files)
                        val filesToDelete = largeFiles.filter { it.path in selectedPaths }
                        
                        // Execute Deletions
                        if (filesToDelete.isNotEmpty()) {
                            // Convert FileModel to path list for deletion
                            viewModel.deleteMultipleFiles(filesToDelete.map { it.path }, "") // path arg ignored for simple delete
                        }
                        
                        selectedPaths.clear()
                    },
                    icon = { Icon(Icons.Default.Delete, "Delete") },
                    text = { Text("Delete (${selectedPaths.size})") },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section: Unused Files (Ghost Files) REMOVED

                // Section: Large Files
                if (largeFiles.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Large Files",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(largeFiles) { file ->
                        RecommendationItem(
                            icon = Icons.Default.InsertDriveFile,
                            title = file.name,
                            subtitle = FileUtils.formatSize(file.size), // Showing file size below name
                            isSelected = selectedPaths.contains(file.path),
                            onToggle = { toggleSelection(file.path) }
                        )
                    }
                }

                if (largeFiles.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No recommendations found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                
                // Extra spacer for FAB
                item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun RecommendationItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String, // formatSize passed here
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(40.dp)
                )
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
                            .size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
