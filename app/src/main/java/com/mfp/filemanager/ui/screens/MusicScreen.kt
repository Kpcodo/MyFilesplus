package com.mfp.filemanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.mfp.filemanager.data.FileModel
import com.mfp.filemanager.ui.components.FileListItem
import com.mfp.filemanager.ui.viewmodels.AudioViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    viewModel: AudioViewModel,
    onTrackClick: () -> Unit
) {
    val musicFiles by viewModel.musicFiles.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    // Load music files on first launch
    LaunchedEffect(Unit) {
        if (musicFiles.isEmpty()) {
            viewModel.loadMusicFiles()
        }
    }

    Scaffold(
        topBar = {
             CenterAlignedTopAppBar(title = { Text("Music Library") })
        },
        modifier = Modifier.graphicsLayer {
            // Isolate this screen to prevent re-draws during player transitions
            clip = true
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.loadMusicFiles()
                // Simulate refresh delay for smooth animation
                coroutineScope.launch {
                    delay(500)
                    isRefreshing = false
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (musicFiles.isEmpty() && !isRefreshing) {
                Box(
                    Modifier.fillMaxSize(), 
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text("No music files found", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Hardware layer for smooth scrolling during transitions
                            clip = true
                        }
                ) {
                    items(musicFiles, key = { it.path }) { file ->
                         com.mfp.filemanager.ui.components.MusicListItem(
                             file = file,
                             onClick = {
                                 viewModel.playFile(File(file.path))
                                 // Removed onTrackClick() to prevent automatic full-screen navigation
                             },
                             onLongClick = {},
                             onMenuAction = { /* Optional: Implement specific music actions */ }
                         )
                    }
                }
            }
        }
    }
}
