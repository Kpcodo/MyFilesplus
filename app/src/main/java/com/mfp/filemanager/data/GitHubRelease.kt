package com.mfp.filemanager.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("body") val body: String,
    @SerialName("html_url") val htmlUrl: String
)
