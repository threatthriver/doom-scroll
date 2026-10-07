package com.securemessage.app.data.notify

import android.Manifest
import android.app.Notification
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
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.securemessage.app.MainActivity

/**
 * System-tray notifications for incoming chat messages.
 *
 * Two senders post these: the in-process Firestore watcher (while the app process is alive)
 * and the FCM MessagingService (when a push from the Cloud Function arrives). Both use the
 * same channel and per-chat id. All calls no-op safely when the notification permission is
 * missing.
 */
object MessageNotifier {
    const val EXTRA_OPEN_CHAT = "open_chat_id"
    const val CHANNEL_ID = "chat_messages"

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

    /**
     * True only if a message alert would actually be shown: app notifications are allowed AND the
     * user hasn't switched off just the "New messages" channel in system settings.
     */
    fun messageAlertsEnabled(context: Context): Boolean {
        if (!canNotify(context)) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return true
        val channel = manager.getNotificationChannel(CHANNEL_ID) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
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

    const val ACTION_REPLY = "com.securemessage.app.action.REPLY"
    const val ACTION_MARK_READ = "com.securemessage.app.action.MARK_READ"
    const val EXTRA_CHAT_ID = "chat_id"
    const val KEY_REPLY = "reply_text"

    private fun activeNotification(context: Context, chatId: String): Notification? = runCatching {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.activeNotifications.firstOrNull { it.id == notificationIdFor(chatId) }?.notification
    }.getOrNull()

    private fun existingStyle(context: Context, chatId: String): NotificationCompat.MessagingStyle? =
        activeNotification(context, chatId)?.let {
            runCatching { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }.getOrNull()
        }

    private fun actionIntent(context: Context, chatId: String, action: String, requestCode: Int, mutable: Boolean): PendingIntent {
        val intent = Intent(context, ReplyReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_CHAT_ID, chatId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE
            else if (!mutable) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private fun post(
        context: Context,
        chatId: String,
        title: String,
        style: NotificationCompat.MessagingStyle,
        alertOnce: Boolean,
        showText: Boolean,
    ) {
        val id = notificationIdFor(chatId)
        val replyInput = RemoteInput.Builder(KEY_REPLY).setLabel("Reply").build()
        val reply = NotificationCompat.Action.Builder(
            com.securemessage.app.R.drawable.ic_notification,
            "Reply",
            actionIntent(context, chatId, ACTION_REPLY, id * 2, mutable = true),
        )
            .addRemoteInput(replyInput)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(true)
            .build()
        val markRead = NotificationCompat.Action.Builder(
            com.securemessage.app.R.drawable.ic_notification,
            "Mark as read",
            actionIntent(context, chatId, ACTION_MARK_READ, id * 2 + 1, mutable = false),
        ).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ).setShowsUserInterface(false).build()

        // What the lock screen shows when the phone is locked: never the text.
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.securemessage.app.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(com.securemessage.app.R.string.new_message_from, title))
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.securemessage.app.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(
                if (showText) style.messages.lastOrNull()?.text
                else context.getString(com.securemessage.app.R.string.new_message_from, title),
            )
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(chatPendingIntent(context, chatId))
            .addAction(reply)
            .addAction(markRead)
            .setAutoCancel(true)
            .setOnlyAlertOnce(alertOnce)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the canNotify() check and now: nothing to show.
        }
    }

    /**
     * Posts the "new message" alert. [text] is the message already decrypted on this phone (null
     * when it couldn't be, or the user turned previews off): then the alert only says who wrote.
     * A push and the in-process watcher use the same per-chat id, so they replace each other.
     */
    fun showMessage(context: Context, chatId: String, senderName: String, text: String? = null) {
        ensureChannel(context)
        if (!canNotify(context)) return
        val me = Person.Builder().setName("You").build()
        val sender = Person.Builder().setName(senderName).build()
        val style = existingStyle(context, chatId) ?: NotificationCompat.MessagingStyle(me)
        val body = text?.takeIf { it.isNotBlank() }
            ?: context.getString(com.securemessage.app.R.string.new_message_from, senderName)
        style.addMessage(body, System.currentTimeMillis(), sender)
        post(context, chatId, senderName, style, alertOnce = false, showText = text != null)
    }

    /** After a reply from the notification: show it in the thread and stop the progress spinner. */
    fun appendOwnReply(context: Context, chatId: String) = appendOwnReply(context, chatId, null)

    fun appendOwnReply(context: Context, chatId: String, text: String?) {
        val existing = activeNotification(context, chatId) ?: return
        val style = existingStyle(context, chatId) ?: return
        val title = existing.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        if (text != null) style.addMessage(text, System.currentTimeMillis(), null as Person?)
        post(context, chatId, title, style, alertOnce = true, showText = true)
    }

    /** Replace the spinner with a note the reply didn't go out (tap the chat to retry). */
    fun replyFailed(context: Context, chatId: String) {
        val existing = activeNotification(context, chatId) ?: return
        val style = existingStyle(context, chatId) ?: return
        val title = existing.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        style.addMessage("Couldn't send your reply. Open the chat to try again.", System.currentTimeMillis(),
            Person.Builder().setName("Hush").build())
        post(context, chatId, title, style, alertOnce = true, showText = true)
    }

    fun cancelFor(context: Context, chatId: String) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(notificationIdFor(chatId))
        }
    }
}
