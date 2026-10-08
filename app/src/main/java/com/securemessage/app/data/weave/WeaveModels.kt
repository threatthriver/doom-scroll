package com.securemessage.app.data.weave

import com.google.firebase.Timestamp

/**
 * WEAVE's six primitive objects. The unit of the product is the relationship and the shared
 * experience, not the post — these types encode that.
 *
 * All are plain data classes with default values so Firestore's reflective deserialization works,
 * mirroring the existing [com.securemessage.app.data.model] conventions.
 */

// ---------------------------------------------------------------------------
// 1. PERSON — a human context, not a profile.
// ---------------------------------------------------------------------------
data class Person(
    val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val photoUrl: String = "",
    /** Things this person actually wants to interact around. */
    val interests: List<String> = emptyList(),
    /** Free-form "what I'm up to" / current context. */
    val status: String = "",
    /** Coarse location label (e.g. a campus or city) — never precise coordinates by default. */
    val place: String = "",
    /** Lightweight availability signal for the opportunity engine. */
    val availability: Availability = Availability.UNKNOWN,
    val createdAt: Timestamp? = null,
) {
    enum class Availability { UNKNOWN, FREE, FOCUSED, AWAY }
}

// ---------------------------------------------------------------------------
// 2. RELATIONSHIP — an edge between two people, with history & reciprocity.
//    Stored at relationships/{pairId} where pairId = sorted(a,b).join("_").
// ---------------------------------------------------------------------------
data class Relationship(
    val id: String = "",
    /** The two uids, sorted, so the pair has one canonical document. */
    val members: List<String> = emptyList(),
    /** Per-user layer assignment (uid -> layer name). A relationship can be asymmetric. */
    val layer: Map<String, String> = emptyMap(),
    /** Rolling count of back-and-forth exchanges — drives "is this alive?" internally. */
    val reciprocityScore: Int = 0,
    /** Last time the two actually interacted (message, session, reaction). */
    val lastInteractionAt: Timestamp? = null,
    /** Shared contexts (circles, spaces, interests) that connect them. */
    val sharedContext: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
)

// ---------------------------------------------------------------------------
// 3. CIRCLE — a small recurring group.
// ---------------------------------------------------------------------------
data class Circle(
    val id: String = "",
    val name: String = "",
    val emoji: String = "",
    val ownerId: String = "",
    val memberIds: List<String> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val createdAt: Timestamp? = null,
) {
    val size: Int get() = memberIds.size
}

// ---------------------------------------------------------------------------
// 4. SPACE — a broader interest/identity/location community.
// ---------------------------------------------------------------------------
data class Space(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val category: Category = Category.INTEREST,
    val memberCount: Int = 0,
    val onlineCount: Int = 0,
    /** uids who joined (kept small/bounded in the client; count fields drive the UI). */
    val memberIds: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
) {
    enum class Category { INTEREST, LOCATION, UNIVERSITY, PROFESSION, IDENTITY }
}

// ---------------------------------------------------------------------------
// 5. MOMENT — replaces the post. Exists to trigger interaction, not accrue views.
// ---------------------------------------------------------------------------
data class Moment(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val body: String = "",
    /** The author's intent — what kind of response they're inviting. */
    val intent: Intent = Intent.SHARE,
    /** Where it's visible: a circle id, a space id, or empty for the author's people. */
    val audienceId: String = "",
    val audienceType: Audience = Audience.PEOPLE,
    /** Intent reactions: uid -> reaction name (replaces likes). */
    val reactions: Map<String, String> = emptyMap(),
    val responseCount: Int = 0,
    val createdAt: Timestamp? = null,
) {
    /** What the author is inviting. */
    enum class Intent { SHARE, ASK_HELP, INVITE, CELEBRATE, LOOKING_FOR }

    enum class Audience { PEOPLE, CIRCLE, SPACE }

    /** The intent-reaction vocabulary that replaces the "like". */
    enum class Reaction(val label: String, val emoji: String) {
        CELEBRATE("Celebrate", "\uD83C\uDF89"),
        HELP("I can help", "\uD83E\uDD1D"),
        RELATE("Tell me more", "\uD83D\uDCA1"),
        TALK("Let's talk", "\uD83D\uDCAC"),
    }

    fun myReaction(uid: String?): String? = uid?.let { reactions[it] }
    val reactionCount: Int get() = reactions.size
}

// ---------------------------------------------------------------------------
// 6. SESSION — a bounded period where people do something together.
//    The highest-value social object in the system.
// ---------------------------------------------------------------------------
data class Session(
    val id: String = "",
    val title: String = "",
    val kind: Kind = Kind.DISCUSSION,
    val hostId: String = "",
    val hostName: String = "",
    /** Where it lives (circle/space id) or empty for an open session. */
    val contextId: String = "",
    val topics: List<String> = emptyList(),
    /** Active participants (talking/doing) vs listeners (observing). */
    val participantIds: List<String> = emptyList(),
    val listenerIds: List<String> = emptyList(),
    val isLive: Boolean = true,
    val startedAt: Timestamp? = null,
    /** Optional soft end time, for "24 min left" style countdowns. */
    val endsAt: Timestamp? = null,
    /** Optional place for real-world sessions (café, court, library). */
    val place: String = "",
    val description: String = "",
    /** In person / online / hybrid — mainly for events. */
    val mode: Mode = Mode.ONLINE,
    /** Scheduled start for events; null for sessions that are live right now. */
    val startsAt: Timestamp? = null,
) {
    enum class Kind { DISCUSSION, STUDY, PLAY, BUILD, WATCH, VOICE, MEETUP }
    enum class Mode { IN_PERSON, ONLINE, HYBRID }

    val isEvent: Boolean get() = startsAt != null

    val headCount: Int get() = participantIds.size + listenerIds.size
}

/** A reply to a moment (moments/{id}/responses). */
data class MomentResponse(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null,
)

/** A chat line inside a live session (sessions/{id}/messages). Plain text: sessions are group spaces. */
data class SessionMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null,
)
