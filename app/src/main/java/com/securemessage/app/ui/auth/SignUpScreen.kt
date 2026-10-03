package com.securemessage.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.securemessage.app.ui.theme.HairlineBorder
import com.securemessage.app.ui.theme.ObsidianCard
import com.securemessage.app.ui.theme.PureWhite

@Composable
fun SignUpScreen(
    onAuthenticated: () -> Unit,
    onNeedsProfile: (reason: String?) -> Unit,
    onBack: () -> Unit,
    vm: AuthViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isAuthenticated) { if (state.isAuthenticated) onAuthenticated() }
    LaunchedEffect(state.needsProfile) { if (state.needsProfile) onNeedsProfile(state.profileReason) }

    AuthScaffold(
        title = "Initialize Node",
        state = state,
        primaryLabel = "Create account",
        onPrimary = vm::signUp,
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(ObsidianCard)
                    .border(1.dp, HairlineBorder, CircleShape),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = PureWhite,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    ) {
        EmailField(state.email, vm::onEmailChange)
        PasswordField(state.password, vm::onPasswordChange)
        ProfileFields(state, vm)
    }
}
