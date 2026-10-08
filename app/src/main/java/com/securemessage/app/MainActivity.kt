package com.securemessage.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.securemessage.app.data.PrivacySettings
import com.securemessage.app.data.notify.MessageNotifier
import com.securemessage.app.data.update.GitHubUpdateManager
import com.securemessage.app.data.update.UpdateNotifier
import com.securemessage.app.data.update.UpdateNotifier.EXTRA_OPEN_UPDATES
import com.securemessage.app.data.update.UpdateState
import com.securemessage.app.ui.nav.AppNavHost
import com.securemessage.app.ui.theme.SecureMessageTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    /** True while the app lock is covering the UI. */
    private var locked by mutableStateOf(false)
    private var promptShowing = false

    /** True when the activity was opened from the update notification (cold start or tap). */
    private var openUpdatesRequested by mutableStateOf(false)

    /** Chat to open when launched from a message notification (cold start or tap). */
    private var openChatRequested by mutableStateOf<String?>(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Result is intentionally ignored: if granted, the next update check notifies;
            // if denied, the in-app Settings card remains the update surface.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openUpdatesRequested = intent?.getBooleanExtra(EXTRA_OPEN_UPDATES, false) == true
        openChatRequested = intent?.getStringExtra(MessageNotifier.EXTRA_OPEN_CHAT)
        maybeRequestNotificationPermission()
        checkUpdatesForTray()
        val container = (application as SecureMessageApp).container
        locked = PrivacySettings.isAppLockEnabled(this) && canAuthenticate()
        setContent {
            SecureMessageTheme {
                Box(Modifier.fillMaxSize()) {
                    AppNavHost(
                        container,
                        openUpdates = openUpdatesRequested,
                        openChatId = openChatRequested
                    )
                    if (locked) {
                        // Opaque cover: nothing of the chats is visible until you unlock.
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = "Unlock WEAVE",
                                ) { showUnlockPrompt() }
                                .semantics { this.contentDescription = "WEAVE is locked. Double tap to unlock." },
                            contentAlignment = Alignment.Center,
                        ) { Text("WEAVE is locked. Tap to unlock.", color = Color.White) }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleTop: tapping a notification while the app is open re-routes in place.
        if (intent.getBooleanExtra(EXTRA_OPEN_UPDATES, false)) {
            UpdateNotifier.cancel(this)
            openUpdatesRequested = true
        }
        intent.getStringExtra(MessageNotifier.EXTRA_OPEN_CHAT)?.let { chatId ->
            openChatRequested = chatId
        }
    }

    override fun onStop() {
        super.onStop()
        if (PrivacySettings.isAppLockEnabled(this) && canAuthenticate()) locked = true
    }

    override fun onResume() {
        super.onResume()
        applyScreenSecurity()
        if (locked) showUnlockPrompt()
    }

    private val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** False when the phone has no fingerprint/face/screen lock set up: the lock can't work then. */
    fun canAuthenticate(): Boolean =
        BiometricManager.from(this).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    private fun showUnlockPrompt() {
        if (promptShowing || !canAuthenticate()) {
            if (!canAuthenticate()) locked = false
            return
        }
        promptShowing = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promptShowing = false
                    locked = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Stay locked; tapping the cover tries again.
                    promptShowing = false
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock WEAVE")
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }

    /** Re-read on every resume so toggling it in Settings takes effect when coming back. */
    fun applyScreenSecurity() {
        if (PrivacySettings.isScreenSecurityEnabled(this)) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        // Ask once per install; the system remembers a denial and won't re-prompt anyway.
        if (getPreferences(MODE_PRIVATE).getBoolean(PREF_ASKED_NOTIFICATIONS, false)) return
        getPreferences(MODE_PRIVATE).edit().putBoolean(PREF_ASKED_NOTIFICATIONS, true).apply()
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * App-start update check that feeds the system tray. Shares the same persisted 6h cool-down as
     * the Settings card (ProfileViewModel), so a cold start no longer makes an unconditional GitHub
     * API call or races the in-app check to post/cancel the tray notification. Never interrupts the
     * user: it only posts a notification, never a dialog.
     */
    private fun checkUpdatesForTray() {
        if (!com.securemessage.app.ui.profile.ProfileViewModel.shouldSilentCheck(this)) return
        com.securemessage.app.ui.profile.ProfileViewModel.markSilentChecked(this)
        lifecycleScope.launch {
            GitHubUpdateManager.checkForUpdate(BuildConfig.VERSION_NAME).collect { state ->
                when (state) {
                    is UpdateState.Available -> {
                        if (UpdateNotifier.shouldNotifyFor(this@MainActivity, state.release.tagName)) {
                            UpdateNotifier.showUpdateAvailable(this@MainActivity, state.release)
                            UpdateNotifier.markNotifiedFor(this@MainActivity, state.release.tagName)
                        }
                    }
                    is UpdateState.UpToDate -> {
                        // Stale notification (e.g. user updated via another route) — clear it.
                        UpdateNotifier.cancel(this@MainActivity)
                    }
                    else -> Unit
                }
            }
        }
    }

    companion object {
        private const val PREF_ASKED_NOTIFICATIONS = "asked_post_notifications"
    }
}
