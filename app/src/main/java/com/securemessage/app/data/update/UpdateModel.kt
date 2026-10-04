package com.securemessage.app.data.update

import java.io.File

data class GitHubRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSize: Long
)

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    data class Available(val release: GitHubRelease, val currentVersion: String) : UpdateState
    data class UpToDate(val currentVersion: String) : UpdateState
    data class Downloading(
        val release: GitHubRelease,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateState
    data class ReadyToInstall(val release: GitHubRelease, val apkFile: File) : UpdateState
    data class Error(val message: String, val retryable: Boolean = true) : UpdateState
}


/**
 * GitHub release notes are Markdown ("### Title", "**bold**", "- item"). The update dialog
 * shows plain text, so strip the symbols and turn list dashes into bullets.
 */
fun cleanReleaseNotes(body: String): String {
    return body
        .replace("\r\n", "\n")
        .lines()
        .joinToString("\n") { rawLine ->
            var line = rawLine.trim()
            line = line.replace(Regex("^#{1,6}\\s*"), "")
            line = line.replace(Regex("^[-*]\\s+"), "• ")
            line = line.replace("**", "").replace("__", "").replace("`", "")
            line
        }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
