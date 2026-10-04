package com.securemessage.app.data.notify

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
 * System-tray notifications for incoming chat messages.
 *
 * Works while the app process is alive (foreground or background): an app-scoped
 * Firestore watcher posts these. When the process is dead (force-stop), nothing can
 * wake the app without FCM — that is a deliberate future step, not covered here.
 * All calls no-op safely when the notification permission is missing.
 */
object MessageNotifier {
    const val EXTRA_OPEN_CHAT = "open_chat_id"
    private const val CHANNEL_ID = "chat_messages"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "New messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when a new chat message arrives"
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

    /** Stable per-chat id so rapid messages update one notification instead of stacking. */
    internal fun notificationIdFor(chatId: String): Int =
        2000 + (chatId.hashCode() and Int.MAX_VALUE) % 50_000

    private fun chatPendingIntent(context: Context, chatId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_CHAT, chatId)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            notificationIdFor(chatId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showMessage(context: Context, chatId: String, senderName: String, text: String) {
        ensureChannel(context)
        if (!canNotify(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(senderName)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(chatPendingIntent(context, chatId))
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationIdFor(chatId), notification)
        }
    }

    fun cancelFor(context: Context, chatId: String) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(notificationIdFor(chatId))
        }
    }
}
