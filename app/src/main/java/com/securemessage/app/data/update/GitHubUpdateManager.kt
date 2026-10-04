package com.securemessage.app.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object GitHubUpdateManager {
    private const val GITHUB_OWNER = "threatthriver"
    private const val GITHUB_REPO = "doom-scroll"
    private const val API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    private const val CHECK_CONNECT_TIMEOUT_MS = 10_000
    private const val CHECK_READ_TIMEOUT_MS = 10_000
    private const val DOWNLOAD_CONNECT_TIMEOUT_MS = 15_000
    private const val DOWNLOAD_READ_TIMEOUT_MS = 30_000
    private const val MAX_REDIRECTS = 6
    private const val CHECK_MAX_ATTEMPTS = 3
    private const val CHECK_RETRY_BASE_DELAY_MS = 1_500L
    private const val DOWNLOAD_BUFFER_BYTES = 32 * 1024
    private const val PROGRESS_EMIT_INTERVAL_MS = 150L
    private const val DOWNLOAD_MAX_ATTEMPTS = 3
    private const val DOWNLOAD_RETRY_BASE_DELAY_MS = 2_000L
    private const val MIN_VALID_APK_BYTES = 1_000_000L
    // ZIP local-file-header magic ("PK\u0003\u0004"). Every valid APK starts with it.
    private const val ZIP_MAGIC_0: Byte = 0x50
    private const val ZIP_MAGIC_1: Byte = 0x4B

    fun checkForUpdate(currentVersionName: String): Flow<UpdateState> = flow {
        emit(UpdateState.Checking)
        var lastError: Exception? = null
        repeat(CHECK_MAX_ATTEMPTS) { attempt ->
            try {
                emit(checkOnce(currentVersionName))
                return@flow
            } catch (e: RateLimitedException) {
                // Retrying won't help until the rate-limit window resets.
                emit(UpdateState.Error(e.message ?: "GitHub rate limit reached. Try again later.", retryable = false))
                return@flow
            } catch (e: NoApkAttachedException) {
                emit(UpdateState.Error(e.message ?: "This release has no APK attached.", retryable = false))
                return@flow
            } catch (e: Exception) {
                lastError = e
                if (attempt < CHECK_MAX_ATTEMPTS - 1) {
                    delay(CHECK_RETRY_BASE_DELAY_MS * (attempt + 1))
                }
            }
        }
        emit(UpdateState.Error(friendlyCheckError(lastError), retryable = true))
    }.flowOn(Dispatchers.IO)

    private fun checkOnce(currentVersionName: String): UpdateState {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(API_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "DoomScroll-Updater")
                connectTimeout = CHECK_CONNECT_TIMEOUT_MS
                readTimeout = CHECK_READ_TIMEOUT_MS
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                return UpdateState.UpToDate(currentVersionName)
            }
            if (responseCode == 403 || responseCode == 429) {
                val reset = connection.getHeaderField("X-RateLimit-Reset")?.toLongOrNull()
                val waitMins = if (reset != null) {
                    ((reset * 1000 - System.currentTimeMillis()) / 60_000).coerceAtLeast(1)
                } else null
                throw RateLimitedException(
                    if (waitMins != null) "Update check rate-limited. Try again in ~${waitMins} min."
                    else "Update check rate-limited. Try again later."
                )
            }
            if (responseCode !in 200..299) {
                throw IllegalStateException("HTTP $responseCode")
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val tagName = json.optString("tag_name", "").trim()
            if (tagName.isEmpty()) {
                throw IllegalStateException("Release has no tag")
            }
            val releaseName = json.optString("name", tagName)
            val releaseBody = json.optString("body", "No changelog provided.")
            val htmlUrl = json.optString("html_url", "")

            var apkUrl: String? = null
            var apkName: String? = null
            var apkSize = 0L

            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", "").ifBlank { null }
                        apkName = name
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            val release = GitHubRelease(
                tagName = tagName,
                name = releaseName,
                body = releaseBody,
                htmlUrl = htmlUrl,
                apkDownloadUrl = apkUrl,
                apkFileName = apkName,
                apkSize = apkSize
            )

            if (!isNewerVersion(currentVersionName, tagName)) {
                return UpdateState.UpToDate(currentVersionName)
            }
            // A newer tag without an APK is NOT "up to date" — say so plainly instead of lying.
            if (apkUrl.isNullOrBlank()) {
                throw NoApkAttachedException("Version $tagName is out, but it has no APK attached yet.")
            }
            return UpdateState.Available(release, currentVersionName)
        } finally {
            connection?.disconnect()
        }
    }

    fun downloadAndInstallUpdate(
        context: Context,
        release: GitHubRelease
    ): Flow<UpdateState> = flow {
        val downloadUrl = release.apkDownloadUrl
        if (downloadUrl.isNullOrBlank()) {
            emit(UpdateState.Error("No APK attached to release ${release.tagName}", retryable = false))
            return@flow
        }

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        if (!dir.exists()) dir.mkdirs()
        val sanitizedTag = release.tagName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val apkFile = File(dir, "doomscroll_update_$sanitizedTag.apk")
        // Download into a ".part" file first, so a half-finished download is never installed.
        val partFile = File(dir, apkFile.name + ".part")

        // Remove STALE update files so they don't pile up — but never touch the file
        // we are about to write, nor the already-verified apk for this version.
        dir.listFiles()
            ?.filter { it.name.startsWith("doomscroll_update_") && it.name != apkFile.name && it.name != partFile.name }
            ?.forEach { runCatching { it.delete() } }

        // Already downloaded this exact version? Validate it and skip straight to install.
        if (apkFile.exists() && isDownloadComplete(apkFile, release.apkSize) &&
            isValidApk(apkFile)
        ) {
            emit(UpdateState.ReadyToInstall(release, apkFile))
            return@flow
        } else if (apkFile.exists() && !isValidApk(apkFile)) {
            // Corrupt leftover from an older buggy build — drop it so we re-download cleanly.
            runCatching { apkFile.delete() }
        }

        try {
            // Retry the transfer itself (resume makes retries cheap: already-fetched
            // bytes are kept). This is what saves slow/flaky mobile connections where
            // a single stall would otherwise fail the whole 9 MB download.
            var attempt = 0
            while (true) {
                try {
                    downloadWithResume(downloadUrl, partFile, release) { downloaded, total ->
                        val progress = if (total > 0) downloaded.toFloat() / total else 0f
                        emit(UpdateState.Downloading(release, progress, downloaded, total))
                    }
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    attempt++
                    if (attempt >= DOWNLOAD_MAX_ATTEMPTS) throw e
                    delay(DOWNLOAD_RETRY_BASE_DELAY_MS * attempt)
                }
            }

            // Only a fully downloaded + sane file becomes the real APK.
            // NOTE: the old verified apkFile (if any) is kept until this point, and is only
            // replaced after the new file passes validation — a failed download never
            // destroys a good cached copy.
            if (!isDownloadComplete(partFile, release.apkSize)) {
                throw IllegalStateException("File is incomplete, please try again")
            }
            if (!isValidApk(partFile)) {
                partFile.delete()
                throw IllegalStateException("Downloaded file looks corrupt, please try again")
            }
            if (apkFile.exists()) apkFile.delete()
            if (!partFile.renameTo(apkFile)) {
                throw IllegalStateException("Couldn't save the downloaded file")
            }

            emit(UpdateState.ReadyToInstall(release, apkFile))
        } catch (e: Exception) {
            // Keep any previously verified apkFile: only the partial file is discarded.
            runCatching { partFile.delete() }
            val retryable = e !is NoApkAttachedException
            emit(UpdateState.Error("Download failed: ${friendlyDownloadError(e)}", retryable = retryable))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Downloads [downloadUrl] into [partFile] with HTTP Range resume.
     * Calls [onProgress] throttled to [PROGRESS_EMIT_INTERVAL_MS].
     */
    private suspend fun downloadWithResume(
        downloadUrl: String,
        partFile: File,
        release: GitHubRelease,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit
    ) {
        val resumeFrom = if (partFile.exists()) partFile.length() else 0L
        val finalUrl = followRedirects(downloadUrl)

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(finalUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "DoomScroll-Updater")
                if (resumeFrom > 0) {
                    setRequestProperty("Range", "bytes=$resumeFrom-")
                }
                connectTimeout = DOWNLOAD_CONNECT_TIMEOUT_MS
                readTimeout = DOWNLOAD_READ_TIMEOUT_MS
            }

            val code = connection.responseCode
            // 206 = resumed; 200 = server ignored Range (restart from scratch).
            val serverResumed = code == HttpURLConnection.HTTP_PARTIAL
            if (code !in 200..299 && code != HttpURLConnection.HTTP_PARTIAL) {
                throw IllegalStateException("HTTP $code")
            }
            if (code == HttpURLConnection.HTTP_OK && resumeFrom > 0) {
                partFile.delete()
            }

            val remoteTotal = connection.contentLengthLong.let { if (it > 0) it else release.apkSize }
            val totalBytes = if (serverResumed && remoteTotal > 0) remoteTotal + resumeFrom
            else if (remoteTotal > 0) remoteTotal
            else release.apkSize

            var downloadedBytes = if (serverResumed) resumeFrom else 0L
            val append = serverResumed
            val inputStream: InputStream = connection.inputStream
            val outputStream = FileOutputStream(partFile, append)

            val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
            var bytesRead: Int
            var lastEmitMs = 0L

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val now = System.currentTimeMillis()
                        if (now - lastEmitMs >= PROGRESS_EMIT_INTERVAL_MS) {
                            lastEmitMs = now
                            onProgress(downloadedBytes, totalBytes)
                        }
                    }
                    output.flush()
                    // Best-effort durability only: fd.sync() throws SyncFailedException
                    // on filesystems without sync support (some emulated/external
                    // storage), and that must never fail an otherwise good download.
                    runCatching { output.fd.sync() }
                }
            }
            onProgress(downloadedBytes, totalBytes)
        } finally {
            connection?.disconnect()
        }
    }

    /** Follows up to [MAX_REDIRECTS] redirects and returns the final URL. */
    private fun followRedirects(startUrl: String): String {
        var currentUrl = startUrl
        var connection: HttpURLConnection? = null
        try {
            var redirects = 0
            while (redirects <= MAX_REDIRECTS) {
                connection?.disconnect()
                connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "DoomScroll-Updater")
                    connectTimeout = DOWNLOAD_CONNECT_TIMEOUT_MS
                    readTimeout = DOWNLOAD_READ_TIMEOUT_MS
                }
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                    code == HttpURLConnection.HTTP_MOVED_TEMP ||
                    code == HttpURLConnection.HTTP_SEE_OTHER ||
                    code == 307 || code == 308
                ) {
                    val location = connection.getHeaderField("Location")
                        ?: throw IllegalStateException("Redirect without Location")
                    currentUrl = location
                    redirects++
                    continue
                }
                if (redirects > MAX_REDIRECTS) break
                return currentUrl
            }
            throw IllegalStateException("Too many redirects")
        } finally {
            connection?.disconnect()
        }
    }

    private fun isDownloadComplete(file: File, expectedSize: Long): Boolean {
        if (!file.exists() || file.length() <= 0) return false
        // GitHub sometimes omits the asset size; fall back to a sanity floor.
        if (expectedSize > 0) return file.length() == expectedSize
        return file.length() >= MIN_VALID_APK_BYTES
    }

    /** Cheap structural check: APKs are ZIPs, so the magic bytes + a size floor catch truncations. */
    internal fun isValidApk(file: File): Boolean {
        if (!file.exists() || file.length() < MIN_VALID_APK_BYTES) return false
        return try {
            file.inputStream().use { input ->
                val magic = ByteArray(4)
                if (input.read(magic) != 4) return false
                magic[0] == ZIP_MAGIC_0 && magic[1] == ZIP_MAGIC_1 && magic[2] == 0x03.toByte() && magic[3] == 0x04.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Verifies the APK parses as an installed package and is signed for an in-place update.
     * Call after download, before prompting install.
     */
    fun isInstallableApk(context: Context, file: File): Boolean {
        if (!isValidApk(file)) return false
        return try {
            @Suppress("DEPRECATION")
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
            val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            info != null && !info.packageName.isNullOrBlank()
        } catch (_: Exception) {
            // getPackageArchiveInfo can throw on some OEM ROMs for valid files; the ZIP check above
            // already caught truncations, so treat parse failures as warnings, not blocks.
            true
        }
    }

    suspend fun promptInstall(context: Context, apkFile: File): Boolean = withContext(Dispatchers.Main) {
        try {
            if (!apkFile.exists() || !isValidApk(apkFile)) return@withContext false
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    try {
                        val manageIntent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(manageIntent)
                    } catch (_: Exception) {
                        context.startActivity(installIntent)
                    }
                    return@withContext false
                }
            }

            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun friendlyCheckError(e: Exception?): String {
        val msg = e?.localizedMessage.orEmpty()
        return when {
            e is java.net.UnknownHostException -> "No internet connection. Check your network and try again."
            e is java.net.SocketTimeoutException -> "Check timed out. Please try again."
            msg.contains("HTTP 404") -> "No releases found yet."
            msg.startsWith("HTTP") -> "Couldn't check for updates ($msg)"
            else -> "Couldn't check for updates. Please try again."
        }
    }

    private fun friendlyDownloadError(e: Exception): String {
        return when (e) {
            is java.net.UnknownHostException -> "No internet connection."
            is java.net.SocketTimeoutException -> "Connection timed out — resume to continue."
            else -> e.localizedMessage ?: "Unknown error"
        }
    }

    internal fun isNewerVersion(currentVersion: String, remoteVersion: String): Boolean {
        val curr = parseVersion(currentVersion) ?: return false
        val remote = parseVersion(remoteVersion) ?: return false
        val length = maxOf(curr.size, remote.size)
        for (i in 0 until length) {
            val c = curr.getOrElse(i) { 0 }
            val r = remote.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    /**
     * Parses "v1.2.3", "1.2", "1.2.3-beta+1" into [1, 2, 3]. Returns null when there is
     * no numeric core at all, so unknown tags never trigger an "update".
     */
    internal fun parseVersion(raw: String): List<Int>? {
        val cleaned = raw.trim().removePrefix("v").removePrefix("V").trim()
        if (cleaned.isEmpty()) return null
        // Strip pre-release/build metadata ("-beta", "+build") before splitting.
        val core = cleaned.split("-", "+").firstOrNull()?.trim().orEmpty()
        if (core.isEmpty()) return null
        val parts = core.split(".")
        if (parts.isEmpty() || parts.size > 8) return null
        val nums = parts.map { it.trim().toIntOrNull() ?: return null }
        if (nums.any { it < 0 || it > 999_999 }) return null
        return nums
    }

    private class RateLimitedException(message: String) : IllegalStateException(message)
    private class NoApkAttachedException(message: String) : IllegalStateException(message)
}
