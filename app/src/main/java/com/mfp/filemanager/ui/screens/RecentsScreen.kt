package com.mfp.filemanager.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mfp.filemanager.data.FileModel
import com.mfp.filemanager.ui.components.DetailedFileItem
import com.mfp.filemanager.ui.viewmodels.HomeViewModel
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onFileClick: (FileModel) -> Unit
) {
    val recentFiles by viewModel.recentFiles.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recent Files") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
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
                contentPadding = PaddingValues(16.dp)
            ) {
                itemsIndexed(recentFiles) { index, file ->
                    DetailedFileItem(
                        file = file,
                        isSelected = false,
                        selectionMode = false,
                        onClick = { onFileClick(file) },
                        onLongClick = {},
                        onMenuAction = {}, // Actions disabled for simple recents view? Or enable them? 
                                          // Design shows simple list in Home, but full list might want actions.
                                          // Keeping it simple for now, as consistent with Home view.
                        allowDelete = false,
                        showMenuButton = false
                    )
                    if (index < recentFiles.lastIndex) {
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
