package com.securemessage.app.data.notify

import com.google.firebase.Timestamp
import com.securemessage.app.data.model.Chat

/** One alert to show: who wrote, in which chat. Never carries message text (it's ciphertext). */
data class IncomingAlert(val chatId: String, val senderName: String)

/**
 * Decides which chat-list changes are new incoming messages worth an alert.
 *
 * Pure logic (no Android, no Firebase I/O) so it can be unit tested. Rules:
 * - The very first snapshot is history, so it only sets the baseline.
 * - A chat already known alerts when its lastMessageAt moves forward.
 * - A chat seen for the first time AFTER the baseline (someone just started a chat with you)
 *   alerts too, but only if its message is recent. That stops a burst of alerts for old chats
 *   when the first snapshot came from an empty local cache.
 * - Never for your own messages, muted chats, the chat on screen, or chats with no sender.
 */
class IncomingMessageDetector(
    private val myUid: String,
    /** Wall-clock millis when watching started. New chats older than this (minus slack) are history. */
    private val startedAtMs: Long,
    private val clockSkewSlackMs: Long = 2 * 60 * 1000L,
) {
    private var baselined = false
    private val lastSeenAt = mutableMapOf<String, Timestamp?>()

    fun onSnapshot(chats: List<Chat>, openChatId: String?): List<IncomingAlert> {
        if (!baselined) {
            baselined = true
            chats.forEach { lastSeenAt[it.id] = it.lastMessageAt }
            return emptyList()
        }
        val alerts = mutableListOf<IncomingAlert>()
        for (chat in chats) {
            val cur = chat.lastMessageAt ?: continue
            val known = lastSeenAt.containsKey(chat.id)
            val prev = lastSeenAt[chat.id]
            val isNew = if (known) {
                prev == null || cur > prev
            } else {
                cur.toDate().time >= startedAtMs - clockSkewSlackMs
            }
            lastSeenAt[chat.id] = cur
            if (!isNew) continue

            val sender = chat.lastSenderId
            if (sender.isEmpty() || sender == myUid) continue
            if (chat.muted[myUid] == true) continue
            if (openChatId == chat.id) continue
            if (chat.lastMessage.isEmpty()) continue

            val name = chat.participantNames[sender]?.takeIf { it.isNotBlank() } ?: "New message"
            alerts += IncomingAlert(chat.id, name)
        }
        return alerts
    }
}
