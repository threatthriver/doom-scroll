package com.securemessage.app.data.model

import com.google.firebase.Timestamp

data class Chat(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageAt: Timestamp? = null,
    val unreadCount: Map<String, Int> = emptyMap(), // userId -> count
    val muted: Map<String, Boolean> = emptyMap(), // userId -> muted
    val pinned: Boolean = false,
    val archived: Map<String, Boolean> = emptyMap(), // userId -> archived
)
