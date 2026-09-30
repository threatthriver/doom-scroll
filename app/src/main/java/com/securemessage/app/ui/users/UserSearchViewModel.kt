package com.securemessage.app.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securemessage.app.data.model.User
import com.securemessage.app.data.repo.AuthRepository
import com.securemessage.app.data.repo.ChatRepository
import com.securemessage.app.data.repo.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserSearchUiState(
    val query: String = "",
    val results: List<User> = emptyList(),
    val isLoading: Boolean = false,
    val searchError: String? = null,
    val hasSearched: Boolean = false,
    val navigateToChatId: String? = null,
    val userMessage: String? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class UserSearchViewModel(
    authRepo: AuthRepository,
    private val userRepo: UserRepository,
    private val chatRepo: ChatRepository,
) : ViewModel() {

    private val myUid: String = authRepo.currentUserId.orEmpty()
    private var me: User? = null

    private val _state = MutableStateFlow(UserSearchUiState())
    val state: StateFlow<UserSearchUiState> = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        queryFlow
            .debounce(300)
            .map { it.trim() }
            .distinctUntilChanged()
            .mapLatest { q ->
                if (q.isEmpty()) {
                    _state.update { it.copy(results = emptyList(), hasSearched = false, isLoading = false, searchError = null) }
                    return@mapLatest
                }
                _state.update { it.copy(isLoading = true, searchError = null) }
                userRepo.searchUsers(q, myUid)
                    .onSuccess { users ->
                        _state.update { it.copy(results = users, isLoading = false, hasSearched = true) }
                    }
                    .onFailure {
                        _state.update {
                            it.copy(results = emptyList(), isLoading = false, hasSearched = true, searchError = "Search failed. Try again.")
                        }
                    }
            }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        queryFlow.value = q
    }

    fun onUserClick(user: User) {
        viewModelScope.launch {
            val self = me ?: userRepo.getUser(myUid).getOrNull()?.also { me = it }
            if (self == null) {
                _state.update { it.copy(userMessage = "Couldn't load your profile") }
                return@launch
            }
            chatRepo.openChat(self, user)
                .onSuccess { id -> _state.update { it.copy(navigateToChatId = id) } }
                .onFailure { _state.update { it.copy(userMessage = "Couldn't open chat") } }
        }
    }

    fun navigationHandled() = _state.update { it.copy(navigateToChatId = null) }
    fun userMessageShown() = _state.update { it.copy(userMessage = null) }
}
