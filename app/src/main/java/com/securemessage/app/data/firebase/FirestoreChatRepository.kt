package com.securemessage.app.data.firebase

import android.util.Log
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
        batch.set(
            chatRef.collection("messages").document(),
            mapOf("text" to text, "senderId" to senderId, "timestamp" to FieldValue.serverTimestamp()),
        )
        batch.update(chatRef, mapOf("lastMessage" to text, "lastMessageAt" to FieldValue.serverTimestamp()))
        batch.commit().await()
        Unit
    }.onFailure { Log.w(TAG, "sendMessage failed", it) }

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toChat() = Chat(
        id = id,
        participants = (get("participants") as? List<String>).orEmpty(),
        participantNames = (get("participantNames") as? Map<String, String>).orEmpty(),
        lastMessage = getString("lastMessage").orEmpty(),
        lastMessageAt = getTimestamp("lastMessageAt", ServerTimestampBehavior.ESTIMATE),
    )

    private fun DocumentSnapshot.toMessage() = Message(
        id = id,
        text = getString("text").orEmpty(),
        senderId = getString("senderId").orEmpty(),
        timestamp = getTimestamp("timestamp", ServerTimestampBehavior.ESTIMATE),
    )

    private companion object {
        const val TAG = "ChatRepo"
    }
}
