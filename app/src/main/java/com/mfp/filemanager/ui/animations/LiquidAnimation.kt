package com.mfp.filemanager.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import com.mfp.filemanager.data.FileUtils

@Composable
fun LiquidCleaningOverlay(
    isVisible: Boolean,
    amountToClean: Long,
    onFinished: () -> Unit
) {
    if (!isVisible) return

    var animationTriggered by remember { mutableStateOf(false) }
    var showSuccessMessage by remember { mutableStateOf(false) }
    
    val cleanedAmountAnim = remember { Animatable(amountToClean.toFloat()) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            animationTriggered = true
            showSuccessMessage = false
            cleanedAmountAnim.snapTo(amountToClean.toFloat())
            
            // Animate value to 0 over 1500ms (simulating cleaning)
            cleanedAmountAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(1500, easing = FastOutSlowInEasing)
            )
            
            showSuccessMessage = true
            delay(1000) // Show "All Cleaned Up" for 1 second
            
            animationTriggered = false
            onFinished()
        }
    }

    if (animationTriggered) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(100f) // Ensure it's on top
                .background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            LiquidWave(
                modifier = Modifier.fillMaxSize()
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!showSuccessMessage) {
                    Text(
                        text = "Cleaning...",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = FileUtils.formatSize(cleanedAmountAnim.value.toLong()),
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "All Cleaned Up!",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

data class Bubble(
    var x: Float,
    var y: Float,
    val radius: Float,
    val speed: Float,
    val initialPhase: Float
)

@Composable
fun LiquidWave(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    
    // Animate phase for movement
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // Vertical fill progress
    var progressState by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        progressState = 1.3f // Go slightly above 1.0 to fully fill
    }
    
    val fillProgress by animateFloatAsState(
        targetValue = progressState,
        animationSpec = tween(1500, easing = FastOutSlowInEasing), // Slightly longer for dramatic effect
        label = "fillProgress"
    )

    // Bubble Logic Optimized: Use a simple list and manual invalidation trigger
    val bubbles = remember { ArrayList<Bubble>() }
    var tick by remember { mutableStateOf(0L) } // Trigger for recomposition/draw
    
    // Game loop for bubbles
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { time ->
                // Remove bubbles that go too high
                bubbles.removeAll { it.y < -0.1f }

                // Add new bubbles randomly if progress is active
                if (bubbles.size < 25 && fillProgress < 1.0f) {
                    bubbles.add(
                        Bubble(
                            x = Random.nextFloat(), // stored as relative 0..1, resolve in draw
                            y = 1.0f, // start at bottom (relative)
                            radius = Random.nextFloat() * 10f + 5f,
                            speed = Random.nextFloat() * 0.015f + 0.005f,
                            initialPhase = Random.nextFloat() * 100f
                        )
                    )
                }

                // Move existing bubbles
                bubbles.forEach { bubble ->
                    bubble.y -= bubble.speed
                }
                tick = time // Trigger redraw
            }
        }
    }

    // Define distinct shades for depth perception
    val deepWaterColor = Color(0xFF1565C0) // Dark Blue 800
    val mediumWaterColor = Color(0xFF42A5F5) // Blue 400
    val lightWaterColor = Color(0xFF90CAF9) // Blue 200

    // Reuse Path object to avoid allocation every frame
    val path = remember { Path() }

    Canvas(modifier = modifier) {
        // Read tick to ensure we redraw when it changes
        val _tick = tick 
        
        val width = size.width
        val height = size.height

        val baseWaterLevel = height * (1f - fillProgress)
        
        // --- Layer 1 (Back) - Deepest Shade ---
        drawWavePath(
            path = path,
            width = width,
            height = height,
            waterLevel = baseWaterLevel - 35f, // Higher up in the background
            waveAmplitude = 0.035f * height,
            wavePhase = wavePhase * 0.7f,
            waveFrequency = 1.2f,
            color = deepWaterColor.copy(alpha = 1.0f) // Solid background
        )

        // --- Layer 2 (Middle) - Medium Shade ---
        drawWavePath(
            path = path,
            width = width,
            height = height,
            waterLevel = baseWaterLevel - 15f, // Slightly higher than front
            waveAmplitude = 0.04f * height,
            wavePhase = wavePhase * 0.9f + 2f,
            waveFrequency = 0.9f,
            color = mediumWaterColor.copy(alpha = 0.85f)
        )
        
        // --- Bubbles (Behind Front Layer) ---
        bubbles.forEach { bubble ->
            val bubbleY = bubble.y * height
            if (bubbleY > baseWaterLevel - 20f) { // Visible if below back waves
                drawCircle(
                    color = Color.White.copy(alpha = 0.4f),
                    radius = bubble.radius,
                    center = androidx.compose.ui.geometry.Offset(
                        x = bubble.x * width,
                        y = bubbleY
                    )
                )
            }
        }

        // --- Layer 3 (Front - Main) - Lightest Shade ---
        drawWavePath(
            path = path,
            width = width,
            height = height,
            waterLevel = baseWaterLevel,
            waveAmplitude = 0.05f * height,
            wavePhase = wavePhase,
            waveFrequency = 1f,
            color = lightWaterColor.copy(alpha = 0.7f), // Semi-transparent to blend
            isGradient = true,
            gradientEndColor = mediumWaterColor // Blend into medium
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWavePath(
    path: Path,
    width: Float,
    height: Float,
    waterLevel: Float,
    waveAmplitude: Float,
    wavePhase: Float,
    waveFrequency: Float,
    color: Color,
    isGradient: Boolean = false,
    gradientEndColor: Color = color
) {
    path.reset() // Clear previous path reuse
    path.moveTo(0f, height) // Bottom Left
    path.lineTo(0f, waterLevel)

    var x = 0f
    val step = 20f
    
    // Draw Wave
    while (x <= width) {
        val relativeX = (x / width) * 2 * Math.PI.toFloat() * waveFrequency
        val yOffset = sin(relativeX + wavePhase) * waveAmplitude
        path.lineTo(x, waterLevel + yOffset)
        x += step
    }

    path.lineTo(width, height) // Bottom Right
    path.close()

    if (isGradient) {
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(
                    color, // Light at top
                    gradientEndColor.copy(alpha = 0.95f), // Deep at bottom
                ),
                startY = waterLevel - waveAmplitude,
                endY = height
            )
        )
    } else {
        drawPath(
            path = path,
            color = color
        )
    }
}
