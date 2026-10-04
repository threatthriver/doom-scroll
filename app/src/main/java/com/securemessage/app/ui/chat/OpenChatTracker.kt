package com.securemessage.app.ui.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks which chat (if any) is currently on screen.
 *
 * The message tray notifier skips chats the user is already looking at —
 * buzzing for a conversation you are reading would be pure noise.
 */
object OpenChatTracker {
    private val _openChatId = MutableStateFlow<String?>(null)
    val openChatId: StateFlow<String?> = _openChatId.asStateFlow()

    fun entered(chatId: String) {
        _openChatId.value = chatId
    }

    fun exited(chatId: String) {
        if (_openChatId.value == chatId) _openChatId.value = null
    }
}
