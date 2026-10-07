package com.securemessage.app.ui.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securemessage.app.data.AuthDestination
import com.securemessage.app.data.AuthGate
import com.securemessage.app.data.repo.AuthRepository
import com.securemessage.app.data.repo.PushTokenRepository
import com.securemessage.app.data.repo.TooManyRequestsException
import com.securemessage.app.data.repo.UserRepository
import com.securemessage.app.data.repo.signOutEverywhere
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VerifyEmailUiState(
    val email: String = "",
    val cooldownSeconds: Int = 0,
    val isLoading: Boolean = false,
    val message: String? = null,
    /** Set once verified: CONVERSATIONS or COMPLETE_PROFILE. */
    val destination: AuthDestination? = null,
    val signedOut: Boolean = false,
)

const val RESEND_COOLDOWN_MS = 60_000L
const val MSG_NOT_VERIFIED = "Not verified yet. Open the link in the email, then try again."
const val MSG_TOO_MANY = "Too many requests. Wait a bit before resending."

class VerifyEmailViewModel(
    savedStateHandle: SavedStateHandle,
    private val authRepo: AuthRepository,
    private val userRepo: UserRepository,
    private val pushRepo: PushTokenRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow(VerifyEmailUiState(email = authRepo.currentEmail.orEmpty()))
    val state: StateFlow<VerifyEmailUiState> = _state.asStateFlow()

    private var cooldownUntil = 0L
    private var ticker: Job? = null

    init {
        // Arriving straight from sign-up: an email was just sent, so start the cooldown.
        if (savedStateHandle.get<Boolean>("sent") == true) startCooldown()
    }

    fun resend() {
        val s = _state.value
        if (s.isLoading || remainingSeconds() > 0) return
        _state.update { it.copy(isLoading = true, message = null) }
        viewModelScope.launch {
            val result = authRepo.sendEmailVerification()
            val msg = when {
                result.isSuccess -> "Verification email sent to ${s.email}."
                result.exceptionOrNull() is TooManyRequestsException -> MSG_TOO_MANY
                else -> "Couldn't send the email. Try again."
            }
            _state.update { it.copy(isLoading = false, message = msg) }
            // Throttle on success and on rate limiting; allow an immediate retry on other errors.
            if (result.isSuccess || result.exceptionOrNull() is TooManyRequestsException) startCooldown()
        }
    }

    fun checkVerified() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, message = null) }
        viewModelScope.launch {
            val verified = authRepo.reloadUser().getOrElse {
                _state.update { it.copy(isLoading = false, message = "Couldn't check right now. Try again.") }
                return@launch
            }
            if (!verified) {
                _state.update { it.copy(isLoading = false, message = MSG_NOT_VERIFIED) }
                return@launch
            }
            val uid = authRepo.currentUserId
            val missing = uid == null || userRepo.getUser(uid).exceptionOrNull() is NoSuchElementException
            val dest = AuthGate.route(signedIn = uid != null, emailVerified = true, hasProfile = !missing)
            _state.update { it.copy(isLoading = false, destination = dest) }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            signOutEverywhere(authRepo, pushRepo)
            _state.update { it.copy(signedOut = true) }
        }
    }

    private fun remainingSeconds(): Int = ((cooldownUntil - clock() + 999) / 1000).toInt().coerceAtLeast(0)

    private fun startCooldown() {
        cooldownUntil = clock() + RESEND_COOLDOWN_MS
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                val left = remainingSeconds()
                _state.update { it.copy(cooldownSeconds = left) }
                if (left == 0) break
                delay(1_000)
            }
        }
    }
}
