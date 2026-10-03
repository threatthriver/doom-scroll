package com.securemessage.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
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

    fun checkForUpdate(currentVersionName: String): Flow<UpdateState> = flow {
        emit(UpdateState.Checking)
        try {
            val url = URL(API_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "DoomScroll-Updater")
                connectTimeout = 10000
                readTimeout = 10000
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                emit(UpdateState.UpToDate(currentVersionName))
                return@flow
            }
            if (responseCode !in 200..299) {
                emit(UpdateState.Error("Couldn't check for updates (error $responseCode)"))
                return@flow
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val tagName = json.optString("tag_name", "").trim()
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
                        apkUrl = asset.optString("browser_download_url", "")
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

            if (isNewerVersion(currentVersionName, tagName) && apkUrl != null) {
                emit(UpdateState.Available(release, currentVersionName))
            } else {
                emit(UpdateState.UpToDate(currentVersionName))
            }
        } catch (e: Exception) {
            emit(UpdateState.Error(e.localizedMessage ?: "Unknown update error"))
        }
    }.flowOn(Dispatchers.IO)

    fun downloadAndInstallUpdate(
        context: Context,
        release: GitHubRelease
    ): Flow<UpdateState> = flow {
        val downloadUrl = release.apkDownloadUrl
        if (downloadUrl.isNullOrBlank()) {
            emit(UpdateState.Error("No APK attached to release ${release.tagName}"))
            return@flow
        }

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        if (!dir.exists()) dir.mkdirs()
        val sanitizedTag = release.tagName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val apkFile = File(dir, "doomscroll_update_$sanitizedTag.apk")
        // Download into a ".part" file first, so a half-finished download is never installed.
        val partFile = File(dir, apkFile.name + ".part")

        // Remove old update files so they don't pile up on the phone.
        dir.listFiles()
            ?.filter { it.name.startsWith("doomscroll_update_") && it.name != apkFile.name }
            ?.forEach { it.delete() }

        // Already downloaded this exact version? Skip the download and go straight to install.
        if (apkFile.exists() && release.apkSize > 0 && apkFile.length() == release.apkSize) {
            emit(UpdateState.ReadyToInstall(release, apkFile))
            return@flow
        }

        try {
            var currentUrl = downloadUrl
            var connection: HttpURLConnection? = null
            var redirects = 0
            val maxRedirects = 6

            // Follow HTTP redirects safely (GitHub asset downloads redirect to AWS S3/Azure blobs)
            while (redirects < maxRedirects) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "DoomScroll-Updater")
                    connectTimeout = 15000
                    readTimeout = 30000
                }
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                    code == HttpURLConnection.HTTP_MOVED_TEMP ||
                    code == HttpURLConnection.HTTP_SEE_OTHER ||
                    code == 307 || code == 308
                ) {
                    val location = connection.getHeaderField("Location")
                    if (location != null) {
                        currentUrl = location
                        redirects++
                        connection.disconnect()
                        continue
                    }
                }
                break
            }

            val conn = connection ?: throw IllegalStateException("Could not open download connection")
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode}")
            }
            val totalBytes = conn.contentLengthLong.let { if (it > 0) it else release.apkSize }

            var downloadedBytes = 0L
            val inputStream: InputStream = conn.inputStream
            val outputStream = FileOutputStream(partFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var lastEmitMs = 0L

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val now = System.currentTimeMillis()
                        if (now - lastEmitMs >= 100) {
                            lastEmitMs = now
                            val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                            emit(UpdateState.Downloading(release, progress, downloadedBytes, totalBytes))
                        }
                    }
                    output.flush()
                }
            }

            // Only a fully downloaded file becomes the real APK.
            if (release.apkSize > 0 && partFile.length() != release.apkSize) {
                throw IllegalStateException("File is incomplete, please try again")
            }
            if (apkFile.exists()) apkFile.delete()
            if (!partFile.renameTo(apkFile)) {
                throw IllegalStateException("Couldn't save the downloaded file")
            }

            emit(UpdateState.ReadyToInstall(release, apkFile))
        } catch (e: Exception) {
            partFile.delete()
            apkFile.delete()
            emit(UpdateState.Error("Download failed: ${e.localizedMessage}"))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun promptInstall(context: Context, apkFile: File): Boolean = withContext(Dispatchers.Main) {
        try {
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

    internal fun isNewerVersion(currentVersion: String, remoteVersion: String): Boolean {
        val currParts = currentVersion.trim().removePrefix("v").removePrefix("V").split(".").mapNotNull { it.trim().toIntOrNull() }
        val remoteParts = remoteVersion.trim().removePrefix("v").removePrefix("V").split(".").mapNotNull { it.trim().toIntOrNull() }

        if (currParts.isNotEmpty() && remoteParts.isNotEmpty()) {
            val length = maxOf(currParts.size, remoteParts.size)
            for (i in 0 until length) {
                val curr = currParts.getOrElse(i) { 0 }
                val remote = remoteParts.getOrElse(i) { 0 }
                if (remote > curr) return true
                if (remote < curr) return false
            }
            return false
        }
        return remoteVersion.trim().removePrefix("v") != currentVersion.trim().removePrefix("v") && remoteVersion.isNotBlank()
    }
}
