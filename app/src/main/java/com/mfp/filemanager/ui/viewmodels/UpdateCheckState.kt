package com.mfp.filemanager.ui.viewmodels

import com.mfp.filemanager.data.GitHubRelease
import java.io.File

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data object UpToDate : UpdateCheckState
    data class UpdateAvailable(val release: GitHubRelease) : UpdateCheckState
    data class Error(val message: String) : UpdateCheckState
    data class Downloading(val progress: Float) : UpdateCheckState
    data class DownloadFinished(val file: File) : UpdateCheckState
}
