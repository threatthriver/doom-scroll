package com.securemessage.app.ui.auth

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securemessage.app.ui.common.AppViewModelFactory
import com.securemessage.app.ui.theme.PureWhite

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
        title = "Authenticate Node",
        state = state,
        primaryLabel = "Connect",
        onPrimary = vm::signIn,
        footer = {
            TextButton(onClick = onCreateAccount) {
                Text(
                    text = "NO CREDENTIALS? INITIALIZE ACCOUNT",
                    color = PureWhite,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
        },
    ) {
        EmailField(state.email, vm::onEmailChange)
        PasswordField(state.password, vm::onPasswordChange)
    }
}
