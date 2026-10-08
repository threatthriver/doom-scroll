package com.securemessage.app.ui.weave

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securemessage.app.data.model.User
import com.securemessage.app.data.repo.AuthRepository
import com.securemessage.app.data.repo.ChatRepository
import com.securemessage.app.data.repo.UserRepository
import com.securemessage.app.data.weave.Moment
import com.securemessage.app.data.weave.MomentRepository
import com.securemessage.app.data.weave.MomentResponse
import com.securemessage.app.data.weave.Session
import com.securemessage.app.data.weave.SessionMessage
import com.securemessage.app.data.weave.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Resolves my profile once; shared by the detail view models. */
private suspend fun loadMe(auth: AuthRepository, users: UserRepository): User? =
    auth.currentUserId?.let { users.getUser(it).getOrNull() }

/** Opens (or reuses) the 1:1 chat with [otherUid]. */
private suspend fun openDm(auth: AuthRepository, users: UserRepository, chats: ChatRepository, otherUid: String): Result<String> {
    val me = loadMe(auth, users) ?: return Result.failure(IllegalStateException("No profile"))
    val other = users.getUser(otherUid).getOrElse { return Result.failure(it) }
    return chats.openChat(me, other)
}

// ---------------------------------------------------------------------------
data class MomentUiState(
    val moment: Moment? = null,
    val responses: List<MomentResponse> = emptyList(),
    val loading: Boolean = true,
    val sending: Boolean = false,
    val message: String? = null,
)

class MomentViewModel(
    handle: SavedStateHandle,
    private val auth: AuthRepository,
    private val users: UserRepository,
    private val moments: MomentRepository,
    private val chats: ChatRepository,
) : ViewModel() {
    val momentId: String = checkNotNull(handle["momentId"])
    val myUid: String? get() = auth.currentUserId
    private var myName = ""

    private val _state = MutableStateFlow(MomentUiState())
    val state: StateFlow<MomentUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { myName = loadMe(auth, users)?.displayName.orEmpty() }
        viewModelScope.launch {
            moments.observeMoment(momentId).catch { _state.update { s -> s.copy(loading = false) } }
                .collect { m -> _state.update { it.copy(moment = m, loading = false) } }
        }
        viewModelScope.launch {
            moments.observeResponses(momentId).catch { }.collect { r -> _state.update { it.copy(responses = r) } }
        }
    }

    fun react(reaction: Moment.Reaction) {
        val uid = myUid ?: return
        val m = _state.value.moment ?: return
        val next = if (m.myReaction(uid) == reaction.name) null else reaction
        viewModelScope.launch { moments.react(m.id, uid, next) }
    }

    fun respond(text: String, onDone: () -> Unit) {
        val uid = myUid ?: return
        if (text.isBlank() || _state.value.sending) return
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            val r = moments.respond(momentId, uid, myName.ifBlank { "Someone" }, text)
            _state.update { it.copy(sending = false, message = if (r.isFailure) "Couldn't send" else null) }
            if (r.isSuccess) onDone()
        }
    }

    fun talkToAuthor(onOpenChat: (String) -> Unit) {
        val m = _state.value.moment ?: return
        if (m.authorId == myUid) return
        viewModelScope.launch {
            openDm(auth, users, chats, m.authorId)
                .onSuccess(onOpenChat)
                .onFailure { _state.update { it.copy(message = "Couldn't open a chat") } }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

// ---------------------------------------------------------------------------
data class SessionUiState(
    val session: Session? = null,
    val messages: List<SessionMessage> = emptyList(),
    /** uid -> display name for everyone in the roster (filled lazily). */
    val names: Map<String, String> = emptyMap(),
    val loading: Boolean = true,
    val message: String? = null,
)

class SessionViewModel(
    handle: SavedStateHandle,
    private val auth: AuthRepository,
    private val users: UserRepository,
    private val sessions: SessionRepository,
) : ViewModel() {
    val sessionId: String = checkNotNull(handle["sessionId"])
    val myUid: String? get() = auth.currentUserId
    private var myName = ""

    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { myName = loadMe(auth, users)?.displayName.orEmpty() }
        viewModelScope.launch {
            sessions.observeSession(sessionId).catch { _state.update { s -> s.copy(loading = false) } }.collect { s ->
                _state.update { it.copy(session = s, loading = false) }
                s?.let { resolveNames(it.participantIds + it.listenerIds + it.hostId) }
            }
        }
        viewModelScope.launch {
            sessions.observeMessages(sessionId).catch { }.collect { m -> _state.update { it.copy(messages = m) } }
        }
    }

    private fun resolveNames(uids: List<String>) {
        val missing = uids.distinct().filter { it.isNotEmpty() && it !in _state.value.names }
        missing.forEach { uid ->
            _state.update { it.copy(names = it.names + (uid to "")) }
            viewModelScope.launch {
                val name = users.getUser(uid).getOrNull()?.displayName.orEmpty()
                _state.update { it.copy(names = it.names + (uid to name)) }
            }
        }
    }

    val isIn: Boolean get() = _state.value.session?.let { s -> myUid in s.participantIds || myUid in s.listenerIds } == true

    fun join(asListener: Boolean = false) {
        val uid = myUid ?: return
        viewModelScope.launch { sessions.join(sessionId, uid, asListener) }
    }

    fun leave(onDone: () -> Unit) {
        val uid = myUid ?: return onDone()
        viewModelScope.launch {
            val s = _state.value.session
            // The host leaving ends the session for everyone.
            if (s != null && s.hostId == uid) sessions.end(sessionId) else sessions.leave(sessionId, uid)
            onDone()
        }
    }

    fun send(text: String, onSent: () -> Unit) {
        val uid = myUid ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            if (!isIn) sessions.join(sessionId, uid, asListener = false)
            sessions.sendMessage(sessionId, uid, myName.ifBlank { "Someone" }, text)
                .onSuccess { onSent() }
                .onFailure { _state.update { it.copy(message = "Couldn't send") } }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}
