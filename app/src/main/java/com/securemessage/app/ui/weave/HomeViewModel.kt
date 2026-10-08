package com.securemessage.app.ui.weave

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securemessage.app.data.model.User
import com.securemessage.app.data.repo.AuthRepository
import com.securemessage.app.data.repo.UserRepository
import com.securemessage.app.data.weave.Circle
import com.securemessage.app.data.weave.CircleRepository
import com.securemessage.app.data.weave.Moment
import com.securemessage.app.data.weave.MomentRepository
import com.securemessage.app.data.weave.Session
import com.securemessage.app.data.weave.SessionRepository
import com.securemessage.app.data.weave.Space
import com.securemessage.app.data.weave.SpaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Shared state for the WEAVE tabs (Home, Explore, Nearby, Circles, Profile, Assistant): who I am,
 * my circles, live sessions/events, moments and spaces. Bounded lists only — no infinite feed.
 */
data class HomeUiState(
    val me: User = User(),
    val circles: List<Circle> = emptyList(),
    val liveSessions: List<Session> = emptyList(),
    val moments: List<Moment> = emptyList(),
    val spaces: List<Space> = emptyList(),
    val loading: Boolean = true,
    val posting: Boolean = false,
    val error: String? = null,
    val message: String? = null,
) {
    val displayName: String get() = me.displayName
}

class HomeViewModel(
    private val authRepo: AuthRepository,
    private val userRepo: UserRepository,
    private val momentRepo: MomentRepository,
    private val sessionRepo: SessionRepository,
    private val circleRepo: CircleRepository,
    private val spaceRepo: SpaceRepository,
) : ViewModel() {

    val myUid: String? get() = authRepo.currentUserId

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val myName: String get() = _state.value.me.displayName.ifBlank { "You" }

    init {
        refreshMe()
        myUid?.let { uid ->
            viewModelScope.launch {
                circleRepo.observeMyCircles(uid).catch { }.collect { c -> _state.update { it.copy(circles = c) } }
            }
        }
        viewModelScope.launch {
            sessionRepo.observeLiveSessions(limit = 30).catch { }.collect { s -> _state.update { it.copy(liveSessions = s) } }
        }
        viewModelScope.launch {
            spaceRepo.observeSpaces(limit = 60).catch { }.collect { s -> _state.update { it.copy(spaces = s) } }
        }
        viewModelScope.launch {
            momentRepo.observeMoments(limit = 30)
                .catch { e -> _state.update { it.copy(loading = false, error = e.localizedMessage) } }
                .collect { m -> _state.update { it.copy(moments = m, loading = false, error = null) } }
        }
    }

    fun refreshMe() {
        val uid = myUid ?: return
        viewModelScope.launch {
            userRepo.getUser(uid).onSuccess { u -> _state.update { it.copy(me = u) } }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun say(msg: String?) { if (msg != null) _state.update { it.copy(message = msg) } }

    fun postMoment(body: String, intent: Moment.Intent, onDone: (Boolean) -> Unit = {}) {
        val uid = myUid
        val text = body.trim()
        if (uid == null || text.isEmpty() || _state.value.posting) { onDone(false); return }
        _state.update { it.copy(posting = true) }
        viewModelScope.launch {
            val result = momentRepo.createMoment(uid, myName, text, intent)
            _state.update { it.copy(posting = false) }
            if (result.isFailure) say("Couldn't share your moment")
            onDone(result.isSuccess)
        }
    }

    fun react(moment: Moment, reaction: Moment.Reaction) {
        val uid = myUid ?: return
        val next = if (moment.myReaction(uid) == reaction.name) null else reaction
        viewModelScope.launch { momentRepo.react(moment.id, uid, next) }
    }

    fun deleteMoment(moment: Moment) {
        if (moment.authorId != myUid) return
        viewModelScope.launch { momentRepo.deleteMoment(moment.id) }
    }

    fun joinSession(session: Session, asListener: Boolean = false) {
        val uid = myUid ?: return
        viewModelScope.launch { sessionRepo.join(session.id, uid, asListener) }
    }

    fun startSession(title: String, kind: Session.Kind, topics: List<String>, onDone: (String?) -> Unit) {
        val uid = myUid ?: return onDone(null)
        _state.update { it.copy(posting = true) }
        viewModelScope.launch {
            val r = sessionRepo.createSession(uid, myName, title, kind, topics)
            _state.update { it.copy(posting = false) }
            if (r.isFailure) say(r.exceptionOrNull()?.message ?: "Couldn't start the session")
            onDone(r.getOrNull())
        }
    }

    fun createEvent(
        title: String,
        description: String,
        mode: Session.Mode,
        startsAt: Date,
        place: String,
        onDone: (String?) -> Unit,
    ) {
        val uid = myUid ?: return onDone(null)
        _state.update { it.copy(posting = true) }
        viewModelScope.launch {
            val r = sessionRepo.createEvent(uid, myName, title, description, mode, startsAt, place)
            _state.update { it.copy(posting = false) }
            say(if (r.isSuccess) "Event created" else r.exceptionOrNull()?.message ?: "Couldn't create the event")
            onDone(r.getOrNull())
        }
    }

    fun createCircle(name: String, emoji: String) {
        val uid = myUid ?: return
        viewModelScope.launch {
            circleRepo.createCircle(name, emoji, uid, myName).onFailure { say(it.message ?: "Couldn't create circle") }
        }
    }

    fun leaveCircle(circle: Circle) {
        val uid = myUid ?: return
        viewModelScope.launch { circleRepo.leave(circle.id, uid) }
    }

    fun addToCircle(circle: Circle, user: User) {
        viewModelScope.launch {
            circleRepo.addMember(circle.id, user.uid, user.displayName.ifBlank { user.username })
                .onSuccess { say("Added ${user.displayName.ifBlank { user.username }}") }
                .onFailure { say("Couldn't add them") }
        }
    }

    suspend fun searchPeople(query: String): List<User> =
        userRepo.searchUsers(query, myUid.orEmpty()).getOrDefault(emptyList())

    fun isMember(space: Space): Boolean = myUid != null && space.memberIds.contains(myUid)

    fun toggleSpace(space: Space) {
        val uid = myUid ?: return
        viewModelScope.launch {
            if (isMember(space)) spaceRepo.leave(space.id, uid) else spaceRepo.join(space.id, uid)
        }
    }

    fun createSpace(name: String, description: String, category: Space.Category) {
        val uid = myUid ?: return
        viewModelScope.launch {
            spaceRepo.createSpace(name, description, category, uid)
                .onSuccess { say("Space created") }
                .onFailure { say(it.message ?: "Couldn't create space") }
        }
    }

    fun saveDetails(displayName: String, bio: String, headline: String, place: String, interests: List<String>, onDone: () -> Unit) {
        val uid = myUid ?: return
        val name = displayName.trim()
        if (name.isEmpty() || name.length > 50) { say("Name must be 1-50 characters"); return }
        if (bio.trim().length > 160) { say("Bio must be at most 160 characters"); return }
        viewModelScope.launch {
            val a = userRepo.updateProfile(uid, name, bio.trim(), _state.value.me.photoUrl)
            val b = userRepo.updateDetails(uid, headline, place, interests)
            if (a.isSuccess && b.isSuccess) {
                say("Profile saved")
                refreshMe()
                onDone()
            } else {
                say("Couldn't save your profile")
            }
        }
    }
}
