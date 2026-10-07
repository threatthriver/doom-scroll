package com.securemessage.app.data.model

import com.google.firebase.Timestamp

data class Message(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val timestamp: Timestamp? = null,
    val reactions: Map<String, String> = emptyMap(), // userId -> emoji
    /** Quoted context for replies. Empty when the message is not a reply. */
    val replyToId: String = "",
    val replyToText: String = "",
    val replyToSender: String = "",
    /** True once the sender has changed the text after sending. */
    val edited: Boolean = false,
)
