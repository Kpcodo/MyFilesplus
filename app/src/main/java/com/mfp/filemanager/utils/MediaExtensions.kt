package com.mfp.filemanager.utils

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import android.net.Uri
import java.io.File

fun File.toMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setUri(Uri.fromFile(this))
        .setMediaId(this.absolutePath)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(this.name)
                .build()
        )
        .build()
}

fun com.mfp.filemanager.data.FileModel.toMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setUri(Uri.fromFile(File(this.path)))
        .setMediaId(this.path)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(this.name)
                .setArtist(this.artist)
                .build()
        )
        .build()
}
