package com.securemessage.app.data.firebase

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.securemessage.app.data.chatIdFor
import com.securemessage.app.data.model.Chat
import com.securemessage.app.data.model.Message
import com.securemessage.app.data.model.User
import com.securemessage.app.data.push.PushNotifier
import com.securemessage.app.data.repo.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirestoreChatRepository(
    private val db: FirebaseFirestore,
    /** Asks the push relay to alert the recipient. Never affects whether sending succeeds. */
    private val pushNotifier: PushNotifier? = null,
    private val scope: CoroutineScope? = null,
) : ChatRepository {

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
        val ref = chats.document(chatId)
        // The chat list keeps this document in the local cache, so read it from there first:
        // opening a chat must not wait for the network. Fall back to the server if it isn't cached.
        val cached = runCatching { ref.get(Source.CACHE).await() }.getOrNull()
        val doc = if (cached != null && cached.exists()) cached else ref.get().await()
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

    override suspend fun sendMessage(
        chatId: String,
        senderId: String,
        text: String,
        replyToId: String,
        replyToText: String,
        replyToSender: String,
        recipientIds: List<String>?,
    ): Result<Unit> = runCatching {
        val chatRef = chats.document(chatId)
        val batch = db.batch()
        val msgRef = chatRef.collection("messages").document()
        val messageData = mutableMapOf<String, Any>(
            "text" to text,
            "senderId" to senderId,
            "timestamp" to FieldValue.serverTimestamp(),
            "reactions" to emptyMap<String, String>(),
        )
        // Replies carry their quoted context inline so any client version can render
        // them without a second read. (Message creates have no field allow-list.)
        if (replyToId.isNotEmpty()) {
            messageData["replyToId"] = replyToId
            messageData["replyToText"] = replyToText.take(MAX_REPLY_QUOTE_STORED_CHARS)
            messageData["replyToSender"] = replyToSender
        }
        batch.set(msgRef, messageData)
        // Bump unread for the other participants. A NESTED map in a merge-set increments just
        // those keys atomically. (A dotted key like "unreadCount.<uid>" in set() is NOT a path:
        // it would be stored as one literal top-level field and the counter would never move.)
        // Metadata uses merge-set so a first message to a fresh chat can't fail the batch.
        val participants = (recipientIds ?: run {
            // Fallback only: a server read here delays the whole send.
            val chat = chatRef.get().await()
            (chat.get("participants") as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
        }).filter { it != senderId }
        val metadata = mutableMapOf<String, Any>(
            "lastMessage" to text,
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "lastSenderId" to senderId,
        )
        if (participants.isNotEmpty()) {
            metadata["unreadCount"] = participants.associateWith { FieldValue.increment(1) }
        }
        batch.set(chatRef, metadata, SetOptions.merge())
        batch.commit().await()
        // The message is saved; now tell the relay so the other phone gets an alert even if the
        // app there is closed. Runs in the background and failures are only logged.
        if (pushNotifier != null && scope != null) {
            val messageId = msgRef.id
            scope.launch { pushNotifier.messageSent(chatId, messageId) }
        }
        Unit
    }.onFailure { Log.w(TAG, "sendMessage failed", it) }

    override suspend fun getChatFresh(chatId: String): Result<Chat> = runCatching {
        val doc = chats.document(chatId).get(Source.SERVER).await()
        if (!doc.exists()) throw NoSuchElementException("No chat $chatId")
        doc.toChat()
    }

    override fun observeChat(chatId: String): Flow<Chat> = callbackFlow {
        val reg = chats.document(chatId).addSnapshotListener { snap, e ->
            if (e != null) {
                Log.w(TAG, "observeChat failed", e)
                close()
                return@addSnapshotListener
            }
            if (snap != null && snap.exists()) trySend(snap.toChat())
        }
        awaitClose { reg.remove() }
    }

    override suspend fun setTyping(chatId: String, userId: String, value: Long): Result<Unit> = runCatching {
        chats.document(chatId).set(mapOf("typing" to mapOf(userId to value)), SetOptions.merge()).await()
        Unit
    }

    override suspend fun editMessage(
        chatId: String,
        messageId: String,
        text: String,
        updatePreview: Boolean,
    ): Result<Unit> = runCatching {
        val chatRef = chats.document(chatId)
        chatRef.collection("messages").document(messageId)
            .update(mapOf("text" to text, "edited" to true)).await()
        if (updatePreview) chatRef.update("lastMessage", text).await()
        Unit
    }.onFailure { Log.w(TAG, "editMessage failed", it) }

    override suspend fun deleteMessage(chatId: String, messageId: String): Result<Unit> = runCatching {
        val chatRef = chats.document(chatId)
        val msgRef = chatRef.collection("messages").document(messageId)
        val deletedText = msgRef.get().await().getString("text").orEmpty()
        msgRef.delete().await()

        // Keep the chat-list preview truthful: if the deleted message was the preview,
        // fall back to the newest remaining message (or clear the preview entirely).
        // Non-preview deletes skip the extra reads.
        val chat = chatRef.get().await()
        if (deletedText.isNotEmpty() && chat.getString("lastMessage") == deletedText) {
            val latest = chatRef.collection("messages")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get().await().documents.firstOrNull()
            if (latest == null) {
                chatRef.update(
                    mapOf(
                        "lastMessage" to "",
                        "lastSenderId" to "",
                        "lastMessageAt" to FieldValue.delete(),
                    )
                ).await()
            } else {
                chatRef.update(
                    mapOf(
                        "lastMessage" to latest.getString("text").orEmpty(),
                        "lastSenderId" to latest.getString("senderId").orEmpty(),
                        "lastMessageAt" to (latest.getTimestamp("timestamp") ?: FieldValue.serverTimestamp()),
                    )
                ).await()
            }
        }
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
        // limitToLast (not limit): with ascending order, limit() would return the FIRST
        // N messages overall instead of the N immediately preceding the cursor.
        chats.document(chatId).collection("messages")
            .orderBy("timestamp")
            .endBefore(beforeTimestamp)
            .limitToLast(limit.toLong())
            .get().await()
            .documents.map { it.toMessage() }
    }.onFailure { Log.w(TAG, "loadMoreMessages failed", it) }

    private fun DocumentSnapshot.toChat() = Chat(
        id = id,
        participants = FirestoreCoerce.stringList(get("participants")),
        participantNames = FirestoreCoerce.stringMap(get("participantNames")),
        lastMessage = getString("lastMessage").orEmpty(),
        lastMessageAt = getTimestamp("lastMessageAt", ServerTimestampBehavior.ESTIMATE),
        lastSenderId = getString("lastSenderId").orEmpty(),
        unreadCount = FirestoreCoerce.intMap(get("unreadCount")),
        muted = FirestoreCoerce.booleanMap(get("muted")),
        pinned = FirestoreCoerce.bool(get("pinned")),
        archived = FirestoreCoerce.booleanMap(get("archived")),
        typing = (get("typing") as? Map<*, *>).orEmpty().mapNotNull { (k, v) ->
            (k as? String)?.let { it to ((v as? Number)?.toLong() ?: 0L) }
        }.toMap(),
    )

    private fun DocumentSnapshot.toMessage() = Message(
        id = id,
        text = getString("text").orEmpty(),
        senderId = getString("senderId").orEmpty(),
        timestamp = getTimestamp("timestamp", ServerTimestampBehavior.ESTIMATE),
        reactions = FirestoreCoerce.stringMap(get("reactions")),
        replyToId = getString("replyToId").orEmpty(),
        replyToText = getString("replyToText").orEmpty(),
        replyToSender = getString("replyToSender").orEmpty(),
        edited = FirestoreCoerce.bool(get("edited")),
    )

    private companion object {
        const val TAG = "ChatRepo"
        /**
         * Safety cap on the stored quote. The quote is encrypted (base64) before it gets here, so
         * this must stay well above the 300-char plaintext limit or it would cut the ciphertext.
         */
        const val MAX_REPLY_QUOTE_STORED_CHARS = 2000
    }
}
