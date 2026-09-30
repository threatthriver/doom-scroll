package com.securemessage.app.ui.auth

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securemessage.app.ui.common.AppViewModelFactory

@Composable
fun CompleteProfileScreen(
    onAuthenticated: () -> Unit,
    onSignedOut: () -> Unit,
    vm: AuthViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isAuthenticated) { if (state.isAuthenticated) onAuthenticated() }

    AuthScaffold(
        title = "Complete profile",
        state = state,
        primaryLabel = "Continue",
        onPrimary = vm::completeProfile,
        footer = {
            TextButton(onClick = { vm.signOut(); onSignedOut() }) { Text("Sign out") }
        },
    ) {
        Text("Pick a username so other people can find you.")
        ProfileFields(state, vm)
    }
}
