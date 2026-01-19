package com.mfp.filemanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.mfp.filemanager.data.StorageInfo
import com.mfp.filemanager.data.FileUtils

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

    // State for highlighting
    var highlightedCategory by remember { mutableStateOf<String?>(null) }

    // Animations for the storage segments
    val animSpec = androidx.compose.animation.core.tween<Float>(1000, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    
    val total = storageInfo.totalBytes.toFloat().coerceAtLeast(1f)
    val targetPercentage = if (storageInfo.totalBytes > 0) {
        (storageInfo.usedBytes.toFloat() / storageInfo.totalBytes) * 100
    } else 0f
    
    // Helper to ensure tiny but non-zero data is visible (min 1.2% width)
    fun getTargetWeight(bytes: Long): Float {
        if (bytes <= 0) return 0f
        return (bytes / total).coerceAtLeast(0.012f)
    }
    
    val animatedPercentage by androidx.compose.animation.core.animateFloatAsState(targetValue = targetPercentage, animationSpec = animSpec, label = "PercentAnim")
    
    val wVideo by androidx.compose.animation.core.animateFloatAsState(targetValue = getTargetWeight(storageInfo.videoBytes), animationSpec = animSpec)
    val wImage by androidx.compose.animation.core.animateFloatAsState(targetValue = getTargetWeight(storageInfo.imageBytes), animationSpec = animSpec)
    val wApps by androidx.compose.animation.core.animateFloatAsState(targetValue = getTargetWeight(storageInfo.appBytes), animationSpec = animSpec)
    val wDocs by androidx.compose.animation.core.animateFloatAsState(targetValue = getTargetWeight(storageInfo.documentBytes), animationSpec = animSpec)
    val wAudio by androidx.compose.animation.core.animateFloatAsState(targetValue = getTargetWeight(storageInfo.audioBytes), animationSpec = animSpec)
    val wOthers by androidx.compose.animation.core.animateFloatAsState(targetValue = getTargetWeight(storageInfo.otherBytes + storageInfo.archiveBytes), animationSpec = animSpec)
    val wFree by androidx.compose.animation.core.animateFloatAsState(targetValue = getTargetWeight(storageInfo.totalBytes - storageInfo.usedBytes), animationSpec = animSpec)

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
                    // Large Animated Percentage
                    Text(
                        text = "${animatedPercentage.toInt()}%",
                        fontSize = 36.sp,
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

            Spacer(modifier = Modifier.height(2.dp))

            // --- POPUP INFO AREA ---
            // Replaces the generic spacer to show details when highlighted
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (highlightedCategory != null) {
                    val (label, size, color) = when(highlightedCategory) {
                        "Videos" -> Triple("Videos", storageInfo.videoBytes, colorVideo)
                        "Images" -> Triple("Images", storageInfo.imageBytes, colorImage)
                        "Apps" -> Triple("Apps", storageInfo.appBytes, colorApps)
                        "Docs" -> Triple("Docs", storageInfo.documentBytes, colorDocs)
                        "Audio" -> Triple("Audio", storageInfo.audioBytes, colorAudio)
                        "Others" -> Triple("Others", storageInfo.otherBytes + storageInfo.archiveBytes, colorOthers)
                        "Free Space" -> Triple("Free Space", storageInfo.totalBytes - storageInfo.usedBytes, Color(0xFFE0E0E0))
                        else -> Triple("", 0L, Color.Transparent)
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$label: ${FileUtils.formatSize(size)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.inverseOnSurface
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp)) 

            // --- PROGRESS BAR ---
            // We use a custom Row for the segmented bar look
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Transparent) 
            ) {
                // Helper to get alpha based on highlight
                fun getAlpha(category: String): Float {
                    return if (highlightedCategory == null || highlightedCategory == category) 1f else 0.2f
                }

                if (wVideo > 0.001f) Box(modifier = Modifier.weight(wVideo).fillMaxHeight().background(colorVideo.copy(alpha = getAlpha("Videos"))))
                if (wImage > 0.001f) Box(modifier = Modifier.weight(wImage).fillMaxHeight().background(colorImage.copy(alpha = getAlpha("Images"))))
                if (wApps > 0.001f) Box(modifier = Modifier.weight(wApps).fillMaxHeight().background(colorApps.copy(alpha = getAlpha("Apps"))))
                if (wDocs > 0.001f) Box(modifier = Modifier.weight(wDocs).fillMaxHeight().background(colorDocs.copy(alpha = getAlpha("Docs"))))
                if (wAudio > 0.001f) Box(modifier = Modifier.weight(wAudio).fillMaxHeight().background(colorAudio.copy(alpha = getAlpha("Audio"))))
                if (wOthers > 0.001f) Box(modifier = Modifier.weight(wOthers).fillMaxHeight().background(colorOthers.copy(alpha = getAlpha("Others"))))
                
                // Explicitly show Free Space as a segment
                if (wFree > 0.001f) {
                     Box(modifier = Modifier.weight(wFree).fillMaxHeight().background(colorFree.copy(alpha = getAlpha("Free Space"))))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- LEGEND ---
            val onHighlight: (String, Boolean) -> Unit = { category, active ->
                highlightedCategory = if (active) category else null
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Row 1
                Row(modifier = Modifier.fillMaxWidth()) {
                    LegendItem(
                        color = colorVideo, 
                        label = "Videos", 
                        onInteraction = { onHighlight("Videos", it) },
                        modifier = Modifier.weight(1f)
                    )
                    LegendItem(
                        color = colorImage, 
                        label = "Images", 
                        onInteraction = { onHighlight("Images", it) },
                        modifier = Modifier.weight(1f)
                    )
                    LegendItem(
                        color = colorApps, 
                        label = "Apps", 
                        onInteraction = { onHighlight("Apps", it) },
                        modifier = Modifier.weight(1f)
                    )
                    LegendItem(
                        color = colorDocs, 
                        label = "Docs", 
                        onInteraction = { onHighlight("Docs", it) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Row 2
                Row(modifier = Modifier.fillMaxWidth()) {
                    LegendItem(
                        color = colorAudio, 
                        label = "Audio", 
                        onInteraction = { onHighlight("Audio", it) },
                        modifier = Modifier.weight(1f)
                    )
                    LegendItem(
                        color = colorOthers, 
                        label = "Others", 
                        onInteraction = { onHighlight("Others", it) },
                        modifier = Modifier.weight(1f)
                    )
                    // Merging the last two slots (weight 2f) to give "Free Space" text room to expand
                    LegendItem(
                        color = Color(0xFFE0E0E0), 
                        label = "Free Space", 
                        onInteraction = { onHighlight("Free Space", it) },
                        modifier = Modifier.weight(2f)
                    ) 
                }
            }
        }
    }
}

@Composable
fun LegendItem(
    color: Color, 
    label: String, 
    onInteraction: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically, 
            modifier = Modifier
                .pointerInput(Unit) {
                    if (onInteraction != null) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                onInteraction(true)
                                tryAwaitRelease()
                                isPressed = false
                                onInteraction(false)
                            }
                        )
                    }
                }
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
}



