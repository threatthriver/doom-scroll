package com.securemessage.app.data.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.securemessage.app.SecureMessageApp
import com.securemessage.app.data.PrivacySettings
import com.securemessage.app.data.crypto.E2EEncryption
import com.securemessage.app.di.AppContainer
import com.securemessage.app.ui.chat.MAX_MESSAGE_LENGTH
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Handles the "Reply" and "Mark as read" buttons on a message notification. */
class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getStringExtra(MessageNotifier.EXTRA_CHAT_ID) ?: return
        val app = context.applicationContext as SecureMessageApp
        val container = app.container
        val pending = goAsync()
        container.appScope.launch {
            try {
                when (intent.action) {
                    MessageNotifier.ACTION_REPLY -> {
                        val text = RemoteInput.getResultsFromIntent(intent)
                            ?.getCharSequence(MessageNotifier.KEY_REPLY)?.toString()?.trim().orEmpty()
                        if (text.isEmpty()) return@launch
                        val sent = sendReply(container, chatId, text)
                        if (sent) MessageNotifier.appendOwnReply(app, chatId, text)
                        else MessageNotifier.replyFailed(app, chatId)
                    }
                    MessageNotifier.ACTION_MARK_READ -> {
                        container.authRepository.currentUserId?.let { container.chatRepository.markChatRead(chatId, it) }
                        MessageNotifier.cancelFor(app, chatId)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun sendReply(c: AppContainer, chatId: String, text: String): Boolean {
        if (text.length > MAX_MESSAGE_LENGTH) return false
        val me = c.authRepository.currentUserId ?: return false
        val chat = c.chatRepository.getChat(chatId).getOrNull() ?: return false
        val other = chat.participants.firstOrNull { it != me } ?: return false
        // Never send plaintext: without a key the reply is refused, not sent unencrypted.
        val channel = c.chatSessionManager.openCachedChannel(chatId, other)
            ?: withTimeoutOrNull(6_000L) { c.chatSessionManager.openChannel(chatId, other) }
            ?: return false
        val body = runCatching { E2EEncryption.encrypt(text, channel.key) }.getOrNull() ?: return false
        // No answer in time usually means offline: Firestore keeps the write and sends it later.
        val result = withTimeoutOrNull(8_000L) {
            c.chatRepository.sendMessage(chatId, me, body, recipientIds = listOf(other))
        }
        if (result != null && result.isFailure) return false
        c.chatRepository.markChatRead(chatId, me)
        return true
    }
}

/** Reads the newest message of a chat and decrypts it with the key already on this phone. */
object NotificationText {
    suspend fun latest(c: AppContainer, context: Context, chatId: String): String? {
        if (!PrivacySettings.isNotificationPreviewEnabled(context)) return null
        val me = c.authRepository.currentUserId ?: return null
        return runCatching {
            val chat = c.chatRepository.getChatFresh(chatId).getOrNull() ?: return null
            if (chat.lastSenderId == me || chat.lastMessage.isEmpty()) return null
            val other = chat.participants.firstOrNull { it != me } ?: return null
            // Cached key only: a notification must not wait for (or trigger) a key lookup.
            val key = c.chatSessionManager.openCachedChannel(chatId, other)?.key ?: return null
            E2EEncryption.decrypt(chat.lastMessage, key).take(500)
        }.getOrNull()
    }
}
