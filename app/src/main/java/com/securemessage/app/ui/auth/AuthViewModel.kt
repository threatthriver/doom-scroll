package com.securemessage.app.ui.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securemessage.app.data.EMAIL_REGEX
import com.securemessage.app.data.USERNAME_REGEX
import com.securemessage.app.data.model.User
import com.securemessage.app.data.normalizeUsername
import com.securemessage.app.data.repo.AuthRepository
import com.securemessage.app.data.repo.UserRepository
import com.securemessage.app.data.repo.UsernameTakenException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val username: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAuthenticated: Boolean = false,
    val needsProfile: Boolean = false,
    val profileReason: String? = null,
)

const val REASON_USERNAME_TAKEN = "username_taken"
const val REASON_PROFILE_FAILED = "profile_failed"
const val MSG_USERNAME_TAKEN = "Username already taken"
const val MSG_PROFILE_FAILED = "Couldn't create your profile. Try again."

class AuthViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepo: AuthRepository,
    private val userRepo: UserRepository,
    private val emailValidator: (String) -> Boolean = { EMAIL_REGEX.matches(it) },
) : ViewModel() {

    private val _state = MutableStateFlow(
        AuthUiState(
            error = when (savedStateHandle.get<String>("reason")) {
                REASON_USERNAME_TAKEN -> MSG_USERNAME_TAKEN
                REASON_PROFILE_FAILED -> MSG_PROFILE_FAILED
                else -> null
            },
        ),
    )
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onEmailChange(v: String) = _state.update { it.copy(email = v, error = null) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, error = null) }
    fun onDisplayNameChange(v: String) = _state.update { it.copy(displayName = v, error = null) }
    fun onUsernameChange(v: String) = _state.update { it.copy(username = v, error = null) }

    private fun credentialError(s: AuthUiState): String? = when {
        !emailValidator(s.email.trim()) -> "Enter a valid email"
        s.password.length < 6 -> "Password must be at least 6 characters"
        else -> null
    }

    private fun profileError(s: AuthUiState): String? {
        val name = s.displayName.trim()
        return when {
            name.isEmpty() -> "Display name is required"
            name.length > 50 -> "Display name must be at most 50 characters"
            !USERNAME_REGEX.matches(normalizeUsername(s.username)) ->
                "Username must be 3–20 characters: a-z, 0-9 or _"
            else -> null
        }
    }

    private fun failWith(msg: String) = _state.update { it.copy(error = msg) }

    fun signIn() {
        val s = _state.value
        if (s.isLoading) return
        credentialError(s)?.let { return failWith(it) }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            authRepo.signIn(s.email.trim(), s.password)
                .onSuccess { uid ->
                    val profile = userRepo.getUser(uid)
                    val missing = profile.exceptionOrNull() is NoSuchElementException
                    _state.update {
                        it.copy(isLoading = false, needsProfile = missing, isAuthenticated = !missing)
                    }
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message()) } }
        }
    }

    fun signUp() {
        val s = _state.value
        if (s.isLoading) return
        (credentialError(s) ?: profileError(s))?.let { return failWith(it) }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val email = s.email.trim()
            val uid = authRepo.signUp(email, s.password).getOrElse { e ->
                _state.update { it.copy(isLoading = false, error = e.message()) }
                return@launch
            }
            val user = User(uid, normalizeUsername(s.username), s.displayName.trim(), email.lowercase())
            userRepo.createProfile(user)
                .onSuccess { _state.update { it.copy(isLoading = false, isAuthenticated = true) } }
                .onFailure { e ->
                    val reason = if (e is UsernameTakenException) REASON_USERNAME_TAKEN else REASON_PROFILE_FAILED
                    _state.update { it.copy(isLoading = false, needsProfile = true, profileReason = reason) }
                }
        }
    }

    fun completeProfile() {
        val s = _state.value
        if (s.isLoading) return
        profileError(s)?.let { return failWith(it) }
        val uid = authRepo.currentUserId ?: return failWith("You're signed out. Sign in again.")
        val email = authRepo.currentEmail.orEmpty().lowercase()
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            userRepo.createProfile(User(uid, normalizeUsername(s.username), s.displayName.trim(), email))
                .onSuccess { _state.update { it.copy(isLoading = false, isAuthenticated = true) } }
                .onFailure { e ->
                    val msg = if (e is UsernameTakenException) MSG_USERNAME_TAKEN else MSG_PROFILE_FAILED
                    _state.update { it.copy(isLoading = false, error = msg) }
                }
        }
    }

    fun signOut() = authRepo.signOut()

    private fun Throwable.message() = localizedMessage ?: "Something went wrong"
}
