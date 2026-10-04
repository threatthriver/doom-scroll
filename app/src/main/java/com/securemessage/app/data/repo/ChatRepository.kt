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
    ): Result<Unit>
    suspend fun deleteMessage(chatId: String, messageId: String): Result<Unit>
    suspend fun addReaction(chatId: String, messageId: String, userId: String, emoji: String): Result<Unit>
    suspend fun removeReaction(chatId: String, messageId: String, userId: String): Result<Unit>
    suspend fun markChatRead(chatId: String, userId: String): Result<Unit>
    suspend fun toggleMute(chatId: String, userId: String): Result<Unit>
    suspend fun togglePin(chatId: String, userId: String): Result<Unit>
    suspend fun toggleArchive(chatId: String, userId: String): Result<Unit>
    suspend fun loadMoreMessages(chatId: String, beforeTimestamp: com.google.firebase.Timestamp, limit: Int): Result<List<Message>>
}
