package com.securemessage.app.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securemessage.app.data.model.Message
import com.securemessage.app.data.repo.AuthRepository
import com.securemessage.app.data.repo.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val title: String = "Chat",
    val messages: List<Message> = emptyList(),
    val draft: String = "",
    val error: String? = null,
    val userMessage: String? = null,
)

const val MAX_MESSAGE_LENGTH = 2000

class ChatViewModel(
    savedStateHandle: SavedStateHandle,
    private val chatRepo: ChatRepository,
    authRepo: AuthRepository,
) : ViewModel() {

    private val chatId: String = checkNotNull(savedStateHandle["chatId"])
    val myUid: String? = authRepo.currentUserId

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            chatRepo.getChat(chatId).onSuccess { chat ->
                val other = chat.participants.firstOrNull { it != myUid }
                val name = other?.let { chat.participantNames[it] }
                if (!name.isNullOrBlank()) _state.update { it.copy(title = name) }
            }
        }
        viewModelScope.launch {
            chatRepo.observeMessages(chatId)
                .catch { e -> _state.update { it.copy(error = e.localizedMessage ?: "Couldn't load messages") } }
                .collect { msgs -> _state.update { it.copy(messages = msgs, error = null) } }
        }
    }

    fun onDraftChange(v: String) = _state.update { it.copy(draft = v) }

    fun send() {
        val original = _state.value.draft
        val text = original.trim()
        val uid = myUid ?: return
        if (text.isEmpty()) return
        if (text.length > MAX_MESSAGE_LENGTH) {
            _state.update { it.copy(userMessage = "Message is too long (max $MAX_MESSAGE_LENGTH)") }
            return
        }
        _state.update { it.copy(draft = "") }
        viewModelScope.launch {
            chatRepo.sendMessage(chatId, uid, text).onFailure {
                _state.update { it.copy(draft = original, userMessage = "Couldn't send message") }
            }
        }
    }

    fun userMessageShown() = _state.update { it.copy(userMessage = null) }
}
