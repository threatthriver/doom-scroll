package com.securemessage.app.ui.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securemessage.app.ui.common.AppViewModelFactory
import com.securemessage.app.ui.theme.PureWhite
import com.securemessage.app.ui.theme.TextMuted

@Composable
fun CompleteProfileScreen(
    onAuthenticated: () -> Unit,
    onSignedOut: () -> Unit,
    vm: AuthViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isAuthenticated) { if (state.isAuthenticated) onAuthenticated() }

    AuthScaffold(
        title = "Set up your profile",
        state = state,
        primaryLabel = "Continue",
        onPrimary = vm::completeProfile,
        footer = {
            TextButton(onClick = { vm.signOut(); onSignedOut() }) {
                Text(
                    text = "Sign out",
                    color = PureWhite,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
        },
    ) {
        Text(
            text = "Pick a unique username so others can find and message you.",
            fontSize = 13.sp,
            color = TextMuted,
        )
        Spacer(Modifier.height(8.dp))
        ProfileFields(state, vm)
    }
}
