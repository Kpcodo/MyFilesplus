package com.mfp.filemanager.ui.adapters

import com.mfp.filemanager.data.FileModel

sealed class RecentsListItem {
    data class Header(val title: String) : RecentsListItem()
    data class FileItem(val file: FileModel) : RecentsListItem()
}
