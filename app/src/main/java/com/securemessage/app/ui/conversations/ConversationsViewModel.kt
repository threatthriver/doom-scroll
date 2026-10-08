package com.securemessage.app.ui.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securemessage.app.data.AuthGate
import com.securemessage.app.data.model.Chat
import com.securemessage.app.data.repo.AuthRepository
import com.securemessage.app.data.repo.ChatRepository
import com.securemessage.app.data.repo.PushTokenRepository
import com.securemessage.app.data.repo.UserRepository
import com.securemessage.app.data.repo.signOutEverywhere
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Encrypted previews are base64 ciphertext; show a short label instead. */
internal fun previewText(lastMessage: String, empty: String): String = when {
    lastMessage.isEmpty() -> empty
    com.securemessage.app.data.crypto.E2EEncryption.looksEncrypted(lastMessage) -> "Encrypted message"
    else -> lastMessage
}

data class ConversationsUiState(
    val chats: List<Chat> = emptyList(),
    /** Chats this user has archived — hidden from the main list. */
    val archivedChats: List<Chat> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val needsProfile: Boolean = false,
    /** Signed in but the email isn't verified: chats are locked until it is. */
    val needsVerification: Boolean = false,
    /** Our own Firestore display name (FirebaseAuth's copy is stale/empty). */
    val myDisplayName: String = "",
) {
    val archivedCount: Int get() = archivedChats.size
}

class ConversationsViewModel(
    private val authRepo: AuthRepository,
    private val chatRepo: ChatRepository,
    private val userRepo: UserRepository,
    private val pushRepo: PushTokenRepository,
) : ViewModel() {

    /** Read live: the cached-uid version went stale across sign-out/sign-in. */
    val myUid: String? get() = authRepo.currentUserId

    private val canAccess = AuthGate.canAccessChats(myUid != null, authRepo.isEmailVerified)

    private val _state = MutableStateFlow(
        ConversationsUiState(isLoading = canAccess, needsVerification = myUid != null && !canAccess),
    )
    val state: StateFlow<ConversationsUiState> = _state.asStateFlow()

    /** Chat ids with an archive toggle in flight, to block double taps. */
    private val archivePending = mutableSetOf<String>()

    init {
        // Unverified users never start the chat listener; the screen sends them to Verify Email.
        myUid?.takeIf { canAccess }?.let { uid ->
            viewModelScope.launch { pushRepo.registerCurrentToken() }
            viewModelScope.launch {
                userRepo.getUser(uid)
                    .onSuccess { user ->
                        _state.update {
                            it.copy(needsProfile = false, myDisplayName = user.displayName)
                        }
                    }
                    .onFailure { e ->
                        if (e is NoSuchElementException) {
                            _state.update { it.copy(needsProfile = true) }
                        }
                    }
            }
            viewModelScope.launch {
                chatRepo.observeChats(uid)
                    .catch { e ->
                        _state.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Couldn't load chats") }
                    }
                    .collect { chats ->
                        val sorted = sortChats(chats)
                        val archived = sorted.filter { it.archived[uid] == true }
                        _state.update {
                            it.copy(
                                chats = sorted,
                                archivedChats = archived,
                                isLoading = false,
                                error = null,
                            )
                        }
                    }
            }
        }
    }

    fun titleFor(chat: Chat): String =
        chat.participants.firstOrNull { it != myUid }?.let { chat.participantNames[it] } ?: "Unknown"

    fun unreadCountFor(chat: Chat): Int {
        return myUid?.let { chat.unreadCount[it] } ?: 0
    }

    fun isMuted(chat: Chat): Boolean {
        return myUid?.let { chat.muted[it] } ?: false
    }

    fun isPinned(chat: Chat): Boolean {
        return chat.pinned
    }

    fun isArchived(chat: Chat): Boolean {
        return myUid?.let { chat.archived[it] } ?: false
    }

    /** True while an archive toggle for [chat] is still in flight. */
    fun isArchivePending(chat: Chat): Boolean = archivePending.contains(chat.id)

    /** Flip the archive flag for [chat]; the Firestore listener refreshes the list. */
    fun toggleArchive(chat: Chat) {
        val uid = myUid ?: return
        if (archivePending.contains(chat.id)) return
        archivePending += chat.id
        viewModelScope.launch {
            chatRepo.toggleArchive(chat.id, uid)
                .onFailure { e ->
                    _state.update {
                        it.copy(error = e.localizedMessage ?: "Couldn't update archive")
                    }
                }
            archivePending -= chat.id
        }
    }

    /** Removes this device's push token (needs auth), signs out, then calls [onDone]. */
    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            signOutEverywhere(authRepo, pushRepo)
            onDone()
        }
    }

    companion object {
        /** Newest first; chats with no message yet go last. Pinned chats always first. */
        fun sortChats(chats: List<Chat>): List<Chat> =
            chats.sortedWith(
                compareByDescending<Chat> { it.pinned }
                    .thenByDescending(nullsFirst()) { it.lastMessageAt }
            )
    }
}
