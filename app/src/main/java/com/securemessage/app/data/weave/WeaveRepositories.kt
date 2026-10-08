package com.securemessage.app.data.weave

import kotlinx.coroutines.flow.Flow

/**
 * Repository contracts for WEAVE's primitives. Implementations live in [WeaveFirestore].
 * These intentionally expose relationship/experience operations, not "post" CRUD.
 */

interface MomentRepository {
    /** Live, newest-first moments for the home/"your people" view. */
    fun observeMoments(limit: Int = 50): Flow<List<Moment>>

    /** Publishes a moment that invites interaction. Returns the new id. */
    suspend fun createMoment(
        authorId: String,
        authorName: String,
        body: String,
        intent: Moment.Intent,
    ): Result<String>

    /** Sets or clears the caller's intent reaction (Celebrate / I relate / I can help / Let's talk). */
    suspend fun react(momentId: String, uid: String, reaction: Moment.Reaction?): Result<Unit>

    suspend fun deleteMoment(momentId: String): Result<Unit>

    fun observeMoment(momentId: String): Flow<Moment?>
    fun observeResponses(momentId: String): Flow<List<MomentResponse>>
    suspend fun respond(momentId: String, uid: String, name: String, text: String): Result<Unit>
}

interface SessionRepository {
    /** Live view of sessions currently happening. */
    fun observeLiveSessions(limit: Int = 30): Flow<List<Session>>

    suspend fun createSession(
        hostId: String,
        hostName: String,
        title: String,
        kind: Session.Kind,
        topics: List<String>,
    ): Result<String>

    /** Join as an active participant or as a listener. */
    suspend fun join(sessionId: String, uid: String, asListener: Boolean): Result<Unit>
    suspend fun leave(sessionId: String, uid: String): Result<Unit>
    suspend fun end(sessionId: String): Result<Unit>

    /** Schedules a real-world / online event (a session with a start time). */
    suspend fun createEvent(
        hostId: String,
        hostName: String,
        title: String,
        description: String,
        mode: Session.Mode,
        startsAt: java.util.Date,
        place: String,
    ): Result<String>

    fun observeSession(sessionId: String): Flow<Session?>
    fun observeMessages(sessionId: String, limit: Int = 100): Flow<List<SessionMessage>>
    suspend fun sendMessage(sessionId: String, uid: String, name: String, text: String): Result<Unit>
}

interface CircleRepository {
    fun observeMyCircles(uid: String): Flow<List<Circle>>
    suspend fun createCircle(name: String, emoji: String, ownerId: String, ownerName: String): Result<String>
    suspend fun join(circleId: String, uid: String, name: String): Result<Unit>
    suspend fun leave(circleId: String, uid: String): Result<Unit>
    /** Owner adds someone to their circle. */
    suspend fun addMember(circleId: String, uid: String, name: String): Result<Unit> = join(circleId, uid, name)
}

interface SpaceRepository {
    fun observeSpaces(limit: Int = 50): Flow<List<Space>>
    suspend fun createSpace(name: String, description: String, category: Space.Category, uid: String): Result<String>
    suspend fun join(spaceId: String, uid: String): Result<Unit>
    suspend fun leave(spaceId: String, uid: String): Result<Unit>
}
