package com.securemessage.app.ui.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securemessage.app.BuildConfig
import com.securemessage.app.data.model.User
import com.securemessage.app.data.repo.AuthRepository
import com.securemessage.app.data.repo.UserRepository
import com.securemessage.app.data.update.GitHubRelease
import com.securemessage.app.data.update.GitHubUpdateManager
import com.securemessage.app.data.update.UpdateNotifier
import com.securemessage.app.data.update.UpdateState
import com.securemessage.app.data.update.cleanReleaseNotes
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ProfileUiState(
    val displayName: String = "",
    val username: String = "",
    val email: String = "",
    val uid: String = "",
    val bio: String = "",
    val photoUrl: String = "",
    val isLoading: Boolean = false,
    val isCheckingUpdates: Boolean = false,
    val updateStatusMessage: String? = null,
    val showUpdateDialog: Boolean = false,
    // True when a newer version exists. Drives the small "ready" hint in Settings,
    // so the app never has to interrupt the user with a pop-up on its own.
    val updateAvailable: Boolean = false,
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val updateVersion: String = "",
    val updateNotes: String = "",
    val apkSizeMb: String = "",
    val availableRelease: GitHubRelease? = null,
    val downloadedApkFile: File? = null,
    val isDownloadingUpdate: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedBytesText: String = "",
    val userNotification: String? = null,
)

