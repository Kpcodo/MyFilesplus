package com.example.filemanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filemanager.data.StorageInfo
import com.example.filemanager.data.FileUtils

@Composable
fun StorageDashboard(
    storageInfo: StorageInfo,
    trashSize: Long,
    forecastText: String,
    onForecastClick: () -> Unit
) {
    // Colors from design
    val colorVideo = Color(0xFF4285F4) // Blue
    val colorImage = Color(0xFF9C27B0) // Purple
    val colorApps = Color(0xFF4CAF50) // Green
    val colorDocs = Color(0xFFFFC107) // Yellow
    val colorAudio = Color(0xFF26A69A) // Teal
    val colorOthers = Color(0xFFFFAB91) // Peach/Orange
    val colorFree = Color(0xFFF5F5F5) // Light Grey for empty space

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            // --- TOP ROW: Percentage, Details, Forecast Chip ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false) 
                ) {
                    // Large Percentage
                    val percentage = if (storageInfo.totalBytes > 0) {
                        (storageInfo.usedBytes.toFloat() / storageInfo.totalBytes) * 100
                    } else 0f
                    
                    Text(
                        text = "${percentage.toInt()}%",
                        fontSize = 36.sp, // Reduced from 42sp to save space
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-1).sp
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp)) // Reduced from 12dp
                    
                    // Usage Details
                    Column {
                        Text(
                            text = "STORAGE USAGE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp, // Explicitly smaller
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${FileUtils.formatSize(storageInfo.usedBytes)} / ${FileUtils.formatSize(storageInfo.totalBytes)}",
                            style = MaterialTheme.typography.bodySmall, // Smaller body text
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Forecast Chip
                Surface(
                    color = Color(0xFFE3F2FD), // Light Blue
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { onForecastClick() }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp) // Tighter padding
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = Color(0xFF1976D2), 
                            modifier = Modifier.size(14.dp) // Smaller icon
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = forecastText, 
                            fontSize = 11.sp, // Compact text size
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1976D2),
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- PROGRESS BAR ---
            // We use a custom Row for the segmented bar look
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colorFree) // Background is "Free" space
            ) {
                // Calculate weights
                val total = storageInfo.totalBytes.toFloat().coerceAtLeast(1f)
                val wVideo = storageInfo.videoBytes / total
                val wImage = storageInfo.imageBytes / total
                val wApps = storageInfo.appBytes / total
                val wDocs = storageInfo.documentBytes / total
                val wAudio = storageInfo.audioBytes / total
                val wOthers = storageInfo.otherBytes / total

                if (wVideo > 0) Box(modifier = Modifier.weight(wVideo).fillMaxHeight().background(colorVideo))
                if (wImage > 0) Box(modifier = Modifier.weight(wImage).fillMaxHeight().background(colorImage))
                if (wApps > 0) Box(modifier = Modifier.weight(wApps).fillMaxHeight().background(colorApps))
                if (wDocs > 0) Box(modifier = Modifier.weight(wDocs).fillMaxHeight().background(colorDocs))
                if (wAudio > 0) Box(modifier = Modifier.weight(wAudio).fillMaxHeight().background(colorAudio))
                if (wOthers > 0) Box(modifier = Modifier.weight(wOthers).fillMaxHeight().background(colorOthers))
                
                // Remaining space is automatically "Free" because of the container background
                // But we need to ensure the weights allow for empty space if < 100%
                // The way 'weight' works in Row, if we don't have a filler, it expands to fill.
                // So we need an explicit spacer for free space if we want correct proportions.
                val wUsed = wVideo + wImage + wApps + wDocs + wAudio + wOthers
                val wFree = 1f - wUsed
                
                if (wFree > 0.01f) {
                     Box(modifier = Modifier.weight(wFree).fillMaxHeight().background(Color.Transparent))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- LEGEND ---
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Row 1
                Row(modifier = Modifier.fillMaxWidth()) {
                    LegendItem(color = colorVideo, label = "Videos", modifier = Modifier.weight(1f))
                    LegendItem(color = colorImage, label = "Images", modifier = Modifier.weight(1f))
                    LegendItem(color = colorApps, label = "Apps", modifier = Modifier.weight(1f))
                    LegendItem(color = colorDocs, label = "Docs", modifier = Modifier.weight(1f))
                }
                // Row 2
                Row(modifier = Modifier.fillMaxWidth()) {
                    LegendItem(color = colorAudio, label = "Audio", modifier = Modifier.weight(1f))
                    LegendItem(color = colorOthers, label = "Others", modifier = Modifier.weight(1f))
                    // Merging the last two slots (weight 2f) to give "Free Space" text room to expand
                    LegendItem(color = Color(0xFFE0E0E0), label = "Free Space", modifier = Modifier.weight(2f)) 
                }
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically, 
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(12.dp) // Increased from 10.dp
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp, // Explicitly smaller to fit "Free Space"
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}



