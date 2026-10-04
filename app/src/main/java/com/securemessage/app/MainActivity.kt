package com.securemessage.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.securemessage.app.data.notify.MessageNotifier
import com.securemessage.app.data.update.GitHubUpdateManager
import com.securemessage.app.data.update.UpdateNotifier
import com.securemessage.app.data.update.UpdateNotifier.EXTRA_OPEN_UPDATES
import com.securemessage.app.data.update.UpdateState
import com.securemessage.app.ui.nav.AppNavHost
import com.securemessage.app.ui.theme.SecureMessageTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

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
        setContent {
            SecureMessageTheme {
                AppNavHost(
                    container,
                    openUpdates = openUpdatesRequested,
                    openChatId = openChatRequested
                )
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
     * App-start update check that feeds the system tray. Runs once per process start
     * (the 6h in-app cool-down lives in ProfileViewModel for the Settings card).
     * Never interrupts the user: it only posts a notification, never a dialog.
     */
    private fun checkUpdatesForTray() {
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