class ProfileViewModel(
    private val authRepo: AuthRepository,
    private val userRepo: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    // Guards against overlapping update checks (startup check + user tapping "Check")
    private var isCheckRunning = false
    private var downloadJob: Job? = null

    init {
        loadUserProfile()
        // Quiet check on start: only marks "update available", never opens a pop-up.
        // Cool-down lives in checkForUpdates() so restarts don't hammer the GitHub API.
        checkForUpdates(silent = true)
    }

    fun loadUserProfile() {
        val uid = authRepo.currentUserId ?: return
        val currentEmail = authRepo.currentEmail ?: ""
        _state.update {
            it.copy(
                uid = uid,
                email = currentEmail,
                isLoading = true,
            )
        }

        viewModelScope.launch {
            userRepo.getUser(uid).onSuccess { user ->
                _state.update {
                    it.copy(
                        displayName = user.displayName.ifEmpty { it.displayName },
                        username = user.username,
                        // Email comes from Firebase Auth (owner-only), never from the
                        // public profile doc, so it is seeded above from currentEmail.
                        bio = user.bio,
                        photoUrl = user.photoUrl,
                        isLoading = false,
                    )
                }
            }.onFailure {
                _state.update { s -> s.copy(isLoading = false) }
            }
        }
    }

    fun updateProfile(displayName: String, bio: String) {
        val uid = authRepo.currentUserId ?: return
        // Same caps as sign-up so edits can't smuggle in what sign-up rejects.
        val name = displayName.trim()
        val cleanBio = bio.trim()
        val error = when {
            name.isEmpty() -> "Enter a name"
            name.length > MAX_DISPLAY_NAME -> "Name must be at most $MAX_DISPLAY_NAME characters"
            cleanBio.length > MAX_BIO -> "Bio must be at most $MAX_BIO characters"
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(userNotification = error) }
            return
        }
        _state.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            userRepo.updateProfile(uid, name, cleanBio, _state.value.photoUrl)
                .onSuccess {
                    _state.update {
                        it.copy(
                            displayName = name,
                            bio = cleanBio,
                            isLoading = false,
                            userNotification = "Profile saved",
                        )
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            userNotification = "Couldn't save your profile",
                        )
                    }
                }
        }
    }

    /**
     * [silent] = true  -> background check: no spinner, no pop-up, no error messages.
     * [silent] = false -> user tapped "Check": show spinner, open the dialog if there is
     *                     an update, and tell the user the result either way.
     *
     * Silent checks are rate-limited to once per [SILENT_CHECK_COOLDOWN_MS] (persisted
     * across restarts) so the app never hammers the GitHub API on every cold start.
     * Pass [appContext] when available so the cool-down timestamp can be persisted.
     */
    fun checkForUpdates(silent: Boolean = false, appContext: Context? = null) {
        if (isCheckRunning || _state.value.isDownloadingUpdate) return
        if (silent && appContext != null && !shouldSilentCheck(appContext)) return
        isCheckRunning = true

        viewModelScope.launch {
            val currentVersion = BuildConfig.VERSION_NAME
            if (!silent) {
                _state.update { it.copy(isCheckingUpdates = true, updateStatusMessage = null) }
            }

            try {
                GitHubUpdateManager.checkForUpdate(currentVersion).collect { updateState ->
                    when (updateState) {
                        is UpdateState.Available -> {
                            appContext?.let { markSilentChecked(it) }
                            val release = updateState.release
                            val sizeMb = if (release.apkSize > 0) {
                                String.format("%.1f MB", release.apkSize / (1024.0 * 1024.0))
                            } else {
                                ""
                            }
                            // If this version was already downloaded by an earlier run,
                            // surface the cached file so the button reads "Install".
                            val cached = appContext?.let { ctx ->
                                findCachedApk(ctx, release.tagName, release.apkSize)
                            }
                            _state.update {
                                it.copy(
                                    isCheckingUpdates = false,
                                    updateAvailable = true,
                                    showUpdateDialog = !silent,
                                    availableRelease = release,
                                    updateVersion = release.tagName.removePrefix("v").removePrefix("V"),
                                    updateNotes = cleanReleaseNotes(release.body),
                                    apkSizeMb = sizeMb,
                                    downloadedApkFile = cached ?: it.downloadedApkFile,
                                )
                            }
                            // Silent finds feed the tray (manual ones already show a dialog).
                            if (silent) {
                                appContext?.let { ctx ->
                                    if (UpdateNotifier.shouldNotifyFor(ctx, release.tagName)) {
                                        UpdateNotifier.showUpdateAvailable(ctx, release)
                                        UpdateNotifier.markNotifiedFor(ctx, release.tagName)
                                    }
                                }
                            }
                        }

                        is UpdateState.UpToDate -> {
                            appContext?.let { markSilentChecked(it) }
                            appContext?.let { UpdateNotifier.cancel(it) }
                            _state.update {
                                it.copy(
                                    isCheckingUpdates = false,
                                    updateAvailable = false,
                                    userNotification = if (!silent) {
                                        "You're up to date (version $currentVersion)"
                                    } else {
                                        it.userNotification
                                    },
                                )
                            }
                        }

                        is UpdateState.Error -> {
                            appContext?.let { markSilentChecked(it) }
                            _state.update {
                                it.copy(
                                    isCheckingUpdates = false,
                                    // Surface the manager's specific message on manual checks
                                    // (rate-limit, no-APK, no-network) instead of a generic one.
                                    userNotification = if (!silent) {
                                        updateState.message
                                    } else {
                                        it.userNotification
                                    },
                                )
                            }
                        }

                        else -> Unit
                    }
                }
            } finally {
                isCheckRunning = false
                _state.update { it.copy(isCheckingUpdates = false) }
            }
        }
    }

    /** Opens the update dialog for an update that was already found by the quiet check. */
    fun openUpdateDialog() {
        if (_state.value.availableRelease != null) {
            _state.update { it.copy(showUpdateDialog = true) }
        } else {
            checkForUpdates(silent = false)
        }
    }

    fun dismissUpdateDialog() {
        // Keep the download running if one is in progress; only hide the dialog.
        _state.update { it.copy(showUpdateDialog = false) }
    }

    fun startUpdateDownload(context: Context) {
        val release = _state.value.availableRelease ?: return
        if (_state.value.isDownloadingUpdate) return
        downloadJob?.cancel()

        downloadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isDownloadingUpdate = true,
                    downloadProgress = 0f,
                    downloadedBytesText = "Starting download...",
                )
            }

            GitHubUpdateManager.downloadAndInstallUpdate(context.applicationContext, release).collect { updateState ->
                when (updateState) {
                    is UpdateState.Downloading -> {
                        val downloadedMb = updateState.downloadedBytes / (1024f * 1024f)
                        val totalMb = updateState.totalBytes / (1024f * 1024f)
                        val text = if (totalMb > 0) {
                            String.format("%.1f of %.1f MB", downloadedMb, totalMb)
                        } else {
                            String.format("%.1f MB downloaded", downloadedMb)
                        }
                        _state.update {
                            it.copy(
                                downloadProgress = updateState.progress.coerceIn(0f, 1f),
                                downloadedBytesText = text,
                            )
                        }
                    }

                    is UpdateState.ReadyToInstall -> {
                        _state.update {
                            it.copy(
                                isDownloadingUpdate = false,
                                showUpdateDialog = false,
                                downloadedApkFile = updateState.apkFile,
                                userNotification = "Update downloaded. Opening the installer...",
                            )
                        }
                        UpdateNotifier.showDownloadedReady(context.applicationContext, release)
                        val opened = GitHubUpdateManager.promptInstall(context, updateState.apkFile)
                        if (!opened) {
                            // Android first asks the user to allow installs from this app.
                            _state.update {
                                it.copy(
                                    userNotification = if (!updateState.apkFile.exists()) {
                                        "Downloaded file is missing. Please download again."
                                    } else {
                                        "Allow installs from this app, then come back and tap Install."
                                    },
                                )
                            }
                        }
                    }

                    is UpdateState.Error -> {
                        _state.update {
                            it.copy(
                                isDownloadingUpdate = false,
                                userNotification = updateState.message,
                            )
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    fun installDownloadedApk(context: Context) {
        val file = _state.value.downloadedApkFile
        // The file may have been cleared by the system or an older failed download.
        if (file == null || !file.exists() || !GitHubUpdateManager.isValidApk(file)) {
            _state.update {
                it.copy(
                    downloadedApkFile = null,
                    userNotification = "Downloaded file is missing or corrupt. Downloading again...",
                )
            }
            startUpdateDownload(context)
            return
        }
        viewModelScope.launch {
            val opened = GitHubUpdateManager.promptInstall(context, file)
            if (!opened) {
                _state.update {
                    it.copy(userNotification = "Allow installs from this app, then tap Install again.")
                }
            }
        }
    }

    /** Re-checks the cached APK (e.g. when Settings becomes visible after the installer ran). */
    fun refreshCachedApk(context: Context) {
        val release = _state.value.availableRelease ?: return
        val cached = findCachedApk(context.applicationContext, release.tagName, release.apkSize)
        if (cached == null && _state.value.downloadedApkFile != null) {
            _state.update { it.copy(downloadedApkFile = null) }
        } else if (cached != null && cached.absolutePath != _state.value.downloadedApkFile?.absolutePath) {
            _state.update { it.copy(downloadedApkFile = cached) }
        }
    }

    private fun findCachedApk(context: Context, tag: String, expectedSize: Long): File? {
        return try {
            val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                ?: context.cacheDir
            val sanitized = tag.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val file = File(dir, "doomscroll_update_$sanitized.apk")
            if (!file.exists()) return null
            if (expectedSize > 0 && file.length() != expectedSize) return null
            if (!GitHubUpdateManager.isValidApk(file)) return null
            file
        } catch (_: Exception) {
            null
        }
    }

    private fun shouldSilentCheck(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_UPDATES, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_SILENT_CHECK, 0L)
        return System.currentTimeMillis() - last >= SILENT_CHECK_COOLDOWN_MS
    }

    private fun markSilentChecked(context: Context) {
        try {
            context.getSharedPreferences(PREFS_UPDATES, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_SILENT_CHECK, System.currentTimeMillis()).apply()
        } catch (_: Exception) {
            // Prefs failure must never break the update flow.
        }
    }

    fun dismissNotification() {
        _state.update { it.copy(userNotification = null) }
    }

    fun signOut() {
        downloadJob?.cancel()
        authRepo.signOut()
    }

    companion object {
        private const val PREFS_UPDATES = "doomscroll_updates"
        private const val KEY_LAST_SILENT_CHECK = "last_silent_check_ms"
        /** Silent auto-checks hit the network at most once per 6h; manual taps always check. */
        private const val SILENT_CHECK_COOLDOWN_MS = 6 * 60 * 60 * 1000L
        /** Mirrors sign-up caps so edits can't smuggle in what sign-up rejects. */
        const val MAX_DISPLAY_NAME = 50
        const val MAX_BIO = 160
    }
}
