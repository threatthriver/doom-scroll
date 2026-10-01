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
    val isLoading: Boolean = false,
    val isCheckingUpdates: Boolean = false,
    val updateStatusMessage: String? = null,
    val showUpdateDialog: Boolean = false,
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val updateVersion: String = "1.0",
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

    init {
        loadUserProfile()
        // Auto check updates in background
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
                        email = if (user.emailLower.isNotEmpty()) user.emailLower else it.email,
                        isLoading = false,
                    )
                }
            }.onFailure {
                _state.update { s -> s.copy(isLoading = false) }
            }
        }
    }

    fun checkForUpdates(silent: Boolean = false) {
        viewModelScope.launch {
            val currentVersion = BuildConfig.VERSION_NAME
            if (!silent) {
                _state.update { it.copy(isCheckingUpdates = true, updateStatusMessage = null) }
            }

            GitHubUpdateManager.checkForUpdate(currentVersion).collect { updateState ->
                when (updateState) {
                    is UpdateState.Checking -> {
                        if (!silent) _state.update { it.copy(isCheckingUpdates = true) }
                    }
                    is UpdateState.Available -> {
                        val sizeMb = if (updateState.release.apkSize > 0) {
                            String.format("%.1f MB", updateState.release.apkSize / (1024.0 * 1024.0))
                        } else {
                            ""
                        }
                        _state.update {
                            it.copy(
                                isCheckingUpdates = false,
                                showUpdateDialog = true,
                                availableRelease = updateState.release,
                                updateVersion = updateState.release.tagName,
                                updateNotes = updateState.release.body,
                                apkSizeMb = sizeMb,
                                updateStatusMessage = "UPDATE ${updateState.release.tagName} DISCOVERED",
                            )
                        }
                    }
                    is UpdateState.UpToDate -> {
                        _state.update {
                            it.copy(
                                isCheckingUpdates = false,
                                userNotification = if (!silent) "SYSTEM IS UP TO DATE // V$currentVersion" else null
                            )
                        }
                    }
                    is UpdateState.Error -> {
                        _state.update {
                            it.copy(
                                isCheckingUpdates = false,
                                userNotification = if (!silent) "UPDATE CHECK FAILED: ${updateState.message}" else null
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _state.update { it.copy(showUpdateDialog = false, isDownloadingUpdate = false, downloadProgress = 0f) }
    }

    fun startUpdateDownload(context: Context) {
        val release = _state.value.availableRelease ?: return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isDownloadingUpdate = true,
                    downloadProgress = 0f,
                    downloadedBytesText = "Preparing download..."
                )
            }

            GitHubUpdateManager.downloadAndInstallUpdate(context.applicationContext, release).collect { updateState ->
                when (updateState) {
                    is UpdateState.Downloading -> {
                        val downloadedMb = updateState.downloadedBytes / (1024f * 1024f)
                        val totalMb = updateState.totalBytes / (1024f * 1024f)
                        val text = if (totalMb > 0) {
                            String.format("%.1f MB / %.1f MB (%.0f%%)", downloadedMb, totalMb, updateState.progress * 100)
                        } else {
                            String.format("%.1f MB downloaded", downloadedMb)
                        }
                        _state.update {
                            it.copy(
                                downloadProgress = updateState.progress,
                                downloadedBytesText = text
                            )
                        }
                    }
                    is UpdateState.ReadyToInstall -> {
                        _state.update {
                            it.copy(
                                isDownloadingUpdate = false,
                                showUpdateDialog = false,
                                downloadedApkFile = updateState.apkFile,
                                userNotification = "UPDATE DOWNLOADED // LAUNCHING INSTALLER"
                            )
                        }
                        // Launch system package installer
                        GitHubUpdateManager.promptInstall(context, updateState.apkFile)
                    }
                    is UpdateState.Error -> {
                        _state.update {
                            it.copy(
                                isDownloadingUpdate = false,
                                userNotification = "DOWNLOAD FAILED: ${updateState.message}"
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
            GitHubUpdateManager.promptInstall(context, file)
        }
    }

    fun dismissNotification() {
        _state.update { it.copy(userNotification = null) }
    }

    fun signOut() {
        authRepo.signOut()
    }
}
