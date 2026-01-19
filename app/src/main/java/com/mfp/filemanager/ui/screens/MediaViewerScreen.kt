package com.mfp.filemanager.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.*
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.mfp.filemanager.data.FileModel
import com.mfp.filemanager.data.FileType
import com.mfp.filemanager.ui.viewmodels.HomeViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    viewModel: HomeViewModel,
    initialPath: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val mediaList by viewModel.currentMediaList.collectAsState()
    
    val initialIndex = remember(mediaList, initialPath) {
        val index = mediaList.indexOfFirst { it.path == initialPath }
        if (index != -1) index else 0
    }

    if (mediaList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("No media found", color = Color.White)
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { mediaList.size }
    )

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            val currentFile = mediaList.getOrNull(pagerState.currentPage)
            TopAppBar(
                title = { 
                    Column {
                        Text(currentFile?.name ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        if (mediaList.size > 1) {
                            Text(
                                "${pagerState.currentPage + 1} / ${mediaList.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    currentFile?.let { file ->
                        IconButton(onClick = {
                            try {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", File(file.path))
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = if (file.type == FileType.VIDEO) "video/*" else "image/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Media"))
                            } catch (e: Exception) { e.printStackTrace() }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                        }
                        IconButton(onClick = {
                            viewModel.deleteFile(file.path) {
                                onBack()
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            pageSpacing = 16.dp
        ) { pageIndex ->
            val file = mediaList[pageIndex]
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (file.type == FileType.VIDEO) {
                    VideoPlayer(file.path)
                } else {
                    AsyncImage(
                        model = Uri.fromFile(File(file.path)),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun VideoPlayer(path: String) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(1f) }
    var showControls by remember { mutableStateOf(true) }
    var showProgressBarOnly by remember { mutableStateOf(false) }
    
    // Auto-hide controls after 3 seconds of playing
    LaunchedEffect(isPlaying, showControls) {
        if (isPlaying && showControls) {
            kotlinx.coroutines.delay(3000)
            showControls = false
        }
    }

    // Auto-hide mini progress bar after 2 seconds
    LaunchedEffect(showProgressBarOnly) {
        if (showProgressBarOnly) {
            kotlinx.coroutines.delay(2000)
            showProgressBarOnly = false
        }
    }

    // Progress polling logic
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                videoViewRef?.let {
                    currentPosition = it.currentPosition.toFloat()
                    duration = it.duration.toFloat().coerceAtLeast(1f)
                }
                kotlinx.coroutines.delay(500)
            }
        }
    }

    val seekRelative = { seconds: Int ->
        videoViewRef?.let {
            val target = (it.currentPosition + seconds * 1000).coerceIn(0, it.duration)
            it.seekTo(target)
            currentPosition = target.toFloat()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        val isLeft = offset.x < size.width / 2
                        seekRelative(if (isLeft) -10 else 10)
                        if (!showControls) {
                            showProgressBarOnly = true
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    setVideoPath(path)
                    setOnPreparedListener { mp ->
                        duration = mp.duration.toFloat()
                        mp.isLooping = false
                    }
                    videoViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                if (isPlaying) view.start() else view.pause()
            }
        )

        // Control Overlay (Full)
        if (showControls || !isPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                // Center Controls (Seek/Play)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    IconButton(onClick = { seekRelative(-10) }) {
                        Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = Color.White, modifier = Modifier.size(48.dp))
                    }

                    IconButton(
                        onClick = { isPlaying = !isPlaying },
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Toggle Play",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    IconButton(onClick = { seekRelative(10) }) {
                        Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = Color.White, modifier = Modifier.size(48.dp))
                    }
                }

                // Bottom Progress Bar Area
                VideoProgressBar(
                    currentPosition = currentPosition,
                    duration = duration,
                    onSeek = { 
                        currentPosition = it
                        videoViewRef?.seekTo(it.toInt())
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        } else if (showProgressBarOnly) {
            // Mini Progress Overlay (Double-tap feedback)
            Box(modifier = Modifier.fillMaxSize()) {
                VideoProgressBar(
                    currentPosition = currentPosition,
                    duration = duration,
                    onSeek = { 
                        currentPosition = it
                        videoViewRef?.seekTo(it.toInt())
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                    showTime = false
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoProgressBar(
    currentPosition: Float,
    duration: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    showTime: Boolean = true
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        Slider(
            value = currentPosition,
            onValueChange = onSeek,
            valueRange = 0f..duration,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            ),
            track = { sliderState ->
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp) // Match thumb touch height
                ) {
                    val trackHeight = 3.dp.toPx()
                    val thumbRadius = 6.dp.toPx() // Half of 12.dp
                    // Center vertically without manual offsets
                    val centerY = size.height / 2
                    
                    // Calculate fraction
                    val fraction = if (sliderState.valueRange.endInclusive - sliderState.valueRange.start == 0f) 0f else
                        (sliderState.value - sliderState.valueRange.start) / (sliderState.valueRange.endInclusive - sliderState.valueRange.start)
                    
                    val activeWidth = size.width * fraction
                    
                    // Inactive (Right side)
                    drawLine(
                        color = Color.White.copy(alpha = 0.3f),
                        start = androidx.compose.ui.geometry.Offset(activeWidth, centerY),
                        end = androidx.compose.ui.geometry.Offset(size.width, centerY),
                        strokeWidth = trackHeight,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    
                    // Active (Left side)
                    drawLine(
                        color = Color.White,
                        start = androidx.compose.ui.geometry.Offset(0f, centerY),
                        end = androidx.compose.ui.geometry.Offset(activeWidth, centerY),
                        strokeWidth = trackHeight,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            },
            thumb = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (showTime) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatMillis(currentPosition.toLong()), color = Color.White, style = MaterialTheme.typography.labelMedium)
                Text(formatMillis(duration.toLong()), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun formatMillis(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
