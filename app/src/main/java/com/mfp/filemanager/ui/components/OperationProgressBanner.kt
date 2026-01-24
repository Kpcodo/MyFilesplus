package com.mfp.filemanager.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

data class OperationStatus(
    val isRunning: Boolean = false,
    val type: OperationType = OperationType.NONE,
    val progress: Float = 0f,
    val processedCount: Int = 0,
    val totalCount: Int = 0
)

enum class OperationType {
    NONE, RESTORE, DELETE, MOVE, COPY, EXTRACT, TRASH
}

@Composable
fun OperationProgressBanner(
    status: OperationStatus,
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Local state to manage visibility decoupled from data state
    var shouldShow by remember { mutableStateOf(false) }
    
    // Hold the last valid status to display while animating out or finishing up
    var activeStatus by remember { mutableStateOf(status) }

    // If real status is running, we definitely show, and we update our active status
    if (status.isRunning) {
        shouldShow = true
        activeStatus = status
    }

    // Decoupled Visual Progress: Slow Chaser
    // Force a slow animation duration to ensure user sees the progress bar moving
    // Target value: 
    // - If running: use actual progress
    // - If not running but showing: Aim for 1.0 (completion chase)
    // - Else: 0
    val targetProgress = if (status.isRunning) status.progress else if (shouldShow) 1f else 0f
    
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow 
        ),
        label = "ChaserProgress"
    )

    // Visibility Logic: Keep showing until Visual Progress finishes (reaches ~1.0)
    LaunchedEffect(status.isRunning, animatedProgress) {
        if (!status.isRunning) {
            // Data is done. But wait for visual to catch up.
            // If we are showing and we were chasing up to 100%
            if (shouldShow && animatedProgress >= 0.99f) {
                // Give it a tiny moment at 100% before hiding
                delay(100) 
                shouldShow = false
            } else if (shouldShow && activeStatus.progress < 1.0f && !status.isRunning) {
               // Case where operation was cancelled or errored mid-way?
               // If operation went from running to not running, but progress wasn't complete...
               // For now, let's assume we close if we are not "chasing completion"
               if (targetProgress == 0f) {
                   shouldShow = false
               }
            }
        }
    }

    // Dimming Overlay: Appears behind the card
    AnimatedVisibility(
        visible = shouldShow,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(enabled = true, onClick = { /* Consumes click to prevent background interaction */ })
        )
    }

    AnimatedVisibility(
        visible = shouldShow,
        enter = slideInVertically(animationSpec = tween(100)) { it } + fadeIn(animationSpec = tween(100)),
        exit = slideOutVertically(animationSpec = tween(200)) { it } + fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF2C2C2C))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header (Icon + Title + Close)
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(getIconBackgroundColor(activeStatus.type).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getOperationIcon(activeStatus.type),
                            contentDescription = null,
                            tint = getIconBackgroundColor(activeStatus.type),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = getTitle(activeStatus.type),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = Color.Gray,
                        modifier = Modifier
                            .clickable { onCancel() }
                            .padding(4.dp)
                            .size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Shimmer Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF444444)) // Light gray track
                ) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    // Shimmer Effect
                    // Colors based on Primary Theme Color
                    val shimmerColors = listOf(
                        primaryColor,
                        primaryColor.copy(alpha = 0.6f),
                        primaryColor
                    )
                    
                    val transition = rememberInfiniteTransition(label = "Shimmer")
                    val translateAnim by transition.animateFloat(
                        initialValue = -500f, // Start off-screen
                        targetValue = 2000f, // Ensure it clears most screen widths
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 1500, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "ShimmerTranslate"
                    )

                    val brush = Brush.linearGradient(
                        colors = listOf(
                            primaryColor,
                            Color.White.copy(alpha = 0.4f),
                            primaryColor
                        ),
                        start = Offset(x = translateAnim - 300f, y = 0f),
                        end = Offset(x = translateAnim, y = 0f),
                        tileMode = TileMode.Clamp
                    )

                    // Fill based on Decoupled Visual Progress
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp)) // Half round tip
                            .background(brush)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Stats Row: % Left, Count Right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        color = MaterialTheme.colorScheme.primary, // Use Theme Primary
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    
                    Text(
                        text = "${activeStatus.processedCount} / ${activeStatus.totalCount}",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

private fun getOperationIcon(type: OperationType): ImageVector {
    return when (type) {
        OperationType.RESTORE -> Icons.Default.Restore
        OperationType.DELETE -> Icons.Default.DeleteForever
        OperationType.MOVE -> Icons.AutoMirrored.Filled.DriveFileMove
        OperationType.COPY -> Icons.Outlined.ContentCopy
        OperationType.EXTRACT -> Icons.Default.Folder
        else -> Icons.Default.Folder
    }
}

private fun getTitle(type: OperationType): String {
    return when (type) {
        OperationType.RESTORE -> "Restoring files..."
        OperationType.DELETE -> "Deleting permanently..."
        OperationType.MOVE -> "Moving files..."
        OperationType.COPY -> "Copying files..."
        OperationType.EXTRACT -> "Extracting archive..."
        OperationType.TRASH -> "Moving to Bin..."
        else -> "Processing..."
    }
}

@Composable
private fun getIconBackgroundColor(type: OperationType): Color {
    return when (type) {
        OperationType.DELETE -> Color(0xFFD32F2F) // Red for permanent delete
        OperationType.TRASH -> Color(0xFF795548) // Brown for trash
        OperationType.RESTORE -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary // Default to accent for Move/Copy
    }
}
