package com.securemessage.app.data.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.securemessage.app.MainActivity

/**
 * System-tray notifications for app updates.
 *
 * The in-app Settings card is the primary surface; this is the backup that reaches
 * the user when the app is in the background. All calls are safe when the
 * POST_NOTIFICATIONS permission is missing — they silently no-op instead of crashing.
 */
object UpdateNotifier {
    const val EXTRA_OPEN_UPDATES = "open_updates"
    private const val CHANNEL_ID = "doomscroll_updates"
    private const val NOTIFICATION_ID_UPDATE = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "App updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifies when a new WEAVE version is ready to install"
        }
        manager.createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Tapping the notification opens the app straight on the Settings updates card. */
    private fun updatesPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_UPDATES, true)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showUpdateAvailable(context: Context, release: GitHubRelease) {
        ensureChannel(context)
        if (!canNotify(context)) return
        val version = release.tagName.removePrefix("v").removePrefix("V")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("WEAVE $version is ready")
            .setContentText("Tap to open updates and install the new version.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Version $version is out. Tap to open updates and install it.")
            )
            .setContentIntent(updatesPendingIntent(context))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_UPDATE, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the canNotify() check and now: nothing to show.
        }
    }

    fun showDownloadedReady(context: Context, release: GitHubRelease) {
        ensureChannel(context)
        if (!canNotify(context)) return
        val version = release.tagName.removePrefix("v").removePrefix("V")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Update downloaded")
            .setContentText("Version $version is ready. Tap to install it.")
            .setContentIntent(updatesPendingIntent(context))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_UPDATE, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the canNotify() check and now: nothing to show.
        }
    }

    fun cancel(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_UPDATE)
        }
    }

    // --- Per-version de-duplication --------------------------------------------------
    // The app-start check runs on every cold start; without this the user would get
    // re-alerted for the same version daily until they update.
    private const val PREFS_UPDATES = "doomscroll_updates"
    private const val KEY_NOTIFIED_TAG = "notified_update_tag"

    fun shouldNotifyFor(context: Context, tag: String): Boolean {
        return try {
            context.getSharedPreferences(PREFS_UPDATES, Context.MODE_PRIVATE)
                .getString(KEY_NOTIFIED_TAG, null) != tag
        } catch (_: Exception) {
            true
        }
    }

    fun markNotifiedFor(context: Context, tag: String) {
        try {
            context.getSharedPreferences(PREFS_UPDATES, Context.MODE_PRIVATE)
                .edit().putString(KEY_NOTIFIED_TAG, tag).apply()
        } catch (_: Exception) {
            // Prefs failure must never break the update flow.
        }
    }
}
