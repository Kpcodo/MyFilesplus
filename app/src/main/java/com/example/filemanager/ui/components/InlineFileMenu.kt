package com.example.filemanager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.filemanager.ui.components.bounceClick
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun InlineFileMenu(
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onRename: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onExtract: (() -> Unit)? = null,
    onInfo: () -> Unit,
    onShare: () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        item { 
            InlineActionButton(
                text = "Copy", 
                icon = Icons.Outlined.ContentCopy, 
                onClick = onCopy
            ) 
        }
        item { 
            InlineActionButton(
                text = "Move", 
                icon = Icons.Default.DriveFileMove, 
                onClick = onMove
            ) 
        }
        item { 
            InlineActionButton(
                text = "Share", 
                icon = Icons.Default.Share, 
                onClick = onShare
            ) 
        }
        item { 
            InlineActionButton(
                text = "Rename", 
                icon = Icons.Default.Edit, 
                onClick = onRename
            ) 
        }
        
        if (onExtract != null) {
            item {
                InlineActionButton(
                    text = "Extract",
                    icon = Icons.Default.DriveFileMove, // Or FolderZip if available
                    onClick = onExtract
                )
            }
        }
        
        item {
            InlineActionButton(
                text = "Info",
                icon = Icons.Default.Info,
                onClick = onInfo
            )
        }

        if (onDelete != null) {
            item {
                // Vertical Divider logic could go here but LazyRow makes it tricky. 
                // Just adding the Delete button at the end with Red style
                InlineActionButton(
                    text = "Delete",
                    icon = Icons.Default.Delete,
                    onClick = onDelete,
                    isDestructive = true
                )
            }
        }
    }
}


@Composable
fun InlineActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    val color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    
    Column(
        modifier = Modifier
            .width(64.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .bounceClick(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = color
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
