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
    suspend fun sendMessage(chatId: String, senderId: String, text: String): Result<Unit>
}
