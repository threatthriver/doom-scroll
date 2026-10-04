package com.securemessage.app

import android.app.Application
import com.securemessage.app.data.model.Chat
import com.securemessage.app.data.notify.MessageNotifier
import com.securemessage.app.di.AppContainer
import com.securemessage.app.ui.chat.OpenChatTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SecureMessageApp : Application() {
    lateinit var container: AppContainer

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var messageWatchJob: Job? = null

    /** Chat ids seen since process start — first sighting baselines, never notifies. */
    private val baselinedChats = mutableSetOf<String>()
    private val lastSeenAt = mutableMapOf<String, com.google.firebase.Timestamp?>()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)

        // Whenever a user is signed in (at launch or right after sign-in/sign-up), make sure
        // this device's E2EE public key is published so the other party can decrypt our
        // messages. This is what turns the "Secure chat" indicator from a claim into reality.
        container.firebaseAuth.addAuthStateListener { auth ->
            val user = auth.currentUser
            if (user != null) {
                appScope.launch { container.publishMyPublicKey() }
                startMessageWatch(user.uid)
            } else {
                messageWatchJob?.cancel()
                messageWatchJob = null
                baselinedChats.clear()
                lastSeenAt.clear()
            }
        }
    }

    /**
     * App-scoped watcher that posts a tray notification for incoming messages.
     * Works while the process is alive (foreground or background). Skips:
     * - the first snapshot burst (history, not arrivals),
     * - our own messages,
     * - chats the user is currently reading,
     * - chats whose metadata pre-dates the sender field (no sender, no buzz).
     */
    private fun startMessageWatch(uid: String) {
        messageWatchJob?.cancel()
        messageWatchJob = appScope.launch {
            container.chatRepository.observeChats(uid).collect { chats ->
                onChatsSnapshot(uid, chats)
            }
        }
    }

    private fun onChatsSnapshot(uid: String, chats: List<Chat>) {
        for (chat in chats) {
            if (!baselinedChats.contains(chat.id)) {
                baselinedChats.add(chat.id)
                lastSeenAt[chat.id] = chat.lastMessageAt
                continue
            }
            val prev = lastSeenAt[chat.id]
            val cur = chat.lastMessageAt
            if (cur != null && (prev == null || cur > prev)) {
                lastSeenAt[chat.id] = cur
                val sender = chat.lastSenderId
                if (sender.isNotEmpty() && sender != uid &&
                    OpenChatTracker.openChatId.value != chat.id &&
                    chat.lastMessage.isNotEmpty()
                ) {
                    val name = chat.participantNames[sender]?.ifBlank { null } ?: "New message"
                    MessageNotifier.showMessage(applicationContext, chat.id, name, chat.lastMessage)
                }
            }
        }
    }
}
