package com.securemessage.app.data.model

import com.google.firebase.Timestamp

data class Chat(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageAt: Timestamp? = null,
    /** Sender of [lastMessage]; drives tray notifications. Empty on pre-1.3.0 chats. */
    val lastSenderId: String = "",
    val unreadCount: Map<String, Int> = emptyMap(), // userId -> count
    val muted: Map<String, Boolean> = emptyMap(), // userId -> muted
    val pinned: Boolean = false,
    val archived: Map<String, Boolean> = emptyMap(), // userId -> archived
    /** userId -> a number that changes while that person types (0 = stopped). */
    val typing: Map<String, Long> = emptyMap(),
)
