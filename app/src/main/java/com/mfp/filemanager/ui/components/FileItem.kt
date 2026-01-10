package com.mfp.filemanager.ui.components
import com.mfp.filemanager.ui.animations.bounceClick

import android.content.ContentUris
import android.provider.MediaStore
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.mfp.filemanager.data.FileModel
import com.mfp.filemanager.data.FileType
import com.mfp.filemanager.data.FileUtils

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailedFileItem(
    file: FileModel, 
    isSelected: Boolean, 
    selectionMode: Boolean, 
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuAction: (String) -> Unit,
    allowDelete: Boolean = true
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bounceClick(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                if (file.type == FileType.IMAGE || file.type == FileType.VIDEO || file.type == FileType.AUDIO) {
                    val imageModel = ContentUris.withAppendedId(
                        MediaStore.Files.getContentUri("external"),
                        file.id
                    )

                    val errorIcon = when (file.type) {
                        FileType.VIDEO -> Icons.Default.VideoFile
                        FileType.AUDIO -> Icons.Default.MusicNote
                        else -> Icons.Default.Description
                    }

                    val imageRequest = remember(file.id) {
                        ImageRequest.Builder(context)
                            .data(ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), file.id))
                            .crossfade(true)
                            .build()
                    }

                    coil.compose.AsyncImage(
                        model = imageRequest,
                        contentDescription = file.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(id = android.R.drawable.ic_menu_gallery),
                        error = painterResource(id = android.R.drawable.ic_menu_report_image)
                    )
                } else {
                    val icon = when (file.type) {
                        FileType.ARCHIVE -> Icons.Default.FolderZip
                        else -> Icons.Default.Description
                    }
                    Icon(icon, contentDescription = file.name, tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${FileUtils.formatSize(file.size)} • ${FileUtils.formatDate(file.dateModified)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (selectionMode) {
                 Checkbox(
                     checked = isSelected, 
                     onCheckedChange = { onClick() },
                     modifier = Modifier.padding(start = 8.dp)
                 )
            } else {
                IconButton(onClick = { showMenu = !showMenu }) {
                     Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        modifier = Modifier.bounceClick()
                    )
                }
            }
        }
        
        if (showMenu) {
            InlineFileMenu(
                onMove = { showMenu = false; onMenuAction("move") },
                onCopy = { showMenu = false; onMenuAction("copy") },
                onRename = { showMenu = false; onMenuAction("rename") },
                onDelete = if (allowDelete) { { showMenu = false; onMenuAction("delete") } } else null,
                onExtract = if (file.type == FileType.ARCHIVE) { { showMenu = false; onMenuAction("extract") } } else null,
                onInfo = { onMenuAction("info"); showMenu = false },
                onShare = { showMenu = false; onMenuAction("share") }
            )
        }
    }
}
