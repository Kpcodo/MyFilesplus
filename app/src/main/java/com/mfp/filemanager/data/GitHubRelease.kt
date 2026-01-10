package com.mfp.filemanager.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("body") val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("assets") val assets: List<GitHubAsset> = emptyList()
) {
    @kotlinx.serialization.Transient
    var relatedCommits: List<GitHubCommit>? = null

    val releaseDescription: String
        get() {
            if (!body.isNullOrBlank()) return body
            
            return relatedCommits?.joinToString(separator = "\n\n") { commit ->
                val message = commit.commit.message.substringBefore("\n")
                val lowerMsg = message.lowercase()
                val prefix = when {
                    lowerMsg.startsWith("feat") -> "✨"
                    lowerMsg.startsWith("fix") -> "🐛"
                    lowerMsg.startsWith("docs") -> "📝"
                    lowerMsg.startsWith("style") -> "🎨"
                    lowerMsg.startsWith("refactor") -> "♻️"
                    lowerMsg.startsWith("perf") -> "⚡"
                    lowerMsg.startsWith("test") -> "✅"
                    lowerMsg.startsWith("chore") -> "🔧"
                    lowerMsg.startsWith("release") -> "🚀"
                    else -> "•"
                }
                 "$prefix $message"
            } ?: "No release notes available"
        }
}

@Serializable
data class GitHubAsset(
    @SerialName("name") val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    @SerialName("size") val size: Long,
    @SerialName("content_type") val contentType: String
)

@Serializable
data class GitHubCommit(
    @SerialName("sha") val sha: String,
    @SerialName("commit") val commit: CommitInfo
)

@Serializable
data class CommitInfo(
    @SerialName("message") val message: String,
    @SerialName("author") val author: CommitAuthor
)

@Serializable
data class CommitAuthor(
    @SerialName("name") val name: String,
    @SerialName("date") val date: String
)
