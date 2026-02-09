package com.mfp.filemanager.data.clipboard

import com.mfp.filemanager.data.FileModel

data class FileClipboard(
    val items: List<FileModel>,
    val operation: ClipboardOperation,
    val sourcePath: String
)
