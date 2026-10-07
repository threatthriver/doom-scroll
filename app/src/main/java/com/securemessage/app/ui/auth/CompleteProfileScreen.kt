package com.securemessage.app.ui.auth

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securemessage.app.ui.common.AppViewModelFactory
import com.securemessage.app.ui.theme.TextSecondary
import com.securemessage.app.ui.theme.TgBlue

@Composable
fun CompleteProfileScreen(
    onAuthenticated: () -> Unit,
    onNeedsVerification: () -> Unit,
    onSignedOut: () -> Unit,
    vm: AuthViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isAuthenticated) { if (state.isAuthenticated) onAuthenticated() }
    LaunchedEffect(state.needsVerification) { if (state.needsVerification) onNeedsVerification() }

    AuthScaffold(
        title = "Set up your profile",
        state = state,
        primaryLabel = "Continue",
        onPrimary = vm::completeProfile,
        footer = {
            TextButton(onClick = { vm.signOut(onSignedOut) }) {
                Text(
                    text = "Sign out",
                    color = TgBlue,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                )
            }
        },
    ) {
        Text(
            text = "Pick a unique username so others can find and message you.",
            fontSize = 14.sp,
            color = TextSecondary,
        )
        ProfileFields(state, vm, onDone = vm::completeProfile)
    }
}
