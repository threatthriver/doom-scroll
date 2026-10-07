package com.securemessage.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securemessage.app.ui.common.AppViewModelFactory
import com.securemessage.app.ui.theme.ObsidianCard
import com.securemessage.app.ui.theme.PureWhite

@Composable
fun SignUpScreen(
    onAuthenticated: () -> Unit,
    onNeedsProfile: (reason: String?) -> Unit,
    onNeedsVerification: () -> Unit,
    onBack: () -> Unit,
    vm: AuthViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isAuthenticated) { if (state.isAuthenticated) onAuthenticated() }
    LaunchedEffect(state.needsProfile) { if (state.needsProfile) onNeedsProfile(state.profileReason) }
    LaunchedEffect(state.needsVerification) { if (state.needsVerification) onNeedsVerification() }

    AuthScaffold(
        title = "Create your account",
        state = state,
        primaryLabel = "Create account",
        onPrimary = vm::signUp,
        footer = { GoogleSignInButton(state, vm) },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ObsidianCard),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = PureWhite,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    ) {
        EmailField(state.email, vm::onEmailChange)
        PasswordField(state.password, vm::onPasswordChange)
        ProfileFields(state, vm, onDone = vm::signUp)
    }
}
