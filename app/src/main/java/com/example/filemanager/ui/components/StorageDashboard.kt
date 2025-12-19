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
import androidx.compose.material.icons.filled.Warning
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
    cacheSize: Long, // Added parameter
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



            // Metrics Row (New Design)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically // Align button with text block vertical center
            ) {
                Column {
                    val percentage = if (storageInfo.totalBytes > 0) {
                        (storageInfo.usedBytes.toFloat() / storageInfo.totalBytes) * 100
                    } else 0f
                    
                    Text(
                        text = "${percentage.toInt()}%", // "80%" style
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 42.sp
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${FileUtils.formatSize(storageInfo.usedBytes)} used of ${FileUtils.formatSize(storageInfo.totalBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Button(
                    onClick = onFreeUpClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary, 
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Free up ${FileUtils.formatSize(cacheSize)}", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Progress Bar (Segmented Look)
            StorageProgressBar(storageInfo)

            Spacer(modifier = Modifier.height(24.dp))

            // Legend Grid (3 Columns)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Row 1
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    LegendItem(color = Color(0xFF4285F4), label = "Videos", modifier = Modifier.weight(1f))
                    LegendItem(color = Color(0xFF6750a4), label = "Images", modifier = Modifier.weight(1f))
                    LegendItem(color = Color(0xFF4CAF50), label = "Apps & Data", modifier = Modifier.weight(1f))
                }
                // Row 2
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    LegendItem(color = Color(0xFFFFC107), label = "Docs", modifier = Modifier.weight(1f))
                    LegendItem(color = Color(0xFF009688), label = "Audio", modifier = Modifier.weight(1f))
                    LegendItem(color = Color(0xFFe8b688), label = "Others", modifier = Modifier.weight(1f))
                }
                // Row 3
                 Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    LegendItem(color = MaterialTheme.colorScheme.surfaceVariant, label = "Free", modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.weight(2f)) // Empty space for missing items
                 }
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
fun LegendItem(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(10.dp) // Slightly larger
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp)) // More spacing
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), // Darker text
        )
    }
}

@Composable
fun StorageProgressBar(info: StorageInfo) {
    val totalStorage = info.totalBytes.toFloat().coerceAtLeast(1f)
    
    // Segments mapping
    val imageSeg = info.imageBytes.toFloat() / totalStorage
    val videoSeg = info.videoBytes.toFloat() / totalStorage
    val audioSeg = info.audioBytes.toFloat() / totalStorage
    val docSeg = info.documentBytes.toFloat() / totalStorage
    val appSeg = info.appBytes.toFloat() / totalStorage
    val otherSeg = info.otherBytes.toFloat() / totalStorage

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
        
        // Image
        val w1 = width * imageSeg
        if (w1 > 0) {
            drawRect(
                color = Color(0xFF6750a4), // Deep Purple
                topLeft = Offset(currentX, 0f),
                size = androidx.compose.ui.geometry.Size(w1, height)
            )
            currentX += w1
        }
        
        // Video
        val w2 = width * videoSeg
        if (w2 > 0) {
             drawRect(
                color = Color(0xFF4285F4), // Blue
                topLeft = Offset(currentX, 0f),
                size = androidx.compose.ui.geometry.Size(w2, height)
            )
            currentX += w2
        }

        // Audio
        val w3 = width * audioSeg
        if (w3 > 0) {
             drawRect(
                color = Color(0xFF009688), // Teal
                topLeft = Offset(currentX, 0f),
                size = androidx.compose.ui.geometry.Size(w3, height)
            )
            currentX += w3
        }

        // Docs
        val w4 = width * docSeg
        if (w4 > 0) {
             drawRect(
                color = Color(0xFFFFC107), // Amber
                topLeft = Offset(currentX, 0f),
                size = androidx.compose.ui.geometry.Size(w4, height)
            )
            currentX += w4
        }


        // Apps
        val w6 = width * appSeg
        if (w6 > 0) {
             drawRect(
                color = Color(0xFF4CAF50), // Green
                topLeft = Offset(currentX, 0f),
                size = androidx.compose.ui.geometry.Size(w6, height)
            )
            currentX += w6
        }

        // Others
        val w7 = width * otherSeg
        if (w7 > 0) {
             drawRect(
                color = Color(0xFFe8b688), // Peach
                topLeft = Offset(currentX, 0f),
                size = androidx.compose.ui.geometry.Size(w7, height)
            )
            currentX += w7
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
                        Icon(
                            painter = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(24.dp)
                        )
                    } else if (iconVector != null) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(24.dp)
                        )
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



