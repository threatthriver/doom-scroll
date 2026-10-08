package com.securemessage.app.data.weave

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.securemessage.app.data.firebase.FirestoreCoerce
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val TAG = "Weave"

// ===========================================================================
// MOMENTS
// ===========================================================================
class FirestoreMomentRepository(private val db: FirebaseFirestore) : MomentRepository {

    private val col get() = db.collection("moments")

    override fun observeMoments(limit: Int): Flow<List<Moment>> = callbackFlow {
        val reg = col.orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snap, e ->
                if (e != null) { Log.e(TAG, "observeMoments failed", e); close(e); return@addSnapshotListener }
                trySend(snap?.documents.orEmpty().map { it.toMoment() })
            }
        awaitClose { reg.remove() }
    }

    override suspend fun createMoment(
        authorId: String,
        authorName: String,
        body: String,
        intent: Moment.Intent,
    ): Result<String> = runCatching {
        val text = body.trim()
        require(text.isNotEmpty()) { "Moment is empty" }
        require(text.length <= MAX_BODY) { "Moment is too long" }
        val doc = col.document()
        doc.set(
            mapOf(
                "authorId" to authorId,
                "authorName" to authorName,
                "body" to text,
                "intent" to intent.name,
                "audienceType" to Moment.Audience.PEOPLE.name,
                "audienceId" to "",
                "reactions" to emptyMap<String, String>(),
                "responseCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        doc.id
    }.onFailure { Log.e(TAG, "createMoment failed", it) }

    override suspend fun react(momentId: String, uid: String, reaction: Moment.Reaction?): Result<Unit> = runCatching {
        val field = "reactions.$uid"
        if (reaction == null) {
            col.document(momentId).update(field, FieldValue.delete()).await()
        } else {
            col.document(momentId).update(field, reaction.name).await()
        }
        Unit
    }.onFailure { Log.w(TAG, "react failed", it) }

    override suspend fun deleteMoment(momentId: String): Result<Unit> = runCatching {
        col.document(momentId).delete().await(); Unit
    }.onFailure { Log.w(TAG, "deleteMoment failed", it) }

    override fun observeMoment(momentId: String): Flow<Moment?> = callbackFlow {
        val reg = col.document(momentId).addSnapshotListener { snap, e ->
            if (e != null) { Log.w(TAG, "observeMoment failed", e); close(e); return@addSnapshotListener }
            trySend(snap?.takeIf { it.exists() }?.toMoment())
        }
        awaitClose { reg.remove() }
    }

    override fun observeResponses(momentId: String): Flow<List<MomentResponse>> = callbackFlow {
        val reg = col.document(momentId).collection("responses")
            .orderBy("createdAt", Query.Direction.ASCENDING).limit(200)
            .addSnapshotListener { snap, e ->
                if (e != null) { Log.w(TAG, "observeResponses failed", e); close(e); return@addSnapshotListener }
                trySend(snap?.documents.orEmpty().map {
                    MomentResponse(
                        id = it.id,
                        authorId = it.getString("authorId").orEmpty(),
                        authorName = it.getString("authorName").orEmpty(),
                        text = it.getString("text").orEmpty(),
                        createdAt = it.get("createdAt") as? Timestamp,
                    )
                })
            }
        awaitClose { reg.remove() }
    }

    override suspend fun respond(momentId: String, uid: String, name: String, text: String): Result<Unit> = runCatching {
        val t = text.trim()
        require(t.isNotEmpty() && t.length <= 1000) { "Response must be 1-1000 characters" }
        col.document(momentId).collection("responses").document().set(
            mapOf(
                "authorId" to uid,
                "authorName" to name.take(50),
                "text" to t,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        Unit
    }.onFailure { Log.w(TAG, "respond failed", it) }

    private fun DocumentSnapshot.toMoment() = Moment(
        id = id,
        authorId = getString("authorId").orEmpty(),
        authorName = getString("authorName").orEmpty(),
        body = getString("body").orEmpty(),
        intent = enumOr(getString("intent"), Moment.Intent.SHARE),
        audienceId = getString("audienceId").orEmpty(),
        audienceType = enumOr(getString("audienceType"), Moment.Audience.PEOPLE),
        reactions = FirestoreCoerce.stringMap(get("reactions")),
        responseCount = (get("responseCount") as? Number)?.toInt() ?: 0,
        createdAt = get("createdAt") as? Timestamp,
    )

    private companion object { const val MAX_BODY = 2000 }
}

// ===========================================================================
// SESSIONS
// ===========================================================================
class FirestoreSessionRepository(private val db: FirebaseFirestore) : SessionRepository {

    private val col get() = db.collection("sessions")

    override fun observeLiveSessions(limit: Int): Flow<List<Session>> = callbackFlow {
        val reg = col.whereEqualTo("isLive", true)
            .orderBy("startedAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snap, e ->
                if (e != null) { Log.e(TAG, "observeLiveSessions failed", e); close(e); return@addSnapshotListener }
                trySend(snap?.documents.orEmpty().map { it.toSession() })
            }
        awaitClose { reg.remove() }
    }

    override suspend fun createSession(
        hostId: String,
        hostName: String,
        title: String,
        kind: Session.Kind,
        topics: List<String>,
    ): Result<String> = runCatching {
        val t = title.trim()
        require(t.isNotEmpty()) { "Session needs a title" }
        val doc = col.document()
        doc.set(
            mapOf(
                "title" to t,
                "kind" to kind.name,
                "hostId" to hostId,
                "hostName" to hostName,
                "contextId" to "",
                "topics" to topics,
                "participantIds" to listOf(hostId),
                "listenerIds" to emptyList<String>(),
                "isLive" to true,
                "startedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        doc.id
    }.onFailure { Log.e(TAG, "createSession failed", it) }

    override suspend fun join(sessionId: String, uid: String, asListener: Boolean): Result<Unit> = runCatching {
        val doc = col.document(sessionId)
        if (asListener) {
            doc.update(
                "listenerIds", FieldValue.arrayUnion(uid),
                "participantIds", FieldValue.arrayRemove(uid),
            ).await()
        } else {
            doc.update(
                "participantIds", FieldValue.arrayUnion(uid),
                "listenerIds", FieldValue.arrayRemove(uid),
            ).await()
        }
        Unit
    }.onFailure { Log.w(TAG, "join session failed", it) }

    override suspend fun leave(sessionId: String, uid: String): Result<Unit> = runCatching {
        col.document(sessionId).update(
            "participantIds", FieldValue.arrayRemove(uid),
            "listenerIds", FieldValue.arrayRemove(uid),
        ).await()
        Unit
    }.onFailure { Log.w(TAG, "leave session failed", it) }

    override suspend fun end(sessionId: String): Result<Unit> = runCatching {
        col.document(sessionId).update("isLive", false).await(); Unit
    }.onFailure { Log.w(TAG, "end session failed", it) }

    override suspend fun createEvent(
        hostId: String,
        hostName: String,
        title: String,
        description: String,
        mode: Session.Mode,
        startsAt: java.util.Date,
        place: String,
    ): Result<String> = runCatching {
        val t = title.trim()
        require(t.isNotEmpty() && t.length <= 100) { "Event needs a title" }
        val doc = col.document()
        doc.set(
            mapOf(
                "title" to t,
                "description" to description.trim().take(1000),
                "kind" to Session.Kind.MEETUP.name,
                "mode" to mode.name,
                "hostId" to hostId,
                "hostName" to hostName,
                "contextId" to "",
                "topics" to emptyList<String>(),
                "participantIds" to listOf(hostId),
                "listenerIds" to emptyList<String>(),
                "isLive" to true,
                "place" to place.trim().take(100),
                "startsAt" to Timestamp(startsAt),
                "startedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        doc.id
    }.onFailure { Log.e(TAG, "createEvent failed", it) }

    override fun observeSession(sessionId: String): Flow<Session?> = callbackFlow {
        val reg = col.document(sessionId).addSnapshotListener { snap, e ->
            if (e != null) { Log.w(TAG, "observeSession failed", e); close(e); return@addSnapshotListener }
            trySend(snap?.takeIf { it.exists() }?.toSession())
        }
        awaitClose { reg.remove() }
    }

    override fun observeMessages(sessionId: String, limit: Int): Flow<List<SessionMessage>> = callbackFlow {
        val reg = col.document(sessionId).collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(limit.toLong())
            .addSnapshotListener { snap, e ->
                if (e != null) { Log.w(TAG, "observeSessionMessages failed", e); close(e); return@addSnapshotListener }
                trySend(snap?.documents.orEmpty().map {
                    SessionMessage(
                        id = it.id,
                        senderId = it.getString("senderId").orEmpty(),
                        senderName = it.getString("senderName").orEmpty(),
                        text = it.getString("text").orEmpty(),
                        createdAt = it.get("createdAt") as? Timestamp,
                    )
                }.reversed())
            }
        awaitClose { reg.remove() }
    }

    override suspend fun sendMessage(sessionId: String, uid: String, name: String, text: String): Result<Unit> = runCatching {
        val t = text.trim()
        require(t.isNotEmpty() && t.length <= 1000) { "Message must be 1-1000 characters" }
        col.document(sessionId).collection("messages").document().set(
            mapOf(
                "senderId" to uid,
                "senderName" to name.take(50),
                "text" to t,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        Unit
    }.onFailure { Log.w(TAG, "session sendMessage failed", it) }

    private fun DocumentSnapshot.toSession() = Session(
        id = id,
        title = getString("title").orEmpty(),
        kind = enumOr(getString("kind"), Session.Kind.DISCUSSION),
        hostId = getString("hostId").orEmpty(),
        hostName = getString("hostName").orEmpty(),
        contextId = getString("contextId").orEmpty(),
        topics = FirestoreCoerce.stringList(get("topics")),
        participantIds = FirestoreCoerce.stringList(get("participantIds")),
        listenerIds = FirestoreCoerce.stringList(get("listenerIds")),
        isLive = get("isLive") as? Boolean ?: true,
        startedAt = get("startedAt") as? Timestamp,
        endsAt = get("endsAt") as? Timestamp,
        place = getString("place").orEmpty(),
        description = getString("description").orEmpty(),
        mode = enumOr(getString("mode"), Session.Mode.ONLINE),
        startsAt = get("startsAt") as? Timestamp,
    )
}

// ===========================================================================
// CIRCLES
// ===========================================================================
class FirestoreCircleRepository(private val db: FirebaseFirestore) : CircleRepository {

    private val col get() = db.collection("circles")

    override fun observeMyCircles(uid: String): Flow<List<Circle>> = callbackFlow {
        val reg = col.whereArrayContains("memberIds", uid)
            .addSnapshotListener { snap, e ->
                if (e != null) { Log.e(TAG, "observeMyCircles failed", e); close(e); return@addSnapshotListener }
                trySend(snap?.documents.orEmpty().map { it.toCircle() })
            }
        awaitClose { reg.remove() }
    }

    override suspend fun createCircle(name: String, emoji: String, ownerId: String, ownerName: String): Result<String> = runCatching {
        val n = name.trim()
        require(n.isNotEmpty()) { "Circle needs a name" }
        val doc = col.document()
        doc.set(
            mapOf(
                "name" to n,
                "emoji" to emoji,
                "ownerId" to ownerId,
                "memberIds" to listOf(ownerId),
                "memberNames" to mapOf(ownerId to ownerName),
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        doc.id
    }.onFailure { Log.e(TAG, "createCircle failed", it) }

    override suspend fun join(circleId: String, uid: String, name: String): Result<Unit> = runCatching {
        col.document(circleId).update(
            "memberIds", FieldValue.arrayUnion(uid),
            "memberNames.$uid", name,
        ).await()
        Unit
    }.onFailure { Log.w(TAG, "join circle failed", it) }

    override suspend fun leave(circleId: String, uid: String): Result<Unit> = runCatching {
        col.document(circleId).update(
            "memberIds", FieldValue.arrayRemove(uid),
            "memberNames.$uid", FieldValue.delete(),
        ).await()
        Unit
    }.onFailure { Log.w(TAG, "leave circle failed", it) }

    private fun DocumentSnapshot.toCircle() = Circle(
        id = id,
        name = getString("name").orEmpty(),
        emoji = getString("emoji").orEmpty(),
        ownerId = getString("ownerId").orEmpty(),
        memberIds = FirestoreCoerce.stringList(get("memberIds")),
        memberNames = FirestoreCoerce.stringMap(get("memberNames")),
        createdAt = get("createdAt") as? Timestamp,
    )
}

// ===========================================================================
// SPACES
// ===========================================================================
class FirestoreSpaceRepository(private val db: FirebaseFirestore) : SpaceRepository {

    private val col get() = db.collection("spaces")

    override fun observeSpaces(limit: Int): Flow<List<Space>> = callbackFlow {
        val reg = col.orderBy("memberCount", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snap, e ->
                if (e != null) { Log.e(TAG, "observeSpaces failed", e); close(e); return@addSnapshotListener }
                trySend(snap?.documents.orEmpty().map { it.toSpace() })
            }
        awaitClose { reg.remove() }
    }

    override suspend fun createSpace(name: String, description: String, category: Space.Category, uid: String): Result<String> = runCatching {
        val n = name.trim()
        require(n.isNotEmpty() && n.length <= 60) { "Space needs a name" }
        val doc = col.document()
        doc.set(
            mapOf(
                "name" to n,
                "description" to description.trim().take(300),
                "category" to category.name,
                "creatorId" to uid,
                "memberIds" to listOf(uid),
                "memberCount" to 1L,
                "onlineCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        doc.id
    }.onFailure { Log.e(TAG, "createSpace failed", it) }

    override suspend fun join(spaceId: String, uid: String): Result<Unit> = runCatching {
        col.document(spaceId).update(
            "memberIds", FieldValue.arrayUnion(uid),
            "memberCount", FieldValue.increment(1),
        ).await()
        Unit
    }.onFailure { Log.w(TAG, "join space failed", it) }

    override suspend fun leave(spaceId: String, uid: String): Result<Unit> = runCatching {
        col.document(spaceId).update(
            "memberIds", FieldValue.arrayRemove(uid),
            "memberCount", FieldValue.increment(-1),
        ).await()
        Unit
    }.onFailure { Log.w(TAG, "leave space failed", it) }

    private fun DocumentSnapshot.toSpace() = Space(
        id = id,
        name = getString("name").orEmpty(),
        description = getString("description").orEmpty(),
        category = enumOr(getString("category"), Space.Category.INTEREST),
        memberCount = (get("memberCount") as? Number)?.toInt() ?: 0,
        onlineCount = (get("onlineCount") as? Number)?.toInt() ?: 0,
        memberIds = FirestoreCoerce.stringList(get("memberIds")),
        createdAt = get("createdAt") as? Timestamp,
    )
}

/** Parse an enum name defensively, falling back to [fallback] for unknown/null values. */
private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
    name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
