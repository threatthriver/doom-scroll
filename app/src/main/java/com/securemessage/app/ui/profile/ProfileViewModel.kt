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
import com.securemessage.app.data.update.UpdateState
import com.securemessage.app.data.update.cleanReleaseNotes
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

    init {
        loadUserProfile()
        // Quiet check on start: only marks "update available", never opens a pop-up.
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
        _state.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            userRepo.updateProfile(uid, displayName, bio, _state.value.photoUrl)
                .onSuccess {
                    _state.update {
                        it.copy(
                            displayName = displayName,
                            bio = bio,
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
     */
    fun checkForUpdates(silent: Boolean = false) {
        if (isCheckRunning || _state.value.isDownloadingUpdate) return
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
                            val release = updateState.release
                            val sizeMb = if (release.apkSize > 0) {
                                String.format("%.1f MB", release.apkSize / (1024.0 * 1024.0))
                            } else {
                                ""
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
                                )
                            }
                        }

                        is UpdateState.UpToDate -> {
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
                            _state.update {
                                it.copy(
                                    isCheckingUpdates = false,
                                    userNotification = if (!silent) {
                                        "Couldn't check for updates. Please try again."
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

        viewModelScope.launch {
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
                                downloadProgress = updateState.progress,
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
                        val opened = GitHubUpdateManager.promptInstall(context, updateState.apkFile)
                        if (!opened) {
                            // Android first asks the user to allow installs from this app.
                            _state.update {
                                it.copy(
                                    userNotification = "Allow installs from this app, then come back and tap Install.",
                                )
                            }
                        }
                    }

                    is UpdateState.Error -> {
                        _state.update {
                            it.copy(
                                isDownloadingUpdate = false,
                                userNotification = "Download failed. Please try again.",
                            )
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    fun installDownloadedApk(context: Context) {
        val file = _state.value.downloadedApkFile ?: return
        viewModelScope.launch {
            val opened = GitHubUpdateManager.promptInstall(context, file)
            if (!opened) {
                _state.update {
                    it.copy(userNotification = "Allow installs from this app, then tap Install again.")
                }
            }
        }
    }

    fun dismissNotification() {
        _state.update { it.copy(userNotification = null) }
    }

    fun signOut() {
        authRepo.signOut()
    }
}
