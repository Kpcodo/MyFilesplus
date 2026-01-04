package com.example.filemanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.filemanager.data.FileModel
import com.example.filemanager.data.FileUtils
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
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section: Unused Files (Ghost Files) REMOVED

                // Section: Large Files
                if (largeFiles.isNotEmpty()) {
                    item {
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
                            subtitle = FileUtils.formatSize(file.size),
                            isSelected = selectedPaths.contains(file.path),
                            onToggle = { toggleSelection(file.path) }
                        )
                    }
                }

                if (largeFiles.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No recommendations found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                
                // Extra spacer for FAB
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun RecommendationItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth().clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
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
            if (isSelected) {
                Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
