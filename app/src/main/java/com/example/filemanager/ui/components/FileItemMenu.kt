package com.example.filemanager.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun FileItemMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onRename: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onExtract: (() -> Unit)? = null,
    onInfo: () -> Unit,
    onShare: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            text = { Text("Move to") },
            onClick = onMove,
            leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null) }
        )
        DropdownMenuItem(
            text = { Text("Copy to") },
            onClick = onCopy,
            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) }
        )
        DropdownMenuItem(
            text = { Text("Rename") },
            onClick = onRename,
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
        )
        if (onExtract != null) {
            DropdownMenuItem(
                text = { Text("Extract") },
                onClick = onExtract,
                leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null) }
            )
        }
        if (onDelete != null) {
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = onDelete,
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
            )
        }
        DropdownMenuItem(
            text = { Text("Folder Info") },
            onClick = onInfo,
            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
        )
        DropdownMenuItem(
            text = { Text("Share") },
            onClick = onShare,
            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
        )
    }
}
