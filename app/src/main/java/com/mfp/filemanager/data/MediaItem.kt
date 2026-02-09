package com.mfp.filemanager.data

import android.net.Uri
import android.os.Parcelable
 import kotlinx.parcelize.Parcelize

sealed class MediaItem : Parcelable {
    abstract val uri: Uri
    abstract val name: String

    @Parcelize
    data class Image(
        override val uri: Uri,
        override val name: String
    ) : MediaItem()

    @Parcelize
    data class Video(
        override val uri: Uri,
        override val name: String
    ) : MediaItem()
}
