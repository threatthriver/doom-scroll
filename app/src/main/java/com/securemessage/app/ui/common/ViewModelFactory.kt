package com.securemessage.app.ui.common

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.securemessage.app.SecureMessageApp
import com.securemessage.app.di.AppContainer
import com.securemessage.app.ui.auth.AuthViewModel
import com.securemessage.app.ui.auth.VerifyEmailViewModel
import com.securemessage.app.ui.chat.ChatViewModel
import com.securemessage.app.ui.conversations.ConversationsViewModel
import com.securemessage.app.ui.users.UserSearchViewModel

private fun CreationExtras.container(): AppContainer =
    (this[APPLICATION_KEY] as SecureMessageApp).container

object AppViewModelFactory {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val c = container()
            AuthViewModel(createSavedStateHandle(), c.authRepository, c.userRepository, c.pushTokenRepository)
        }
        initializer {
            val c = container()
            VerifyEmailViewModel(createSavedStateHandle(), c.authRepository, c.userRepository, c.pushTokenRepository)
        }
        initializer {
            val c = container()
            ConversationsViewModel(c.authRepository, c.chatRepository, c.userRepository, c.pushTokenRepository)
        }
        initializer {
            val c = container()
            UserSearchViewModel(c.authRepository, c.userRepository, c.chatRepository)
        }
        initializer {
            val c = container()
            ChatViewModel(
                createSavedStateHandle(),
                c.chatRepository,
                c.authRepository,
                c.chatSessionManager,
                c.safetyNumbers,
            )
        }
        initializer {
            val c = container()
            com.securemessage.app.ui.profile.ProfileViewModel(c.authRepository, c.userRepository)
        }
    }
}
