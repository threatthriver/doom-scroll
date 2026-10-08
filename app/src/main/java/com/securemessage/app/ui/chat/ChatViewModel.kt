package com.securemessage.app.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securemessage.app.data.crypto.ChatCrypto
import com.securemessage.app.data.crypto.E2EEncryption
import com.securemessage.app.data.crypto.SafetyNumberStore
import com.securemessage.app.data.crypto.SecureChannel
import com.securemessage.app.data.model.Message
import com.securemessage.app.data.repo.AuthRepository
import com.securemessage.app.data.repo.ChatRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

data class ChatUiState(
    val title: String = "Chat",
    val messages: List<Message> = emptyList(),
    val draft: String = "",
    val error: String? = null,
    /** True until the first message snapshot (or an error) arrives, so the screen can show a
     *  loading state instead of flashing the "No messages yet" empty card on every open. */
    val isLoading: Boolean = true,
    val userMessage: String? = null,
    val isLoadingMore: Boolean = false,
    val hasMoreMessages: Boolean = true,
    val isEncrypted: Boolean = false,
    /** The chat's safety number (same on both phones), or empty until a channel exists. */
    val encryptionFingerprint: String = "",
    /** The other person's key changed since you last accepted it. */
    val safetyNumberChanged: Boolean = false,
    /** The other person is typing right now. */
    val otherTyping: Boolean = false,
    /** The message being edited (the composer holds its text), or null. */
    val editing: Message? = null,
)

const val MAX_MESSAGE_LENGTH = 2000
const val MESSAGE_PAGE_SIZE = 50
/** Quoted reply preview cap, mirrored by the repository write path. */
const val MAX_REPLY_QUOTE_CHARS = 300

/** Shown in place of an encrypted message while the key is still being fetched. */
const val TEXT_ENCRYPTED_PENDING = "\uD83D\uDD12 Encrypted message"
/** Shown in place of a message that could not be decrypted with the current key. */
const val TEXT_UNREADABLE = "\uD83D\uDD12 Can't decrypt this message"
private const val QUOTE_LOCKED = "\uD83D\uDD12"

fun isLockedPlaceholder(text: String) = text == TEXT_ENCRYPTED_PENDING || text == TEXT_UNREADABLE

private const val ENCRYPTION_INIT_ATTEMPTS = 4
private const val ENCRYPTION_RETRY_DELAY_MS = 1500L
/** A lookup that takes longer than this is treated as "not available right now". */
private const val KEY_LOOKUP_TIMEOUT_MS = 4_000L
/** Re-check the other person's key in the background after a send if it's been this long. */
private const val REVERIFY_AFTER_MS = 2 * 60 * 1000L
private const val MARK_READ_THROTTLE_MS = 5_000L
private const val KEY_DRAFT = "draft"
private const val TYPING_WRITE_INTERVAL_MS = 3_000L
private const val TYPING_EXPIRES_MS = 6_000L


class ChatViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val chatRepo: ChatRepository,
    private val authRepo: AuthRepository,
    private val crypto: ChatCrypto,
    private val safetyNumbers: SafetyNumberStore,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Where decryption runs. Off the main thread so a long chat can't stall scrolling. */
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    val chatId: String = checkNotNull(savedStateHandle["chatId"])
    val myUid: String? = authRepo.currentUserId

    // The unsent draft survives process death and rotation (restored from saved state).
    private val _state = MutableStateFlow(ChatUiState(draft = savedStateHandle.get<String>(KEY_DRAFT).orEmpty()))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /** Messages exactly as stored (still encrypted), oldest first. The screen shows decrypted ones. */
    private var raw: List<Message> = emptyList()

    @Volatile private var channel: SecureChannel? = null
    /** Bumps whenever the key changes, so cached decrypted messages from the old key are dropped. */
    @Volatile private var keyEpoch = 0
    private var otherUid: String? = null
    private var lastVerifiedAt = 0L
    private val channelLock = Mutex()
    private val renderLock = Mutex()

    /** Decrypted copy of each message. Reused while the message and key are unchanged. */
    private class Shown(val raw: Message, val epoch: Int, val out: Message)
    private val shown = ConcurrentHashMap<String, Shown>()

    init {
        initializeEncryption()
        loadChat()
        observeMessages()
        observeTyping()
    }

    // ---- Secure channel ---------------------------------------------------------------------

    private fun initializeEncryption() {
        if (myUid == null) return
        viewModelScope.launch {
            // Fast path: the other person's key is usually already on the phone, so the messages
            // can be readable straight away while the server confirms the key below.
            val other = otherParticipant()
            if (other != null) {
                val cached = runCatching { crypto.openCachedChannel(chatId, other) }.getOrNull()
                if (cached != null) channelLock.withLock { if (channel == null) applyChannel(cached, verify = false) }
            }
            // Authoritative lookup. The other person may not have published a key yet, so retry a
            // few times; sending tries again on demand.
            repeat(ENCRYPTION_INIT_ATTEMPTS) { attempt ->
                if (establishChannel()) return@launch
                if (attempt < ENCRYPTION_INIT_ATTEMPTS - 1) delay(ENCRYPTION_RETRY_DELAY_MS)
            }
        }
    }

    private suspend fun otherParticipant(): String? {
        otherUid?.let { return it }
        val chat = chatRepo.getChat(chatId).getOrNull() ?: return null
        return chat.participants.firstOrNull { it != myUid }?.also { otherUid = it }
    }

    /**
     * Looks up the other person's key on the server and (re)builds the channel. Returns true if a
     * channel exists afterwards. If the lookup fails, an already-working channel is kept.
     */
    private suspend fun establishChannel(): Boolean = channelLock.withLock {
        val other = otherParticipant() ?: return channel != null
        val fresh = withTimeoutOrNull(KEY_LOOKUP_TIMEOUT_MS) {
            runCatching { crypto.openChannel(chatId, other) }.getOrNull()
        }
        lastVerifiedAt = clock()
        if (fresh == null) return channel != null
        applyChannel(fresh, verify = true)
        true
    }

    /** Adopts [ch]. With [verify], also applies trust-on-first-use and flags a changed number. */
    private suspend fun applyChannel(ch: SecureChannel, verify: Boolean) {
        val previous = channel
        channel = ch
        if (previous == null || !previous.key.contentEquals(ch.key)) keyEpoch++

        var changed = _state.value.safetyNumberChanged
        if (verify) {
            val accepted = safetyNumbers.get(chatId)
            changed = if (accepted == null) {
                safetyNumbers.set(chatId, ch.safetyNumber)
                false
            } else {
                accepted != ch.safetyNumber
            }
        }
        _state.update {
            it.copy(isEncrypted = true, encryptionFingerprint = ch.safetyNumber, safetyNumberChanged = changed)
        }
        if (previous == null || !previous.key.contentEquals(ch.key)) refresh()
    }

    /** The user has seen the warning (and presumably compared codes): accept the new number. */
    fun acknowledgeSafetyNumber() {
        channel?.let { safetyNumbers.set(chatId, it.safetyNumber) }
        _state.update { it.copy(safetyNumberChanged = false) }
    }

    // ---- Showing messages -------------------------------------------------------------------

    /** Turns one stored field into what the screen shows. Never shows ciphertext as if it were text. */
    private fun open(value: String, locked: String, unreadable: String): String {
        if (value.isEmpty()) return value
        val ch = channel
        val key = ch?.key
        if (key == null) return if (E2EEncryption.looksEncrypted(value)) locked else value
        return try {
            // Pass the legacy key too, so messages sent by older app versions still decrypt.
            E2EEncryption.decrypt(value, key, ch.legacyKey)
        } catch (e: Exception) {
            // Not ciphertext at all means an old plaintext message: show it as it is.
            if (E2EEncryption.looksEncrypted(value)) unreadable else value
        }
    }

    private fun decrypted(msg: Message): Message {
        val epoch = keyEpoch
        shown[msg.id]?.let { if (it.epoch == epoch && it.raw == msg) return it.out }
        val out = msg.copy(
            text = open(msg.text, TEXT_ENCRYPTED_PENDING, TEXT_UNREADABLE),
            replyToText = open(msg.replyToText, QUOTE_LOCKED, QUOTE_LOCKED),
        )
        shown[msg.id] = Shown(msg, epoch, out)
        return out
    }

    /**
     * Recomputes what's on screen. Decrypting is the expensive part, so it runs off the main thread
     * and already-decrypted messages are reused: a new incoming message costs one decryption, not
     * one per message in the chat. The same Message objects are handed back, which also lets the
     * list skip redrawing bubbles that didn't change.
     *
     * [newRaw] replaces the stored list first; [update] adjusts other state in the same step.
     */
    private suspend fun refresh(
        newRaw: ((List<Message>) -> List<Message>)? = null,
        update: (ChatUiState) -> ChatUiState = { it },
    ) = renderLock.withLock {
        if (newRaw != null) raw = newRaw(raw)
        val source = raw
        val list = withContext(workDispatcher) {
            val out = source.map(::decrypted)
            val ids = source.mapTo(HashSet()) { it.id }
            shown.keys.retainAll(ids)
            out
        }
        _state.update { update(it.copy(messages = list)) }
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
                .catch { e -> _state.update { it.copy(error = e.localizedMessage ?: "Couldn't load messages", isLoading = false) } }
                .collect { window ->
                    refresh({ MessageWindow.merge(it, window) }) { it.copy(error = null, isLoading = false) }
                    myUid?.let { uid ->
                        // Only mark read when the latest message is from someone else, otherwise
                        // every incoming batch (including our own echo) writes to Firestore and
                        // creates a listener feedback loop.
                        val lastFromOther = window.lastOrNull()?.senderId?.let { it != uid } == true
                        if (lastFromOther) markChatReadThrottled(uid)
                    }
                }
        }
    }

    fun onDraftChange(v: String) {
        savedStateHandle[KEY_DRAFT] = v
        _state.update { it.copy(draft = v) }
        reportTyping(v.isNotEmpty() && _state.value.editing == null)
    }

    // ---- Typing indicator -------------------------------------------------------------------

    private var typingSent = false
    private var lastTypingWriteMs = 0L
    private val lastSeenTyping = HashMap<String, Long>()
    private var typingExpiry: kotlinx.coroutines.Job? = null

    /** At most one write every few seconds while typing, one more when you stop. */
    private fun reportTyping(active: Boolean) {
        val uid = myUid ?: return
        val now = clock()
        if (active) {
            if (now - lastTypingWriteMs < TYPING_WRITE_INTERVAL_MS) return
            lastTypingWriteMs = now
            typingSent = true
            viewModelScope.launch { chatRepo.setTyping(chatId, uid, now) }
        } else if (typingSent) {
            typingSent = false
            lastTypingWriteMs = 0L
            viewModelScope.launch { chatRepo.setTyping(chatId, uid, 0L) }
        }
    }

    private fun observeTyping() {
        val me = myUid ?: return
        viewModelScope.launch {
            var first = true
            chatRepo.observeChat(chatId).catch { }.collect { chat ->
                val isFirst = first
                first = false
                val other = chat.typing.entries.firstOrNull { it.key != me } ?: return@collect
                val previous = lastSeenTyping.put(other.key, other.value)
                when {
                    other.value == 0L -> setOtherTyping(false)
                    // The value already stored when the chat opened may be stale: ignore it.
                    isFirst -> Unit
                    // Any later change is a fresh keystroke (no clock comparison across phones).
                    previous != other.value -> showTypingBriefly()
                }
            }
        }
    }

    private fun showTypingBriefly() {
        setOtherTyping(true)
        typingExpiry?.cancel()
        typingExpiry = viewModelScope.launch {
            delay(TYPING_EXPIRES_MS)
            setOtherTyping(false)
        }
    }

    private fun setOtherTyping(v: Boolean) {
        if (!v) typingExpiry?.cancel()
        _state.update { if (it.otherTyping == v) it else it.copy(otherTyping = v) }
    }

    override fun onCleared() {
        reportTyping(false)
        super.onCleared()
    }

    // ---- Editing ----------------------------------------------------------------------------

    fun startEdit(message: Message) {
        if (message.senderId != myUid || isLockedPlaceholder(message.text)) return
        reportTyping(false)
        savedStateHandle[KEY_DRAFT] = message.text
        _state.update { it.copy(editing = message, draft = message.text) }
    }

    fun cancelEdit() {
        savedStateHandle[KEY_DRAFT] = ""
        _state.update { it.copy(editing = null, draft = "") }
    }

    private fun saveEdit(target: Message) {
        val text = _state.value.draft.trim()
        if (text.isEmpty()) return
        if (text.length > MAX_MESSAGE_LENGTH) {
            _state.update { it.copy(userMessage = "Message is too long (max $MAX_MESSAGE_LENGTH)") }
            return
        }
        if (text == target.text) {
            cancelEdit()
            return
        }
        val isLatest = raw.lastOrNull()?.id == target.id
        cancelEdit()
        viewModelScope.launch {
            val ch = channel
            if (ch == null) {
                _state.update { it.copy(userMessage = "Can't edit yet: secure connection isn't ready") }
                return@launch
            }
            if (_state.value.safetyNumberChanged) {
                _state.update { it.copy(userMessage = "Security code changed — verify it before editing.") }
                return@launch
            }
            val body = try {
                E2EEncryption.encrypt(text, ch.key)
            } catch (e: Exception) {
                _state.update { it.copy(userMessage = "Encryption failed") }
                return@launch
            }
            chatRepo.editMessage(chatId, target.id, body, isLatest).onFailure {
                _state.update { it.copy(userMessage = "Couldn't edit message") }
            }
        }
    }

    // ---- Sending ----------------------------------------------------------------------------

    fun send(replyTo: Message? = null) {
        _state.value.editing?.let { return saveEdit(it) }
        val original = _state.value.draft
        val text = original.trim()
        val uid = myUid ?: return
        if (text.isEmpty()) return
        if (text.length > MAX_MESSAGE_LENGTH) {
            _state.update { it.copy(userMessage = "Message is too long (max $MAX_MESSAGE_LENGTH)") }
            return
        }

        val replyId = replyTo?.id.orEmpty()
        val replyPlain = replyTo?.text?.take(MAX_REPLY_QUOTE_CHARS).orEmpty()
        val replySender = replyTo?.senderId.orEmpty()

        // Cleared right away so a double tap can't send twice; restored if anything goes wrong.
        savedStateHandle[KEY_DRAFT] = ""
        reportTyping(false)
        _state.update { it.copy(draft = "") }
        fun restore(message: String) {
            savedStateHandle[KEY_DRAFT] = original
            _state.update { it.copy(draft = original, userMessage = message) }
        }

        viewModelScope.launch {
            // Only wait for the network when there is no channel at all. Otherwise encrypt with
            // the one we have and double-check the key in the background: a send must not sit
            // behind a key lookup.
            if (channel == null) establishChannel()
            val ch = channel
            if (ch == null) {
                // Never fall back to plaintext: this is an end-to-end encrypted chat.
                restore("Can't send yet: ${_state.value.title} hasn't set up secure messaging. Ask them to open WEAVE, then try again.")
                return@launch
            }
            // Hard gate: if the peer's safety number changed and the user hasn't acknowledged it,
            // refuse to send to the unverified key. This blocks a silent key-substitution MITM —
            // the user must confirm the change (ideally compare codes) before any message leaves.
            if (_state.value.safetyNumberChanged) {
                restore("Verify ${_state.value.title}'s security code changed before sending. Open the chat menu to review it.")
                return@launch
            }

            val body: String
            val quote: String
            try {
                body = E2EEncryption.encrypt(text, ch.key)
                // The quote is message content too; it must not sit in Firestore as plaintext.
                quote = if (replyPlain.isEmpty()) "" else E2EEncryption.encrypt(replyPlain, ch.key)
            } catch (e: Exception) {
                restore("Encryption failed")
                return@launch
            }

            // Known from the chat, so the repository doesn't have to read it from the server.
            val recipients = otherUid?.let { listOf(it) }
            chatRepo.sendMessage(chatId, uid, body, replyId, quote, replySender, recipients).onFailure {
                restore("Couldn't send message")
            }

            if (clock() - lastVerifiedAt > REVERIFY_AFTER_MS) {
                launch { establishChannel() }
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
        if (raw.isEmpty() || _state.value.isLoadingMore || !_state.value.hasMoreMessages) return
        val oldestTimestamp = raw.first().timestamp ?: return

        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            chatRepo.loadMoreMessages(chatId, oldestTimestamp, MESSAGE_PAGE_SIZE)
                .onSuccess { olderMessages ->
                    // Merged into the CURRENT list, not the one from before the request started:
                    // messages that arrived meanwhile must not be lost.
                    refresh({ MessageWindow.prepend(it, olderMessages) }) {
                        it.copy(isLoadingMore = false, hasMoreMessages = olderMessages.size >= MESSAGE_PAGE_SIZE)
                    }
                }
                .onFailure {
                    _state.update { it.copy(isLoadingMore = false) }
                }
        }
    }

    private var lastMarkReadMs = 0L

    private fun markChatRead(userId: String) {
        viewModelScope.launch {
            chatRepo.markChatRead(chatId, userId)
        }
    }

    /** At most one read-receipt write per 5s; Firestore listeners make tighter loops wasteful. */
    private fun markChatReadThrottled(userId: String) {
        val now = clock()
        if (now - lastMarkReadMs < MARK_READ_THROTTLE_MS) return
        lastMarkReadMs = now
        markChatRead(userId)
    }

    fun userMessageShown() = _state.update { it.copy(userMessage = null) }
}
