package com.mfp.filemanager.ui.components

import com.mfp.filemanager.ui.animations.bounceClick
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
import com.mfp.filemanager.ui.components.FileThumbnail
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
    modifier: Modifier = Modifier,
    allowDelete: Boolean = true,
    showMenuButton: Boolean = true
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
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
                FileThumbnail(
                    file = file,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
            } else if (showMenuButton) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicListItem(
    file: FileModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuAction: (String) -> Unit
) {
    val backgroundColor = MaterialTheme.colorScheme.surface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .background(backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileThumbnail(file, modifier = Modifier.size(52.dp), iconSize = 1.0f)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name, 
                    style = MaterialTheme.typography.bodyMedium, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = file.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: FileModel,
    isSelected: Boolean,
    selectionMode: Boolean,
    iconSize: Float,
    isCompact: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuAction: (String) -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .background(backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = if (isCompact) 2.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileThumbnail(file, modifier = Modifier.size(48.dp * iconSize), iconSize = iconSize)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name, 
                    style = MaterialTheme.typography.bodyLarge, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = if (file.isDirectory) {
                    "${file.itemCount} items"
                } else {
                    "${FileUtils.formatSize(file.size)} | ${FileUtils.formatDate(file.dateModified)}"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() }, modifier = Modifier.padding(start = 16.dp))
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
                onDelete = { showMenu = false; onMenuAction("delete") },
                onExtract = if (file.type == FileType.ARCHIVE) { { showMenu = false; onMenuAction("extract") } } else null,
                onInfo = { 
                    onMenuAction("info") 
                    showMenu = false 
                },
                onShare = { showMenu = false; onMenuAction("share") }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(
    file: FileModel,
    isSelected: Boolean,
    selectionMode: Boolean,
    iconSize: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuAction: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface)
    ) {
        Column {
            val adjustedIconSize = if (file.isDirectory) iconSize * 2.5f else iconSize
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(if (file.isDirectory) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                FileThumbnail(
                    file = file,
                    modifier = Modifier.size(64.dp * adjustedIconSize),
                    iconSize = adjustedIconSize,
                    transparentBackground = true
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = if (file.isDirectory) "${file.itemCount} items" else FileUtils.formatSize(file.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (selectionMode) {
                    Checkbox(checked = isSelected, onCheckedChange = { onClick() })
                } else {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                        }
                        FileItemMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            onMove = { showMenu = false; onMenuAction("move") },
                            onCopy = { showMenu = false; onMenuAction("copy") },
                            onRename = { showMenu = false; onMenuAction("rename") },
                            onDelete = { showMenu = false; onMenuAction("delete") },
                            onExtract = if (file.type == FileType.ARCHIVE) { { showMenu = false; onMenuAction("extract") } } else null,
                            onInfo = { showMenu = false; onMenuAction("info") },
                            onShare = { showMenu = false; onMenuAction("share") }
                        )
                    }
                }
            }
        }
    }
}
