package com.mfp.filemanager.ui.screens

import androidx.compose.foundation.Canvas

import com.mfp.filemanager.ui.viewmodels.HomeViewModel
import com.mfp.filemanager.ui.animations.animateEnter
import com.mfp.filemanager.ui.animations.bounceClick
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip

import com.mfp.filemanager.ui.components.FileThumbnail
import com.mfp.filemanager.data.FileType
import com.mfp.filemanager.data.FileUtils
import com.mfp.filemanager.data.StorageInfo

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox

import com.mfp.filemanager.data.FileModel
import com.mfp.filemanager.ui.components.DetailedFileItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onInternalStorageClick: () -> Unit,
    onOtherStorageClick: () -> Unit,
    onSearchClick: () -> Unit,
    onForecastClick: () -> Unit,
    onRecentFileClick: (FileModel) -> Unit,
    onViewAllRecentsClick: () -> Unit
) {
    val storageInfo by viewModel.storageInfo.collectAsState()
    val trashSize by viewModel.trashSize.collectAsState()
    val forecastText by viewModel.forecastText.collectAsState()
    
    val recentFiles by viewModel.recentFiles.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadRecentFiles()
    }


    val isLoading by viewModel.isLoading.collectAsState()
    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = {
            viewModel.refreshHomeData()
        },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                // Search Bar Entry Point
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .bounceClick(onClick = { viewModel.clearSearch(); onSearchClick() }),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest, // Dynamic
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Search files",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            item {
                storageInfo?.let {
                    // Parallax Effect
                    // Since it's inside a LazyColumn, we can't easily get absolute scroll offset of the parent easily without nested scroll connection 
                    // OR we can make it sticky or use a custom layout.
                    // However, 'LazyColumn' doesn't support parallax out of the box for items.
                    // A simple workaround for "Cinematic" feel is an entrance animation or just smooth scrolling which we have.
                    // But to support "Parallax", we'd usually put this in a Box behind the list or use a CollapsingToolbar.
                    // Given the request, let's add a subtle scale/fade based on its own visibility or just a cool entrance.
                    // Let's use `animateEnter` here too for consistency first.
                    
                     Box(modifier = Modifier.animateEnter(delayMillis = 100)) {
                        com.mfp.filemanager.ui.components.StorageDashboard(
                            storageInfo = it,
                            trashSize = trashSize,
                            forecastText = forecastText,
                            onForecastClick = onForecastClick
                        )
                    }
                }
            }



            item {
                if (recentFiles.isNotEmpty()) {
                    RecentFilesSection(
                        files = recentFiles.take(6),
                        onFileClick = onRecentFileClick,
                        onViewAllClick = onViewAllRecentsClick
                    )
                }
            }

            item {
                AllStorageSection(
                    storageInfo = storageInfo,
                    onInternalStorageClick = onInternalStorageClick,
                    onOtherStorageClick = onOtherStorageClick
                )
            }
        }
    }
}

@Composable
fun StorageSummaryCard(info: StorageInfo) {
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StorageDonutChart(info = info)
            Spacer(modifier = Modifier.height(16.dp))
            StorageLegend()
        }
    }
}

@Composable
private fun StorageDonutChart(info: StorageInfo) {
    val total = info.totalBytes.toFloat()
    val imageSweep = (info.imageBytes.toFloat() / total) * 360f
    val videoSweep = (info.videoBytes.toFloat() / total) * 360f
    val audioSweep = (info.audioBytes.toFloat() / total) * 360f
    val docsSweep = (info.documentBytes.toFloat() / total) * 360f
    val appSweep = (info.appBytes.toFloat() / total) * 360f
    val otherSweep = (info.otherBytes.toFloat() / total) * 360f

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
        val backgroundCircleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 50f
            drawCircle(color = backgroundCircleColor, style = Stroke(width = strokeWidth))

            var startAngle = -90f

            // Images (Blue)
            drawArc(color = Color(0xFF1E88E5), startAngle = startAngle, sweepAngle = -imageSweep, useCenter = false, style = Stroke(strokeWidth, cap = StrokeCap.Butt))
            startAngle -= imageSweep

            // Videos (Red)
            drawArc(color = Color(0xFFE53935), startAngle = startAngle, sweepAngle = -videoSweep, useCenter = false, style = Stroke(strokeWidth, cap = StrokeCap.Butt))
            startAngle -= videoSweep

            // Audio (Purple)
            drawArc(color = Color(0xFF8E24AA), startAngle = startAngle, sweepAngle = -audioSweep, useCenter = false, style = Stroke(strokeWidth, cap = StrokeCap.Butt))
            startAngle -= audioSweep

            // Docs (Yellow)
            drawArc(color = Color(0xFFFBC02D), startAngle = startAngle, sweepAngle = -docsSweep, useCenter = false, style = Stroke(strokeWidth, cap = StrokeCap.Butt))
            startAngle -= docsSweep

            // Apps (Green)
            drawArc(color = Color(0xFF4CAF50), startAngle = startAngle, sweepAngle = -appSweep, useCenter = false, style = Stroke(strokeWidth, cap = StrokeCap.Butt))
            startAngle -= appSweep

            // Others (Peach)
            drawArc(color = Color(0xFFe8b688), startAngle = startAngle, sweepAngle = -otherSweep, useCenter = false, style = Stroke(strokeWidth, cap = StrokeCap.Butt))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Used Storage:", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = FileUtils.formatSize(info.usedBytes), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(text = "/ ${'$'}{FileUtils.formatSize(info.totalBytes)}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StorageLegend() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            LegendItem(color = Color(0xFF1E88E5), text = "Images")
            Spacer(modifier = Modifier.width(16.dp))
            LegendItem(color = Color(0xFFE53935), text = "Videos")
            Spacer(modifier = Modifier.width(16.dp))
            LegendItem(color = Color(0xFF8E24AA), text = "Audio")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            LegendItem(color = Color(0xFFFBC02D), text = "Docs")
            Spacer(modifier = Modifier.width(16.dp))
            LegendItem(color = Color(0xFF4CAF50), text = "Apps")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            LegendItem(color = Color(0xFFe8b688), text = "Others")
        }
    }
}

@Composable
fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


@Composable
fun AllStorageSection(
    storageInfo: StorageInfo?,
    onInternalStorageClick: () -> Unit,
    onOtherStorageClick: () -> Unit
) {
    Column {
        Text("All Storage", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StorageDeviceCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.PhoneAndroid,
                name = "Internal Storage",
                info = "",
                onClick = onInternalStorageClick
            )
            StorageDeviceCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.SdStorage,
                name = "Other Storage",
                info = "",
                onClick = onOtherStorageClick
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageDeviceCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
    info: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val containerColor = if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)

    Card(
        modifier = modifier.bounceClick(onClick = if (enabled) onClick else null),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = name, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(name, fontWeight = FontWeight.Bold)
                if (info.isNotBlank()) {
                    Text(info, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun RecentFilesSection(
    files: List<FileModel>,
    onFileClick: (FileModel) -> Unit,
    onViewAllClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RECENT FILES",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
            androidx.compose.material3.TextButton(onClick = onViewAllClick) {
                Text("View All")
            }
        }
        
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column {
                files.forEachIndexed { index, file ->
                    CompactFileItem(
                        file = file,
                        onClick = { onFileClick(file) }
                    )
                    if (index < files.lastIndex) {
                        HorizontalDivider(
                             modifier = Modifier.padding(start = 68.dp),
                             color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompactFileItem(
    file: FileModel,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            FileThumbnail(
                file = file,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
             Text(
                text = FileUtils.formatSize(file.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
