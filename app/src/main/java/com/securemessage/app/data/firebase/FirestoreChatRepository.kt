package com.securemessage.app.data.firebase

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.securemessage.app.data.chatIdFor
import com.securemessage.app.data.model.Chat
import com.securemessage.app.data.model.Message
import com.securemessage.app.data.model.User
import com.securemessage.app.data.repo.ChatRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreChatRepository(private val db: FirebaseFirestore) : ChatRepository {

    private val chats get() = db.collection("chats")

    override fun observeChats(uid: String): Flow<List<Chat>> = callbackFlow {
        val reg = chats.whereArrayContains("participants", uid).addSnapshotListener { snap, e ->
            if (e != null) {
                Log.e(TAG, "observeChats failed", e)
                close(e)
                return@addSnapshotListener
            }
            trySend(snap?.documents.orEmpty().map { it.toChat() })
        }
        awaitClose { reg.remove() }
    }

    override suspend fun openChat(me: User, other: User): Result<String> = runCatching {
        val id = chatIdFor(me.uid, other.uid)
        // No read first: a get() on a missing chat is denied by the rules.
        chats.document(id).set(
            mapOf(
                "participants" to listOf(me.uid, other.uid).sorted(),
                "participantNames" to mapOf(me.uid to me.displayName, other.uid to other.displayName),
            ),
            SetOptions.mergeFields("participants", "participantNames"),
        ).await()
        id
    }.onFailure { Log.e(TAG, "openChat failed", it) }

    override suspend fun getChat(chatId: String): Result<Chat> = runCatching {
        val doc = chats.document(chatId).get().await()
        if (!doc.exists()) throw NoSuchElementException("No chat $chatId")
        doc.toChat()
    }.onFailure { Log.w(TAG, "getChat failed", it) }

    override fun observeMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val reg = chats.document(chatId).collection("messages")
            .orderBy("timestamp")
            .limitToLast(200)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    Log.e(TAG, "observeMessages failed", e)
                    close(e)
                    return@addSnapshotListener
                }
                trySend(snap?.documents.orEmpty().map { it.toMessage() })
            }
        awaitClose { reg.remove() }
    }

    override suspend fun sendMessage(chatId: String, senderId: String, text: String): Result<Unit> = runCatching {
        val chatRef = chats.document(chatId)
        val batch = db.batch()
        val msgRef = chatRef.collection("messages").document()
        batch.set(
            msgRef,
            mapOf(
                "text" to text,
                "senderId" to senderId,
                "timestamp" to FieldValue.serverTimestamp(),
                "reactions" to emptyMap<String, String>(),
            ),
        )
        // Increment unread count for other participants
        val chat = chatRef.get().await()
        val participants = chat.get("participants") as? List<*> ?: emptyList<String>()
        val unreadUpdates = participants.filter { it != senderId }.associate { it.toString() to FieldValue.increment(1) }
        batch.update(chatRef, mapOf(
            "lastMessage" to text,
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "unreadCount" to unreadUpdates,
        ))
        batch.commit().await()
        Unit
    }.onFailure { Log.w(TAG, "sendMessage failed", it) }

    override suspend fun deleteMessage(chatId: String, messageId: String): Result<Unit> = runCatching {
        chats.document(chatId).collection("messages").document(messageId).delete().await()
        Unit
    }.onFailure { Log.w(TAG, "deleteMessage failed", it) }

    override suspend fun addReaction(chatId: String, messageId: String, userId: String, emoji: String): Result<Unit> = runCatching {
        val msgRef = chats.document(chatId).collection("messages").document(messageId)
        msgRef.update("reactions.$userId", emoji).await()
        Unit
    }.onFailure { Log.w(TAG, "addReaction failed", it) }

    override suspend fun removeReaction(chatId: String, messageId: String, userId: String): Result<Unit> = runCatching {
        val msgRef = chats.document(chatId).collection("messages").document(messageId)
        msgRef.update("reactions.$userId", FieldValue.delete()).await()
        Unit
    }.onFailure { Log.w(TAG, "removeReaction failed", it) }

    override suspend fun markChatRead(chatId: String, userId: String): Result<Unit> = runCatching {
        chats.document(chatId).update("unreadCount.$userId", 0).await()
        Unit
    }.onFailure { Log.w(TAG, "markChatRead failed", it) }

    override suspend fun toggleMute(chatId: String, userId: String): Result<Unit> = runCatching {
        val chatRef = chats.document(chatId)
        val chat = chatRef.get().await()
        val current = chat.get("muted") as? Map<*, *>
        val isMuted = current?.get(userId) as? Boolean ?: false
        chatRef.update("muted.$userId", !isMuted).await()
        Unit
    }.onFailure { Log.w(TAG, "toggleMute failed", it) }

    override suspend fun togglePin(chatId: String, userId: String): Result<Unit> = runCatching {
        val chatRef = chats.document(chatId)
        val chat = chatRef.get().await()
        val current = chat.get("pinned") as? Boolean ?: false
        chatRef.update("pinned", !current).await()
        Unit
    }.onFailure { Log.w(TAG, "togglePin failed", it) }

    override suspend fun toggleArchive(chatId: String, userId: String): Result<Unit> = runCatching {
        val chatRef = chats.document(chatId)
        val chat = chatRef.get().await()
        val current = chat.get("archived") as? Map<*, *>
        val isArchived = current?.get(userId) as? Boolean ?: false
        chatRef.update("archived.$userId", !isArchived).await()
        Unit
    }.onFailure { Log.w(TAG, "toggleArchive failed", it) }

    override suspend fun loadMoreMessages(chatId: String, beforeTimestamp: Timestamp, limit: Int): Result<List<Message>> = runCatching {
        chats.document(chatId).collection("messages")
            .orderBy("timestamp")
            .endBefore(beforeTimestamp)
            .limit(limit.toLong())
            .get().await()
            .documents.map { it.toMessage() }
    }.onFailure { Log.w(TAG, "loadMoreMessages failed", it) }

    private fun DocumentSnapshot.toChat() = Chat(
        id = id,
        participants = FirestoreCoerce.stringList(get("participants")),
        participantNames = FirestoreCoerce.stringMap(get("participantNames")),
        lastMessage = getString("lastMessage").orEmpty(),
        lastMessageAt = getTimestamp("lastMessageAt", ServerTimestampBehavior.ESTIMATE),
        unreadCount = FirestoreCoerce.intMap(get("unreadCount")),
        muted = FirestoreCoerce.booleanMap(get("muted")),
        pinned = FirestoreCoerce.bool(get("pinned")),
        archived = FirestoreCoerce.booleanMap(get("archived")),
    )

    private fun DocumentSnapshot.toMessage() = Message(
        id = id,
        text = getString("text").orEmpty(),
        senderId = getString("senderId").orEmpty(),
        timestamp = getTimestamp("timestamp", ServerTimestampBehavior.ESTIMATE),
        reactions = FirestoreCoerce.stringMap(get("reactions")),
    )

    private companion object {
        const val TAG = "ChatRepo"
    }
}
