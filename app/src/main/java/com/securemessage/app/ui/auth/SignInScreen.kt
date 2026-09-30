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
fun SignInScreen(
    onAuthenticated: () -> Unit,
    onNeedsProfile: () -> Unit,
    onCreateAccount: () -> Unit,
    vm: AuthViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isAuthenticated) { if (state.isAuthenticated) onAuthenticated() }
    LaunchedEffect(state.needsProfile) { if (state.needsProfile) onNeedsProfile() }

    AuthScaffold(
        title = "Sign in",
        state = state,
        primaryLabel = "Sign in",
        onPrimary = vm::signIn,
        footer = { TextButton(onClick = onCreateAccount) { Text("Create account") } },
    ) {
        EmailField(state.email, vm::onEmailChange)
        PasswordField(state.password, vm::onPasswordChange)
    }
}
