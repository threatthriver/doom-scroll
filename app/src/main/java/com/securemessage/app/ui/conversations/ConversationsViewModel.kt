package com.securemessage.app.ui.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securemessage.app.data.model.Chat
import com.securemessage.app.data.repo.AuthRepository
import com.securemessage.app.data.repo.ChatRepository
import com.securemessage.app.data.repo.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConversationsUiState(
    val chats: List<Chat> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val needsProfile: Boolean = false,
)

class ConversationsViewModel(
    private val authRepo: AuthRepository,
    private val chatRepo: ChatRepository,
    private val userRepo: UserRepository,
) : ViewModel() {

    val myUid: String? = authRepo.currentUserId

    private val _state = MutableStateFlow(ConversationsUiState(isLoading = myUid != null))
    val state: StateFlow<ConversationsUiState> = _state.asStateFlow()

    init {
        myUid?.let { uid ->
            viewModelScope.launch {
                if (userRepo.getUser(uid).exceptionOrNull() is NoSuchElementException) {
                    _state.update { it.copy(needsProfile = true) }
                }
            }
            viewModelScope.launch {
                chatRepo.observeChats(uid)
                    .catch { e ->
                        _state.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Couldn't load chats") }
                    }
                    .collect { chats ->
                        _state.update { it.copy(chats = sortChats(chats), isLoading = false, error = null) }
                    }
            }
        }
    }

    fun titleFor(chat: Chat): String =
        chat.participants.firstOrNull { it != myUid }?.let { chat.participantNames[it] } ?: "Unknown"

    fun signOut() = authRepo.signOut()

    companion object {
        /** Newest first; chats with no message yet go last. */
        fun sortChats(chats: List<Chat>): List<Chat> =
            chats.sortedWith(compareByDescending(nullsFirst()) { it.lastMessageAt })
    }
}
