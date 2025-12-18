package com.example.filemanager.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filemanager.R
import com.example.filemanager.data.FileType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllCategoriesScreen(
    onCategoryClick: (FileType) -> Unit,
    onBackClick: () -> Unit
) {
    val categories = listOf(
        CategoryItemData("Images", Icons.Filled.Image, FileType.IMAGE),
        CategoryItemData("Videos", Icons.Filled.VideoFile, FileType.VIDEO),
        CategoryItemData("Audio", Icons.Filled.AudioFile, FileType.AUDIO),
        CategoryItemData("Documents", Icons.Filled.Description, FileType.DOCUMENT),
        CategoryItemData("Downloads", Icons.Filled.Download, FileType.DOWNLOAD),
        CategoryItemData("Archives", Icons.Filled.FolderZip, FileType.ARCHIVE),
        CategoryItemData("Apps", Icons.Filled.Android, FileType.APK),
        CategoryItemData("Others", Icons.Filled.DataUsage, FileType.OTHERS)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Categories") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val rows = categories.chunked(3)
            items(rows.size) { index ->
                val rowItems = rows[index]
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowItems.forEach { item ->
                        CategoryCard(
                            item = item, 
                            onClick = { onCategoryClick(item.type) }, 
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}
