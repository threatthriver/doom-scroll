package com.securemessage.app.data.repo

import com.securemessage.app.data.model.Chat
import com.securemessage.app.data.model.Message
import com.securemessage.app.data.model.User
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeChats(uid: String): Flow<List<Chat>>
    suspend fun openChat(me: User, other: User): Result<String>
    suspend fun getChat(chatId: String): Result<Chat>
    fun observeMessages(chatId: String): Flow<List<Message>>
    suspend fun sendMessage(
        chatId: String,
        senderId: String,
        text: String,
        replyToId: String = "",
        replyToText: String = "",
        replyToSender: String = "",
        /**
         * Everyone else in the chat (for unread counters). Pass it when known: without it the
         * repository must read the chat from the server first, and the message then can't be
         * queued (or shown) until that round trip finishes.
         */
        recipientIds: List<String>? = null,
    ): Result<Unit>
    suspend fun deleteMessage(chatId: String, messageId: String): Result<Unit>
    suspend fun addReaction(chatId: String, messageId: String, userId: String, emoji: String): Result<Unit>
    suspend fun removeReaction(chatId: String, messageId: String, userId: String): Result<Unit>
    suspend fun markChatRead(chatId: String, userId: String): Result<Unit>
    suspend fun toggleMute(chatId: String, userId: String): Result<Unit>
    suspend fun togglePin(chatId: String, userId: String): Result<Unit>
    suspend fun toggleArchive(chatId: String, userId: String): Result<Unit>
    /** The chat as the server has it right now (the cached copy can be old when the app was closed). */
    suspend fun getChatFresh(chatId: String): Result<Chat> = getChat(chatId)
    /** Live view of the chat document (typing state etc.). Optional: defaults to nothing. */
    fun observeChat(chatId: String): Flow<Chat> = kotlinx.coroutines.flow.emptyFlow()
    suspend fun setTyping(chatId: String, userId: String, value: Long): Result<Unit> = Result.success(Unit)
    /** Replaces the text of your own message (already encrypted by the caller). */
    suspend fun editMessage(chatId: String, messageId: String, text: String, updatePreview: Boolean): Result<Unit> =
        Result.failure(UnsupportedOperationException("Editing is not supported"))
    suspend fun loadMoreMessages(chatId: String, beforeTimestamp: com.google.firebase.Timestamp, limit: Int): Result<List<Message>>
}
