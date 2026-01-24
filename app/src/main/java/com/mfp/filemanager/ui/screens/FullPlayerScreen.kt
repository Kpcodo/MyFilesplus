package com.mfp.filemanager.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mfp.filemanager.ui.animations.bounceClick
import com.mfp.filemanager.ui.animations.AppMotion
import com.mfp.filemanager.ui.animations.LocalMotionScale
import com.mfp.filemanager.ui.viewmodels.AudioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    viewModel: AudioViewModel,
    onBack: () -> Unit
) {
    val metadata by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val position by viewModel.position.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()

    // Color extraction logic
    var dominantColor by remember { mutableStateOf(Color(0xFF121212)) }
    var secondaryColor by remember { mutableStateOf(Color(0xFF242424)) }
    
    LaunchedEffect(metadata?.artworkData) {
        metadata?.artworkData?.let { data ->
            // Move processing to a background thread
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                if (bitmap != null) {
                    Palette.from(bitmap).generate { palette ->
                        palette?.dominantSwatch?.let { swatch ->
                            dominantColor = Color(swatch.rgb)
                        }
                        palette?.darkVibrantSwatch?.let { swatch ->
                            secondaryColor = Color(swatch.rgb)
                        } ?: palette?.mutedSwatch?.let { swatch ->
                            secondaryColor = Color(swatch.rgb)
                        }
                    }
                }
            }
        } ?: run {
            dominantColor = Color(0xFF1A1A1A)
            secondaryColor = Color(0xFF0D0D0D)
        }
    }

    if (metadata == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(dominantColor, secondaryColor, Color.Black)
                    )
                )
            }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Now Playing", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Vinyl Section - Make it flexible to take available space
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    VinylRecord(isPlaying = isPlaying, artworkData = metadata?.artworkData)
                    
                    // Tonearm
                    Tonearm(isPlaying = isPlaying, modifier = Modifier.align(Alignment.TopEnd).padding(end = 20.dp))
                }

                // Track Info - Center section
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    Text(
                        text = metadata?.title?.toString() ?: "Unknown Track",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = metadata?.artist?.toString() ?: "Unknown Artist",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Glassmorphism Control Deck - Fixed but compact
                PlayerControlDeck(
                    isPlaying = isPlaying,
                    position = position,
                    duration = duration,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    onTogglePlay = { viewModel.togglePlayPause() },
                    onPrevious = { viewModel.playPrevious() },
                    onNext = { viewModel.playNext() },
                    onSeek = { viewModel.seekTo(it) },
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onToggleRepeat = { viewModel.toggleRepeatMode() }
                )
                
                // Extra padding for the bottom nav bar
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun VinylRecord(isPlaying: Boolean, artworkData: ByteArray?) {
    val infiniteTransition = rememberInfiniteTransition(label = "VinylSpin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    // Optimization: Only use the animation value if playing, but use a static snapshot if paused
    // To avoid "jumps", we can remember the last rotation
    var lastRotation by remember { mutableStateOf(0f) }
    
    val currentRotation = if (isPlaying) {
        lastRotation = rotation
        rotation
    } else {
        lastRotation
    }

    Box(
        modifier = Modifier
            .size(300.dp)
            .shadow(20.dp, CircleShape)
            .graphicsLayer { 
                rotationZ = currentRotation
            },
        contentAlignment = Alignment.Center
    ) {
        // Vinyl Base
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF2C2C2C), Color.Black),
                    center = center,
                    radius = size.minDimension / 2
                )
            )
            
            // Grooves - Visible and optimized
            for (i in 1..6) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.15f),
                    radius = (size.minDimension / 2.2f) - (i * 18),
                    style = Stroke(width = 1.5f)
                )
            }
        }

        // Album Art Center
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Color.DarkGray)
        ) {
            if (artworkData != null) {
                AsyncImage(
                    model = artworkData,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                     Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
                }
            }
            
            // Center Hole
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
fun Tonearm(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val motionScale = LocalMotionScale.current
    val rotationState = animateFloatAsState(
        targetValue = if (isPlaying) 35f else 0f,
        animationSpec = AppMotion.Specs.mechanicalSpring(motionScale),
        label = "TonearmRotation"
    )

    Box(
        modifier = modifier
            .width(120.dp)
            .height(180.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rotation = rotationState.value
            val pivot = Offset(size.width / 1.5f, 30.dp.toPx())
            val armWidth = 10.dp.toPx()
            val armColor = Color(0xFFC0C0C0) // Metallic Silver
            
            // 1. Draw FIXED Base Pillar (Doesn't rotate)
            drawCircle(
                color = Color(0xFF222222), 
                radius = 24.dp.toPx(), 
                center = pivot
            )
            drawCircle(
                color = Color(0xFF444444), 
                radius = 18.dp.toPx(), 
                center = pivot,
                style = Stroke(width = 4.dp.toPx())
            )

            // 2. Draw ROTATING parts
            withTransform({
                rotate(degrees = rotation, pivot = pivot)
            }) {
                // Main Arm
                drawLine(
                    color = armColor,
                    start = pivot,
                    end = Offset(size.width / 2, size.height - 30.dp.toPx()),
                    strokeWidth = armWidth,
                    cap = StrokeCap.Round
                )
                
                // Headshell (The part that holds the needle)
                drawRoundRect(
                    color = Color(0xFF333333),
                    topLeft = Offset((size.width / 2) - 12.dp.toPx(), size.height - 40.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(24.dp.toPx(), 32.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                )
                
                // Stylus highlight
                drawCircle(
                    color = Color.Red.copy(alpha = 0.8f),
                    radius = 3.dp.toPx(),
                    center = Offset(size.width / 2, size.height - 15.dp.toPx())
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerControlDeck(
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(230.dp),
        color = Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Row 1: Utility Icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* Volume logic */ }) {
                    Icon(Icons.Rounded.VolumeUp, null, tint = Color.White)
                }
                Row {
                    IconButton(onClick = { /* Share logic */ }) {
                        Icon(Icons.Rounded.Share, null, tint = Color.White)
                    }
                    IconButton(onClick = { /* Favorite logic */ }) {
                        Icon(Icons.Rounded.FavoriteBorder, null, tint = Color.White)
                    }
                }
            }

            // Row 2: Seekbar and Timestamps
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = if (duration > 0) position.toFloat() / duration.toFloat() else 0f,
                    onValueChange = { onSeek((it * duration).toLong()) },
                    modifier = Modifier.fillMaxWidth(),
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(width = 8.dp, height = 20.dp)
                                .shadow(4.dp, RoundedCornerShape(4.dp))
                                .background(Color.White, RoundedCornerShape(4.dp))
                        )
                    },
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            modifier = Modifier.height(3.dp),
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                            )
                        )
                    }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(position),
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = formatTime(duration),
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }

            // Row 3: Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        Icons.Rounded.Shuffle, 
                        null, 
                        tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Surface(
                        onClick = onTogglePlay,
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(72.dp).shadow(8.dp, CircleShape).bounceClick()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }

                IconButton(onClick = onToggleRepeat) {
                    val repeatIcon = when (repeatMode) {
                        androidx.media3.common.Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                        else -> Icons.Rounded.Repeat
                    }
                    val isRepeatActive = repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF
                    Icon(
                        repeatIcon, 
                        null, 
                        tint = if (isRepeatActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
