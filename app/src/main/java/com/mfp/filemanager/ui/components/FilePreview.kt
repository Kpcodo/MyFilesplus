package com.mfp.filemanager.ui.components

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.mfp.filemanager.data.FileModel
import com.mfp.filemanager.data.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun FileThumbnail(
    file: FileModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val extension = file.path.substringAfterLast('.', "").lowercase()
    
    // Determine content to show
    val fileType = file.type

    when (fileType) {
        FileType.IMAGE -> {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file.path)
                    .crossfade(true)
                    .size(256) // Resize for thumbnail
                    .build(),
                contentDescription = file.name,
                modifier = modifier,
                contentScale = ContentScale.Crop,
                error = {
                    GenericFileIcon(fileType = fileType, modifier = Modifier.fillMaxSize())
                }
            )
        }
        FileType.VIDEO -> {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file.path)
                    .crossfade(true)
                    .size(256)
                    .build(),
                contentDescription = file.name,
                modifier = modifier,
                contentScale = ContentScale.Crop,
                error = {
                    GenericFileIcon(fileType = fileType, modifier = Modifier.fillMaxSize())
                }
            )
        }
        FileType.APK -> {
            ApkIcon(context = context, filePath = file.path, modifier = modifier, fileType = fileType)
        }
        FileType.DOCUMENT -> {
            if (extension == "pdf") {
                PdfThumbnail(filePath = file.path, modifier = modifier, fileType = fileType)
            } else {
                Box(modifier = modifier, contentAlignment = Alignment.Center) {
                    GenericFileIcon(fileType = fileType, extension = extension)
                }
            }
        }
        else -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                GenericFileIcon(fileType = fileType, extension = extension)
            }
        }
    }
}

@Composable
private fun ApkIcon(
    context: Context,
    filePath: String,
    modifier: Modifier,
    fileType: FileType
) {
    var iconBitmap by remember(filePath) { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                val packageInfo = context.packageManager.getPackageArchiveInfo(filePath, 0)
                if (packageInfo != null) {
                    packageInfo.applicationInfo.sourceDir = filePath
                    packageInfo.applicationInfo.publicSourceDir = filePath
                    val drawable = packageInfo.applicationInfo.loadIcon(context.packageManager)
                    iconBitmap = drawable.toBitmap()
                } else {
                    error = true
                }
            } catch (e: Exception) {
                Log.e("FileThumbnail", "Error loading APK icon", e)
                error = true
            }
        }
    }

    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap!!.asImageBitmap(),
            contentDescription = "APK Icon",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
             // Fallback or loading indicator?
             if (error) {
                 GenericFileIcon(fileType = fileType)
             }
        }
    }
}

@Composable
private fun PdfThumbnail(
    filePath: String,
    modifier: Modifier,
    fileType: FileType
) {
    var pdfBitmap by remember(filePath) { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(descriptor)
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        val width = page.width
                        val height = page.height
                        // Create a bitmap for the preview - scaling it down if necessary
                        // Using a fixed small size for thumbnail
                        val w = 200
                        val h = (height.toFloat() / width.toFloat() * w).toInt()
                        
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        // Canvas to draw white background
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
                         error = true
                    }
                } else {
                    error = true
                }
            } catch (e: Exception) {
                Log.e("FileThumbnail", "Error rendering PDF", e)
                error = true
            }
        }
    }

    if (pdfBitmap != null) {
        Image(
            bitmap = pdfBitmap!!.asImageBitmap(),
            contentDescription = "PDF Preview",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
             // Loading or Error
             if (error) {
                  GenericFileIcon(fileType = fileType, extension = "pdf")
             }
        }
    }
}

@Composable
fun GenericFileIcon(
    fileType: FileType,
    extension: String = "",
    modifier: Modifier = Modifier
) {
    val icon: ImageVector = when {
        fileType == FileType.AUDIO -> Icons.Default.AudioFile
        fileType == FileType.VIDEO -> Icons.Default.VideoFile
        fileType == FileType.IMAGE -> Icons.Default.Image
        fileType == FileType.ARCHIVE -> Icons.Default.FolderZip
        fileType == FileType.APK -> Icons.Default.Android
        extension == "pdf" -> Icons.Default.PictureAsPdf
        extension == "txt" -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.fillMaxSize(0.6f) // Scale icon down a bit within the box
    )
}
