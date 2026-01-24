package com.mfp.filemanager.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor

import android.media.MediaMetadataRetriever

import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.size.Precision
import com.mfp.filemanager.data.FileModel
import com.mfp.filemanager.data.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun FileThumbnail(
    file: FileModel,
    modifier: Modifier = Modifier,
    iconSize: Float = 1.0f,
    transparentBackground: Boolean = false
) {
    val context = LocalContext.current
    val extension = file.path.substringAfterLast('.', "").lowercase()
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (transparentBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (file.isDirectory) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = file.name,
                modifier = Modifier.size(32.dp * iconSize),
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            when (file.type) {
                FileType.IMAGE -> {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(File(file.path))
                            .crossfade(true)
                            .size(256)
                            .build(),
                        contentDescription = file.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = {
                            GenericFileIcon(fileType = file.type, iconSize = iconSize)
                        }
                    )
                }
                FileType.VIDEO -> {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(File(file.path))
                            .videoFrameMillis(2000) // Avoid black intro frames by skipping 2 seconds
                            .size(256)
                            .precision(Precision.INEXACT)
                            .crossfade(true)
                            .build(),
                        contentDescription = file.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = {
                            GenericFileIcon(fileType = file.type, iconSize = iconSize)
                        }
                    )
                }
                FileType.AUDIO -> {
                    AudioThumbnail(path = file.path, iconSize = iconSize, modifier = Modifier.fillMaxSize())
                }
                FileType.APK -> {
                    ApkIcon(path = file.path, iconSize = iconSize, modifier = Modifier.fillMaxSize())
                }
                FileType.DOCUMENT -> {
                    if (extension == "pdf") {
                        PdfThumbnail(path = file.path, iconSize = iconSize, modifier = Modifier.fillMaxSize())
                    } else {
                        GenericFileIcon(fileType = file.type, extension = extension, iconSize = iconSize)
                    }
                }
                else -> {
                    GenericFileIcon(fileType = file.type, extension = extension, iconSize = iconSize)
                }
            }
        }
    }
}

@Composable
private fun AudioThumbnail(
    path: String,
    iconSize: Float,
    modifier: Modifier
) {
    val context = LocalContext.current
    
    // Use Coil with custom fetcher for audio album art - enables automatic caching
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(File(path))  // Pass File object for custom fetcher
            .memoryCachePolicy(CachePolicy.ENABLED)  // Enable memory cache
            .diskCachePolicy(CachePolicy.ENABLED)    // Enable disk cache
            .crossfade(100)  // Short crossfade for smooth appearance
            .size(256)  // Consistent thumbnail size
            .build(),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        loading = {
            // Show icon while loading
            GenericFileIcon(fileType = FileType.AUDIO, iconSize = iconSize)
        },
        error = {
            // Fallback to icon if no album art
            GenericFileIcon(fileType = FileType.AUDIO, iconSize = iconSize)
        }
    )
}

@Composable
private fun ApkIcon(
    path: String,
    iconSize: Float,
    modifier: Modifier
) {
    val context = LocalContext.current
    var iconBitmap by remember(path) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(path) {
        withContext(Dispatchers.IO) {
            try {
                val packageInfo = context.packageManager.getPackageArchiveInfo(path, 0)
                val appInfo = packageInfo?.applicationInfo
                if (appInfo != null) {
                    appInfo.sourceDir = path
                    appInfo.publicSourceDir = path
                    val drawable = appInfo.loadIcon(context.packageManager)
                    iconBitmap = drawable.toBitmap()
                }
            } catch (e: Exception) {
                Log.e("FileThumbnail", "Error loading APK icon", e)
            }
        }
    }

    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        GenericFileIcon(fileType = FileType.APK, iconSize = iconSize)
    }
}



@Composable
private fun PdfThumbnail(
    path: String,
    iconSize: Float,
    modifier: Modifier
) {
    var pdfBitmap by remember(path) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(path) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                if (file.exists()) {
                    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(descriptor)
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        val targetWidth = 256
                        val targetHeight = (page.height.toFloat() / page.width.toFloat() * targetWidth).toInt()
                        
                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bitmap)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        renderer.close()
                        descriptor.close()
                        pdfBitmap = bitmap
                    } else {
                        renderer.close()
                        descriptor.close()
                    }
                }
            } catch (e: Exception) {
                Log.e("FileThumbnail", "Error rendering PDF", e)
            }
        }
    }

    if (pdfBitmap != null) {
        Image(
            bitmap = pdfBitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        GenericFileIcon(fileType = FileType.DOCUMENT, extension = "pdf", iconSize = iconSize)
    }
}

@Composable
fun GenericFileIcon(
    fileType: FileType,
    extension: String = "",
    iconSize: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    val (icon: ImageVector, color: Color) = when {
        fileType == FileType.AUDIO -> Icons.Default.MusicNote to Color(0xFF8E24AA)
        fileType == FileType.VIDEO -> Icons.Default.VideoFile to Color(0xFFF44336)
        fileType == FileType.IMAGE -> Icons.Default.Image to Color(0xFF2196F3)
        fileType == FileType.APK -> Icons.Default.Android to Color(0xFF4CAF50)
        extension == "pdf" -> Icons.Default.PictureAsPdf to Color(0xFFE91E63)
        fileType == FileType.DOCUMENT -> Icons.Default.Description to Color(0xFFFBC02D)
        fileType == FileType.ARCHIVE -> Icons.Default.Folder to MaterialTheme.colorScheme.secondary
        else -> Icons.AutoMirrored.Filled.InsertDriveFile to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = color,
        modifier = modifier.size(32.dp * iconSize)
    )
}
