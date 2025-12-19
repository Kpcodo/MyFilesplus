package com.example.filemanager.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.VideoFile
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filemanager.data.FileUtils
import com.example.filemanager.data.StorageInfo


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onCleanupClick: () -> Unit
) {
    val forecastText by viewModel.forecastText.collectAsState()
    val dailyUsageBytes by viewModel.dailyUsageRate.collectAsState()
    val storageInfo by viewModel.storageInfo.collectAsState()
    val estimatedFullDate by viewModel.estimatedFullDate.collectAsState()
    
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
                    // 1. Prediction Card
                    item {
                        PredictionCard(forecastText, estimatedFullDate, dailyUsageBytes, currentStorageInfo)
                    }

                    // 2. File Type Distribution (Redesigned)
                    item {
                        FileTypeDistributionCard(currentStorageInfo)
                    }

                    // 3. Smart Suggestions Entry
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Smart Suggestions",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Review files to reclaim space",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = onCleanupClick) {
                                    Text("Clear")
                                }
                            }
                        }
                    }
                }
            } else {
                // Loading state
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
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
                    
                    val currentUsageRatio = (storageInfo.usedBytes.toFloat() / storageInfo.totalBytes.toFloat()).coerceIn(0f, 1f)
                    
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.5f),
                        start = Offset(0f, 0f),
                        end = Offset(width, 0f),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                    
                    val startY = height * (1 - currentUsageRatio)
                    val endY = 0f 
                    
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, startY + 10f) 
                        quadraticBezierTo(
                            width * 0.5f, startY, 
                            width, endY
                        )
                    }
                    
                    drawPath(
                        path = path,
                        color = Color(0xFF2196F3), 
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )
                    
                    drawCircle(
                        color = Color(0xFF6750a4),
                        radius = 6.dp.toPx(),
                        center = Offset(0f, startY + 10f)
                    )
                }
                
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
fun FileTypeDistributionCard(info: StorageInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "FILE TYPE DISTRIBUTION",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Top Segmented Bar
            // Colors matching StorageDashboard
            val appColor = Color(0xFF4CAF50) // Green
            val videoColor = Color(0xFF4285F4) // Blue
            val imageColor = Color(0xFF6750a4) // Purple
            val audioColor = Color(0xFF009688) // Teal
            val docColor = Color(0xFFFFC107) // Amber
            val otherColor = Color(0xFFe8b688) // Peach
            val freeColor = Color(0xFFC8E6C9) // Light Green/Mint for Free space

            val total = info.totalBytes.toFloat().coerceAtLeast(1f)
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (info.appBytes > 0) Box(Modifier.weight(info.appBytes / total).fillMaxSize().background(appColor))
                if (info.videoBytes > 0) Box(Modifier.weight(info.videoBytes / total).fillMaxSize().background(videoColor))
                if (info.imageBytes > 0) Box(Modifier.weight(info.imageBytes / total).fillMaxSize().background(imageColor))
                if (info.audioBytes > 0) Box(Modifier.weight(info.audioBytes / total).fillMaxSize().background(audioColor))
                if (info.documentBytes > 0) Box(Modifier.weight(info.documentBytes / total).fillMaxSize().background(docColor))
                if (info.otherBytes > 0) Box(Modifier.weight(info.otherBytes / total).fillMaxSize().background(otherColor))
                if (info.freeBytes > 0) Box(Modifier.weight(info.freeBytes / total).fillMaxSize().background(freeColor))
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // 3. Free Space Card (Moved to top)
            FreeSpaceCard(freeBytes = info.freeBytes, totalBytes = info.totalBytes, color = Color(0xFF00C853))

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Grid of Category Cards
            val categories = listOf(
                CategoryDetail(
                    name = "Apps & Data", 
                    size = info.appBytes, 
                    color = appColor, 
                    icon = Icons.Default.Android
                ),
                CategoryDetail(
                    name = "Videos", 
                    size = info.videoBytes, 
                    color = videoColor, 
                    icon = Icons.Default.VideoFile
                ),
                CategoryDetail(
                    name = "Images", 
                    size = info.imageBytes, 
                    color = imageColor, 
                    icon = Icons.Default.Image
                ),
                CategoryDetail(
                    name = "Documents", 
                    size = info.documentBytes, 
                    color = docColor, 
                    icon = Icons.Default.Description
                ),
                CategoryDetail(
                    name = "Audio", 
                    size = info.audioBytes, 
                    color = audioColor, 
                    icon = Icons.Default.AudioFile
                ),
                CategoryDetail(
                    name = "Others", 
                    size = info.otherBytes, 
                    color = otherColor, 
                    icon = Icons.Default.MoreHoriz
                )
            )

            // 2 Columns Grid
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                categories.chunked(2).forEach { rowItems ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowItems.forEach { item ->
                            CategoryDetailCard(
                                item = item,
                                totalBytes = info.totalBytes,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowItems.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

        }
    }
}

data class CategoryDetail(
    val name: String,
    val size: Long,
    val color: Color,
    val icon: ImageVector
)

@Composable
fun CategoryDetailCard(
    item: CategoryDetail,
    totalBytes: Long,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) // Slightly distinct background
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(item.color.copy(alpha = 0.9f), RoundedCornerShape(8.dp)), // Vibrant icon bg
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = FileUtils.formatSize(item.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Linear Progress for this specific category relative to Total (or relative to used? usually total)
            // Visualizing small ratios might be hard, so maybe relative to *Used* or just visual? 
            // The image shows small bars. Let's do relative to Total for context.
            val ratio = if (totalBytes > 0) item.size.toFloat() / totalBytes else 0f
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = item.color,
                trackColor = item.color.copy(alpha = 0.2f),
            )
        }
    }
}

@Composable
fun FreeSpaceCard(
    freeBytes: Long,
    totalBytes: Long,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(color, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = "Free Space",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            
            Text(
                text = "${FileUtils.formatSize(freeBytes)} / ${FileUtils.formatSize(totalBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
