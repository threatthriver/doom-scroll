package com.securemessage.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.securemessage.app.SecureMessageApp
import com.securemessage.app.data.notify.MessageNotifier
import com.securemessage.app.data.notify.NotificationText
import com.securemessage.app.ui.chat.OpenChatTracker
import kotlinx.coroutines.launch

/**
 * Receives data-only pushes while the app is in the background or closed. The payload names the
 * sender and chat but never carries message text (messages are end-to-end encrypted).
 */
class MessagingService : FirebaseMessagingService() {
    private val container get() = (application as SecureMessageApp).container

    override fun onNewToken(token: String) {
        container.appScope.launch { container.pushTokenRepository.register(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val payload = NotificationPayload.parse(message.data) ?: return
        val me = container.authRepository.currentUserId
        if (!NotificationPayload.shouldShow(payload, me, OpenChatTracker.openChatId.value)) return
        // Same notifier, channel and per-chat id as the in-process watcher, so they never double up.
        // The push carries no text. Read the newest message and decrypt it here, with a short time
        // limit so a slow network still gives the plain "new message" alert instead of nothing.
        val text = kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeoutOrNull(7_000L) {
                NotificationText.latest(container, applicationContext, payload.chatId)
            }
        }
        MessageNotifier.showMessage(applicationContext, payload.chatId, payload.senderName, text)
    }
}
