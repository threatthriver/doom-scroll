package com.securemessage.app.data.model

import com.google.firebase.Timestamp

data class Message(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val timestamp: Timestamp? = null,
    val reactions: Map<String, String> = emptyMap(), // userId -> emoji
)
