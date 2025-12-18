package com.example.filemanager.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoFile
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filemanager.R
import com.example.filemanager.data.FileType
import com.example.filemanager.data.FileUtils
import com.example.filemanager.data.StorageInfo

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.example.filemanager.ui.components.LiquidCleaningOverlay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onCategoryClick: (FileType) -> Unit,
    onInternalStorageClick: () -> Unit,
    onOtherStorageClick: () -> Unit,
    onViewAllClick: () -> Unit,
    onSearchClick: () -> Unit,
    onGhostFilesClick: () -> Unit,
    onForecastClick: () -> Unit
) {
    val storageInfo by viewModel.storageInfo.collectAsState()
    val trashSize by viewModel.trashSize.collectAsState()
    val cacheSize by viewModel.cacheSize.collectAsState()
    val emptyFoldersCount by viewModel.emptyFoldersCount.collectAsState()
    val forecastText by viewModel.forecastText.collectAsState()

    var showRocketAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadStorageInfo()
        viewModel.loadDashboardData()
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
                        .clickable { viewModel.clearSearch(); onSearchClick() },
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
                    com.example.filemanager.ui.components.StorageDashboard(
                        storageInfo = it,
                        trashSize = trashSize,
                        cacheSize = cacheSize,
                        emptyFoldersCount = emptyFoldersCount,
                        forecastText = forecastText,
                        onFreeUpClick = { 
                            if (cacheSize > 0) {
                                showRocketAnimation = true
                                viewModel.cleanTemporaryFiles()
                            }
                        },
                        onGhostFilesClick = onGhostFilesClick,
                        onForecastClick = onForecastClick
                    )
                }
            }


            item {
                CategoriesSection(
                    onCategoryClick = onCategoryClick,
                    onViewAllClick = onViewAllClick
                )
            }

            item {
                AllStorageSection(
                    storageInfo = storageInfo,
                    onInternalStorageClick = onInternalStorageClick,
                    onOtherStorageClick = onOtherStorageClick
                )
            }
        }

        LiquidCleaningOverlay(
            isVisible = showRocketAnimation,
            amountToClean = trashSize,
            onFinished = { showRocketAnimation = false }
        )
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

            // Others (Gray)
            drawArc(color = Color(0xFF757575), startAngle = startAngle, sweepAngle = -otherSweep, useCenter = false, style = Stroke(strokeWidth, cap = StrokeCap.Butt))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Used Storage:", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = FileUtils.formatSize(info.usedBytes), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(text = "/ ${FileUtils.formatSize(info.totalBytes)}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            LegendItem(color = Color(0xFF757575), text = "Others")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesSection(
    onCategoryClick: (FileType) -> Unit,
    onViewAllClick: () -> Unit
) {
    val categories = listOf(
        CategoryItemData("Images", Icons.Filled.Image, FileType.IMAGE),
        CategoryItemData("Videos", Icons.Filled.VideoFile, FileType.VIDEO),
        CategoryItemData("Audio", Icons.Filled.AudioFile, FileType.AUDIO),
        CategoryItemData("Documents", Icons.Filled.Description, FileType.DOCUMENT),
        CategoryItemData("Downloads", Icons.Filled.Download, FileType.DOWNLOAD),
        CategoryItemData("Archives", Icons.Filled.FolderZip, FileType.ARCHIVE)
    )

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Categories", style = MaterialTheme.typography.titleMedium)
            androidx.compose.material3.TextButton(onClick = onViewAllClick) {
                Text("View All")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        val rows = categories.chunked(3)
        rows.forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { item ->
                    CategoryCard(item = item, onClick = { onCategoryClick(item.type) }, modifier = Modifier.weight(1f))
                }
                repeat(3 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

data class CategoryItemData(val name: String, val icon: ImageVector, val type: FileType)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryCard(item: CategoryItemData, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.name,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(item.name, fontSize = 12.sp, maxLines = 1)
        }
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
            val internalInfo = if (storageInfo != null) {
                "${FileUtils.formatSize(storageInfo.usedBytes)} / ${FileUtils.formatSize(storageInfo.totalBytes)}"
            } else {
                "..."
            }
            StorageDeviceCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.PhoneAndroid,
                name = "Internal Storage",
                info = internalInfo,
                onClick = onInternalStorageClick
            )
            StorageDeviceCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.SdStorage,
                name = "Other Storage",
                info = "SD card, USB",
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
    Card(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
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
                Text(info, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
