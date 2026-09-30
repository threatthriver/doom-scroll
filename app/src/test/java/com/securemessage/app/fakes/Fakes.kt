package com.securemessage.app.fakes

import com.securemessage.app.data.model.Chat
import com.securemessage.app.data.model.Message
import com.securemessage.app.data.model.User
import com.securemessage.app.data.repo.AuthRepository
import com.securemessage.app.data.repo.ChatRepository
import com.securemessage.app.data.repo.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAuthRepository(
    override var currentUserId: String? = "me",
    override var currentEmail: String? = "Me@Example.com",
) : AuthRepository {
    var signUpResult: Result<String> = Result.success("me")
    var signInResult: Result<String> = Result.success("me")
    var calls = 0
    var signedOut = false

    override suspend fun signUp(email: String, password: String): Result<String> { calls++; return signUpResult }
    override suspend fun signIn(email: String, password: String): Result<String> { calls++; return signInResult }
    override fun signOut() { signedOut = true }
}

class FakeUserRepository : UserRepository {
    var createResult: Result<Unit> = Result.success(Unit)
    var getUserResult: Result<User> = Result.success(User("me", "me", "Me", "me@example.com"))
    var searchResult: Result<List<User>> = Result.success(emptyList())
    val created = mutableListOf<User>()
    val searches = mutableListOf<String>()
    var getUserCalls = 0

    override suspend fun createProfile(user: User): Result<Unit> { created += user; return createResult }
    override suspend fun getUser(uid: String): Result<User> { getUserCalls++; return getUserResult }
    override suspend fun searchUsers(query: String, excludeUid: String): Result<List<User>> {
        searches += query
        return searchResult.map { list -> list.filter { it.uid != excludeUid } }
    }
}

class FakeChatRepository : ChatRepository {
    val chats = MutableStateFlow<List<Chat>>(emptyList())
    val messages = MutableStateFlow<List<Message>>(emptyList())
    var openResult: Result<String> = Result.success("chat1")
    var getChatResult: Result<Chat> = Result.failure(NoSuchElementException())
    var sendResult: Result<Unit> = Result.success(Unit)
    val sent = mutableListOf<String>()

    override fun observeChats(uid: String): Flow<List<Chat>> = chats
    override suspend fun openChat(me: User, other: User): Result<String> = openResult
    override suspend fun getChat(chatId: String): Result<Chat> = getChatResult
    override fun observeMessages(chatId: String): Flow<List<Message>> = messages
    override suspend fun sendMessage(chatId: String, senderId: String, text: String): Result<Unit> {
        sent += text
        return sendResult
    }
}
