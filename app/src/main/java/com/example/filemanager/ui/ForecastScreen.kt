package com.example.filemanager.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filemanager.data.FileModel
import com.example.filemanager.data.FileUtils
import com.example.filemanager.data.StorageInfo
import com.example.filemanager.ui.components.LiquidCleaningOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit
) {
    val forecastText by viewModel.forecastText.collectAsState()
    val dailyUsageBytes by viewModel.dailyUsageRate.collectAsState()
    val largeFiles by viewModel.largeFiles.collectAsState()
    val storageInfo by viewModel.storageInfo.collectAsState()
    val estimatedFullDate by viewModel.estimatedFullDate.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showAnimation by remember { mutableStateOf(false) }
    var fileToDeleteSize by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        viewModel.loadStorageInfo()
        viewModel.loadForecastDetails()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage Health") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val currentStorageInfo = storageInfo
            
            if (currentStorageInfo != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Prediction Card (moved up since Health Card is gone)
                    item {
                        PredictionCard(forecastText, estimatedFullDate, dailyUsageBytes, currentStorageInfo)
                    }

                    // 2. Category Breakdown
                    item {
                        StorageBreakdownCard(currentStorageInfo)
                    }

                    // 3. Cleanup Recommendations Hub
                    item {
                        Text(
                            text = "Cleanup Recommendations",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // A. Unused Files (Empty Folders)
                    item {
                         val ghostFiles by viewModel.ghostFiles.collectAsState()
                         if (ghostFiles.isNotEmpty()) {
                             Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                             ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Unused Files", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("${ghostFiles.size} empty folders found", style = MaterialTheme.typography.bodySmall)
                                    }
                                    TextButton(onClick = { viewModel.deleteAllGhostFiles() }) {
                                        Text("Clean")
                                    }
                                }
                             }
                         }
                    }

                    // B. Large Files Header/Card
                    item {
                        // Just a sub-header for the list
                         Text(
                            text = "Large Files",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    // C. Large Files List
                    if (largeFiles.isEmpty() && !isLoading) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text("No large files found.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(largeFiles, key = { it.path }) { file ->
                            LargeFileItem(
                                file = file,
                                onDelete = {
                                    fileToDeleteSize = file.size
                                    showAnimation = true
                                    viewModel.deleteLargeFile(file)
                                }
                            )
                        }
                    }
                }
            } else {
                // Loading state
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            
            LiquidCleaningOverlay(
                isVisible = showAnimation,
                amountToClean = fileToDeleteSize,
                onFinished = { showAnimation = false }
            )
        }
    }
}

@Composable
fun PredictionCard(forecastText: String, estimatedDate: String, dailyUsage: Long, storageInfo: StorageInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Replaced Icon with Text Label, Graph will be below
                Text("Storage Projection", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            // Graphical Projection
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(horizontal = 8.dp)
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    
                    // Usage Percentage (0.0 to 1.0)
                    val currentUsageRatio = (storageInfo.usedBytes.toFloat() / storageInfo.totalBytes.toFloat()).coerceIn(0f, 1f)
                    
                    // Draw Threshold Line (Total Capacity)
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.5f),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(width, 0f),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                    
                    // Draw Projection Curve
                    // Start: (0, CurrentUsageY)
                    // End: (Width, 0) -> represents hitting 100% capacity "Future"
                    
                    val startY = height * (1 - currentUsageRatio)
                    val endY = 0f // Top of chart (100% full)
                    
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, startY + 10f) // Start slightly below for visual
                        quadraticBezierTo(
                            width * 0.5f, startY, // Control point
                            width, endY
                        )
                    }
                    
                    drawPath(
                        path = path,
                        color = Color(0xFF2196F3), // Blue projection line
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )
                    
                    // Draw "Now" point
                    drawCircle(
                        color = Color(0xFF6750a4),
                        radius = 6.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(0f, startY + 10f)
                    )
                }
                
                // Labels inside the graph area
                Text(
                    text = "Now: ${FileUtils.formatSize(storageInfo.usedBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
                 Text(
                    text = "Total: ${FileUtils.formatSize(storageInfo.totalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            if (forecastText == "Stable") {
                 Text("Storage usage is stable.", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            } else {
                 Text("Full by $estimatedDate", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                 Spacer(modifier = Modifier.height(4.dp))
                 Text("You're using about ${FileUtils.formatSize(dailyUsage)} per day.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
fun StorageBreakdownCard(info: StorageInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Storage Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            // Visual Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val total = info.usedBytes.toFloat().coerceAtLeast(1f)
                
                if (info.imageBytes > 0) Box(Modifier.weight(info.imageBytes / total).fillMaxSize().background(Color(0xFF6750a4))) // Purple
                if (info.videoBytes > 0) Box(Modifier.weight(info.videoBytes / total).fillMaxSize().background(Color(0xFF4285F4))) // Blue
                if (info.audioBytes > 0) Box(Modifier.weight(info.audioBytes / total).fillMaxSize().background(Color(0xFF009688))) // Teal
                if (info.documentBytes > 0) Box(Modifier.weight(info.documentBytes / total).fillMaxSize().background(Color(0xFFFFC107))) // Amber
                if (info.otherBytes > 0) Box(Modifier.weight(info.otherBytes / total).fillMaxSize().background(Color(0xFFe8b688))) // Peach
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Legend
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BreakdownItem("Images", info.imageBytes, Color(0xFF6750a4))
                BreakdownItem("Videos", info.videoBytes, Color(0xFF4285F4))
                BreakdownItem("Audio", info.audioBytes, Color(0xFF009688))
                BreakdownItem("Docs", info.documentBytes, Color(0xFFFFC107))
                BreakdownItem("Other", info.otherBytes, Color(0xFFe8b688))
            }
        }
    }
}

@Composable
fun BreakdownItem(label: String, size: Long, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(FileUtils.formatSize(size), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LargeFileItem(
    file: FileModel,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = FileUtils.formatSize(file.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
