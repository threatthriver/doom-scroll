package com.securemessage.app.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.securemessage.app.data.crypto.ChatSessionManager
import com.securemessage.app.data.crypto.E2EEncryption
import com.securemessage.app.data.crypto.KeyStoreManager
import com.securemessage.app.data.model.Message
import com.securemessage.app.data.repo.AuthRepository
import com.securemessage.app.data.repo.ChatRepository
import kotlinx.coroutines.delay
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
    val isLoadingMore: Boolean = false,
    val hasMoreMessages: Boolean = true,
    val isEncrypted: Boolean = false,
    val encryptionFingerprint: String = "",
)

const val MAX_MESSAGE_LENGTH = 2000
const val MESSAGE_PAGE_SIZE = 50
private const val ENCRYPTION_INIT_ATTEMPTS = 4
private const val ENCRYPTION_RETRY_DELAY_MS = 1500L

class ChatViewModel(
    savedStateHandle: SavedStateHandle,
    private val chatRepo: ChatRepository,
    private val authRepo: AuthRepository,
    private val chatSessionManager: ChatSessionManager? = null,
    private val context: Context? = null,
) : ViewModel() {

    private val chatId: String = checkNotNull(savedStateHandle["chatId"])
    val myUid: String? = authRepo.currentUserId

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var encryptionKey: ByteArray? = null

    init {
        initializeEncryption()
        loadChat()
        observeMessages()
    }

    private fun initializeEncryption() {
        if (chatSessionManager == null || context == null || myUid == null) return

        viewModelScope.launch {
            // The other participant may not have published their public key yet (e.g. they
            // haven't opened the app since this feature shipped). Retry a few times with
            // backoff before giving up and leaving the chat in the "Not encrypted" state.
            val chat = chatRepo.getChat(chatId).getOrNull() ?: return@launch
            val otherUid = chat.participants.firstOrNull { it != myUid } ?: return@launch

            repeat(ENCRYPTION_INIT_ATTEMPTS) { attempt ->
                val otherPublicKey = runCatching { chatSessionManager.getPublicKey(otherUid) }
                    .getOrNull()

                if (otherPublicKey != null) {
                    try {
                        val key = chatSessionManager.getOrCreateSessionKey(chatId, otherPublicKey)
                        encryptionKey = key

                        val myPublicKey = KeyStoreManager.getIdentityPublicKey(context)
                        val fingerprint = E2EEncryption.computeFingerprint(myPublicKey.encoded)

                        // Re-decrypt anything that already arrived before the key was ready.
                        _state.update { s ->
                            s.copy(
                                isEncrypted = true,
                                encryptionFingerprint = fingerprint,
                                messages = s.messages.map { decryptMessage(it, key) },
                            )
                        }
                    } catch (e: Exception) {
                        _state.update { it.copy(isEncrypted = false) }
                    }
                    return@launch
                }

                if (attempt < ENCRYPTION_INIT_ATTEMPTS - 1) {
                    delay(ENCRYPTION_RETRY_DELAY_MS)
                }
            }
        }
    }

    /** Decrypts a single message when a key is available; otherwise returns it unchanged. */
    private fun decryptMessage(msg: Message, key: ByteArray?): Message {
        if (key == null || msg.text.isEmpty()) return msg
        return try {
            msg.copy(text = E2EEncryption.decrypt(msg.text, key))
        } catch (e: Exception) {
            msg // Plaintext (pre-encryption) message or wrong key: keep original.
        }
    }

    private fun loadChat() {
        viewModelScope.launch {
            chatRepo.getChat(chatId).onSuccess { chat ->
                val other = chat.participants.firstOrNull { it != myUid }
                val name = other?.let { chat.participantNames[it] }
                if (!name.isNullOrBlank()) _state.update { it.copy(title = name) }
            }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            chatRepo.observeMessages(chatId)
                .catch { e -> _state.update { it.copy(error = e.localizedMessage ?: "Couldn't load messages") } }
                .collect { msgs ->
                    val key = encryptionKey
                    val decryptedMsgs = msgs.map { decryptMessage(it, key) }
                    _state.update { it.copy(messages = decryptedMsgs, error = null) }
                    myUid?.let { uid -> markChatRead(uid) }
                }
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
        
        // Encrypt the message if we have an encryption key
        val textToSend = if (encryptionKey != null) {
            try {
                E2EEncryption.encrypt(text, encryptionKey!!)
            } catch (e: Exception) {
                _state.update { it.copy(userMessage = "Encryption failed") }
                return
            }
        } else {
            text
        }
        
        _state.update { it.copy(draft = "") }
        viewModelScope.launch {
            chatRepo.sendMessage(chatId, uid, textToSend).onFailure {
                _state.update { it.copy(draft = original, userMessage = "Couldn't send message") }
            }
        }
    }

    fun deleteMessage(message: Message) {
        if (message.senderId != myUid) {
            _state.update { it.copy(userMessage = "You can only delete your own messages") }
            return
        }
        viewModelScope.launch {
            chatRepo.deleteMessage(chatId, message.id).onFailure {
                _state.update { it.copy(userMessage = "Couldn't delete message") }
            }
        }
    }

    fun addReaction(message: Message, emoji: String) {
        val uid = myUid ?: return
        viewModelScope.launch {
            if (message.reactions[uid] == emoji) {
                chatRepo.removeReaction(chatId, message.id, uid)
            } else {
                chatRepo.addReaction(chatId, message.id, uid, emoji)
            }
        }
    }

    fun loadMoreMessages() {
        val currentMessages = _state.value.messages
        if (currentMessages.isEmpty() || _state.value.isLoadingMore || !_state.value.hasMoreMessages) return

        val oldestTimestamp = currentMessages.first().timestamp ?: return

        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            chatRepo.loadMoreMessages(chatId, oldestTimestamp, MESSAGE_PAGE_SIZE)
                .onSuccess { olderMessages ->
                    val key = encryptionKey
                    val decryptedOlder = olderMessages.map { decryptMessage(it, key) }
                    _state.update {
                        it.copy(
                            messages = decryptedOlder + currentMessages,
                            isLoadingMore = false,
                            hasMoreMessages = olderMessages.size >= MESSAGE_PAGE_SIZE,
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(isLoadingMore = false) }
                }
        }
    }

    private fun markChatRead(userId: String) {
        viewModelScope.launch {
            chatRepo.markChatRead(chatId, userId)
        }
    }

    fun userMessageShown() = _state.update { it.copy(userMessage = null) }
}
