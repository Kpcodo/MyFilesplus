package com.example.filemanager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filemanager.data.StorageInfo
import com.example.filemanager.data.FileUtils
import com.example.filemanager.R

@Composable
fun StorageDashboard(
    storageInfo: StorageInfo,
    trashSize: Long,
    emptyFoldersCount: Int,
    forecastText: String,
    onFreeUpClick: () -> Unit,
    onGhostFilesClick: () -> Unit,
    onForecastClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh, // Dynamic container color
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header
            Text(
                text = "STORAGE USAGE",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    val percentage = if (storageInfo.totalBytes > 0) {
                        (storageInfo.usedBytes.toFloat() / storageInfo.totalBytes) * 100
                    } else 0f
                    
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${percentage.toInt()}",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 48.sp
                        )
                        Text(
                            text = "%",
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 8.dp, start = 2.dp)
                        )
                    }
                    Text(
                        text = "${FileUtils.formatSize(storageInfo.usedBytes)} used of ${FileUtils.formatSize(storageInfo.totalBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }

                Button(
                    onClick = onFreeUpClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary, // Dynamic primary
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Free up ${FileUtils.formatSize(trashSize)}", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Progress Bar
            StorageProgressBar(storageInfo)

            Spacer(modifier = Modifier.height(16.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(color = Color(0xFF6750a4), label = "Images & Video")
                LegendItem(color = Color(0xFF9a82db), label = "Audio & Docs")
                LegendItem(color = Color(0xFFe8b688), label = "Others")
                LegendItem(color = MaterialTheme.colorScheme.surfaceVariant, label = "Free")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Smart Tools Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmartToolCard(
                    title = "Ghost Files",
                    subtitle = "$emptyFoldersCount Empty Folders",
                    iconVector = Icons.Default.Description, // Fallback icon
                    iconColor = MaterialTheme.colorScheme.primary,
                    hasNotification = emptyFoldersCount > 0,
                    modifier = Modifier.weight(1f),
                    onClick = onGhostFilesClick
                )
                SmartToolCard(
                    title = "Forecast",
                    subtitle = forecastText,
                    iconVector = Icons.Default.TrendingUp,
                    iconColor = Color(0xFF2196F3), // Blue color as requested
                    modifier = Modifier.weight(1f),
                    onClick = onForecastClick
                )
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 10.sp
        )
    }
}

@Composable
fun StorageProgressBar(info: StorageInfo) {
    val totalStorage = info.totalBytes.toFloat().coerceAtLeast(1f)
    
    // Segments mapping
    // Segment 1 (Deep Purple): Images + Video
    val segment1 = (info.imageBytes + info.videoBytes).toFloat() / totalStorage
    // Segment 2 (Light Purple): Audio + Docs
    val segment2 = (info.audioBytes + info.documentBytes).toFloat() / totalStorage
    // Segment 3 (Peach): Others
    val segment3 = info.otherBytes.toFloat() / totalStorage

    // Use clip to ensure the whole bar has rounded corners, allowing segments to be simple Rects without gaps.
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp)) 
            .background(MaterialTheme.colorScheme.surfaceVariant) // Track background
    ) {
        val width = size.width
        val height = size.height

        var currentX = 0f
        
        // Segment 1
        val w1 = width * segment1
        if (w1 > 0) {
            drawRect(
                color = Color(0xFF6750a4), // Deep Purple
                topLeft = Offset(currentX, 0f),
                size = androidx.compose.ui.geometry.Size(w1, height)
            )
            currentX += w1
        }
        
        // Segment 2
        val w2 = width * segment2
        if (w2 > 0) {
             drawRect(
                color = Color(0xFF9a82db), // Light Purple
                topLeft = Offset(currentX, 0f),
                size = androidx.compose.ui.geometry.Size(w2, height)
            )
            currentX += w2
        }

        // Segment 3
        val w3 = width * segment3
        if (w3 > 0) {
             drawRect(
                color = Color(0xFFe8b688), // Peach
                topLeft = Offset(currentX, 0f),
                size = androidx.compose.ui.geometry.Size(w3, height)
            )
            currentX += w3
        }
    }
}


@Composable
fun SmartToolCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.painter.Painter? = null,
    iconVector: ImageVector? = null,
    iconColor: Color,
    hasNotification: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.shadow(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(iconColor.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (icon != null) {
                         Icon(painter = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
                    } else if (iconVector != null) {
                         Icon(imageVector = iconVector, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            if (hasNotification) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp) // Closer to the corner
                        .size(8.dp)
                        .background(Color(0xFFFF5252), CircleShape)
                )
            }
        }
    }
}
