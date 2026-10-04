package com.securemessage.app.fakes

import com.google.firebase.Timestamp
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
    var getUserResult: Result<User> = Result.success(User("me", "me", "Me"))
    var searchResult: Result<List<User>> = Result.success(emptyList())
    var updateResult: Result<Unit> = Result.success(Unit)
    val created = mutableListOf<User>()
    val searches = mutableListOf<String>()
    var getUserCalls = 0

    override suspend fun createProfile(user: User): Result<Unit> { created += user; return createResult }
    override suspend fun getUser(uid: String): Result<User> { getUserCalls++; return getUserResult }
    override suspend fun searchUsers(query: String, excludeUid: String): Result<List<User>> {
        searches += query
        return searchResult.map { list -> list.filter { it.uid != excludeUid } }
    }
    override suspend fun updateProfile(uid: String, displayName: String, bio: String, photoUrl: String): Result<Unit> {
        return updateResult
    }
}

class FakeChatRepository : ChatRepository {
    val chats = MutableStateFlow<List<Chat>>(emptyList())
    val messages = MutableStateFlow<List<Message>>(emptyList())
    var openResult: Result<String> = Result.success("chat1")
    var getChatResult: Result<Chat> = Result.failure(NoSuchElementException())
    var sendResult: Result<Unit> = Result.success(Unit)
    var addReactionResult: Result<Unit> = Result.success(Unit)
    var removeReactionResult: Result<Unit> = Result.success(Unit)
    var markReadResult: Result<Unit> = Result.success(Unit)
    var toggleMuteResult: Result<Unit> = Result.success(Unit)
    var togglePinResult: Result<Unit> = Result.success(Unit)
    var toggleArchiveResult: Result<Unit> = Result.success(Unit)
    var loadMoreResult: Result<List<Message>> = Result.success(emptyList())
    val sent = mutableListOf<String>()
    val sentReplies = mutableListOf<Triple<String, String, String>>()
    val deleted = mutableListOf<String>()
    val reactions = mutableListOf<Triple<String, String, String>>()
    val readMarked = mutableListOf<String>()
    val mutedToggled = mutableListOf<String>()
    val pinnedToggled = mutableListOf<String>()
    val archivedToggled = mutableListOf<String>()

    override fun observeChats(uid: String): Flow<List<Chat>> = chats
    override suspend fun openChat(me: User, other: User): Result<String> = openResult
    override suspend fun getChat(chatId: String): Result<Chat> = getChatResult
    override fun observeMessages(chatId: String): Flow<List<Message>> = messages
    override suspend fun sendMessage(
        chatId: String,
        senderId: String,
        text: String,
        replyToId: String,
        replyToText: String,
        replyToSender: String
    ): Result<Unit> {
        sent += text
        sentReplies += Triple(replyToId, replyToText, replyToSender)
        return sendResult
    }
    override suspend fun deleteMessage(chatId: String, messageId: String): Result<Unit> {
        deleted += messageId
        return Result.success(Unit)
    }
    override suspend fun addReaction(chatId: String, messageId: String, userId: String, emoji: String): Result<Unit> {
        reactions.add(Triple(messageId, userId, emoji))
        return addReactionResult
    }
    override suspend fun removeReaction(chatId: String, messageId: String, userId: String): Result<Unit> {
        reactions.remove(Triple(messageId, userId, ""))
        return removeReactionResult
    }
    override suspend fun markChatRead(chatId: String, userId: String): Result<Unit> {
        readMarked += chatId
        return markReadResult
    }
    override suspend fun toggleMute(chatId: String, userId: String): Result<Unit> {
        mutedToggled += chatId
        return toggleMuteResult
    }
    override suspend fun togglePin(chatId: String, userId: String): Result<Unit> {
        pinnedToggled += chatId
        return togglePinResult
    }
    override suspend fun toggleArchive(chatId: String, userId: String): Result<Unit> {
        archivedToggled += chatId
        return toggleArchiveResult
    }
    override suspend fun loadMoreMessages(chatId: String, beforeTimestamp: Timestamp, limit: Int): Result<List<Message>> {
        return loadMoreResult
    }
}
