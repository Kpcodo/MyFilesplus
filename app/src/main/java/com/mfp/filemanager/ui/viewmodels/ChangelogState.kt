package com.mfp.filemanager.ui.viewmodels

import com.mfp.filemanager.data.GitHubRelease

sealed interface ChangelogState {
    object Idle : ChangelogState
    object Loading : ChangelogState
    data class Success(val release: GitHubRelease) : ChangelogState
    data class Error(val message: String) : ChangelogState
}
