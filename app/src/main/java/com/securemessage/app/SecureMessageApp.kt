package com.securemessage.app

import android.app.Application
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.securemessage.app.data.notify.IncomingMessageDetector
import com.securemessage.app.data.notify.MessageNotifier
import com.securemessage.app.data.notify.NotificationText
import com.securemessage.app.di.AppContainer
import com.securemessage.app.ui.chat.OpenChatTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SecureMessageApp : Application() {
    lateinit var container: AppContainer

    private val appScope get() = container.appScope
    private var watchedUid: String? = null
    private var messageWatchJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)

        // Fires on sign-in, sign-out and every ID-token refresh (e.g. right after verifying email).
        container.firebaseAuth.addIdTokenListener(FirebaseAuth.IdTokenListener { auth ->
            val user = auth.currentUser
            if (user != null) {
                // Publish this device's E2EE public key so the other side can derive the chat key.
                appScope.launch { container.publishMyPublicKey() }
                // Keep this device registered for push (no-op until the push function is live).
                appScope.launch { container.pushTokenRepository.registerCurrentToken() }
                // Watch for incoming messages for any signed-in user. It is NOT gated on email
                // verification: the server decides what may be read, and an alert is the nudge an
                // unverified user needs to finish verifying.
                if (user.uid != watchedUid) {
                    watchedUid = user.uid
                    startMessageWatch(user.uid)
                }
            } else {
                watchedUid = null
                messageWatchJob?.cancel()
                messageWatchJob = null
            }
        })
    }

    /**
     * In-process watcher that posts a tray alert for incoming messages while the app process is
     * alive. When the process is gone, only an FCM push (sent by the Cloud Function) can alert.
     *
     * A Firestore listener error (network drop, permission change after sign-in) used to end the
     * watcher for good. It now reconnects with backoff, keeping its baseline so nothing that
     * arrived during the gap is lost or alerted twice.
     */
    private fun startMessageWatch(uid: String) {
        messageWatchJob?.cancel()
        messageWatchJob = appScope.launch {
            val detector = IncomingMessageDetector(uid, startedAtMs = System.currentTimeMillis())
            var backoffMs = INITIAL_BACKOFF_MS
            while (isActive) {
                try {
                    container.chatRepository.observeChats(uid).collect { chats ->
                        backoffMs = INITIAL_BACKOFF_MS
                        detector.onSnapshot(chats, OpenChatTracker.openChatId.value).forEach { alert ->
                            val text = kotlinx.coroutines.withTimeoutOrNull(5_000L) {
                                NotificationText.latest(container, applicationContext, alert.chatId)
                            }
                            MessageNotifier.showMessage(applicationContext, alert.chatId, alert.senderName, text)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Message watcher stopped (${e.javaClass.simpleName}); retrying")
                }
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    private companion object {
        const val TAG = "MessageWatch"
        const val INITIAL_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 60_000L
    }
}
