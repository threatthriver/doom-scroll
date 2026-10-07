package com.securemessage.app.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securemessage.app.ui.common.AppViewModelFactory
import com.securemessage.app.ui.theme.TgBlue

@Composable
fun SignInScreen(
    onAuthenticated: () -> Unit,
    onNeedsProfile: () -> Unit,
    onNeedsVerification: () -> Unit,
    onCreateAccount: () -> Unit,
    vm: AuthViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isAuthenticated) { if (state.isAuthenticated) onAuthenticated() }
    LaunchedEffect(state.needsProfile) { if (state.needsProfile) onNeedsProfile() }
    LaunchedEffect(state.needsVerification) { if (state.needsVerification) onNeedsVerification() }

    AuthScaffold(
        title = "Welcome back",
        state = state,
        primaryLabel = "Sign in",
        onPrimary = vm::signIn,
        footer = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GoogleSignInButton(state, vm)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCreateAccount) {
                    Text(
                        text = "New here? Create an account",
                        color = TgBlue,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
            }
        },
    ) {
        EmailField(state.email, vm::onEmailChange)
        PasswordField(state.password, vm::onPasswordChange, onDone = vm::signIn)
    }
}
