package com.securemessage.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securemessage.app.ui.theme.HairlineBorder
import com.securemessage.app.ui.theme.ObsidianCard
import com.securemessage.app.ui.theme.ObsidianCardHover
import com.securemessage.app.ui.theme.ObsidianVoid
import com.securemessage.app.ui.theme.TextMuted
import com.securemessage.app.ui.theme.TextPrimary
import com.securemessage.app.ui.theme.TextSecondary
import com.securemessage.app.ui.theme.TgBlue
import com.securemessage.app.ui.theme.TgErrorRed

/** Shared layout for the sign-in, sign-up and profile screens (same Telegram-blue look as the rest of the app). */
@Composable
internal fun AuthScaffold(
    title: String,
    state: AuthUiState,
    primaryLabel: String,
    onPrimary: () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
    fields: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                navigationIcon()
            }

            Spacer(Modifier.height(16.dp))

            // App identity
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                com.securemessage.app.ui.common.HushOrb(size = 48.dp)
                Column {
                    Text(
                        text = "Hush",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Text(
                        text = "Private chats, kept quiet",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = (-0.5).sp,
            )

            Spacer(Modifier.height(24.dp))

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                fields()
            }

            // Error message (announced to screen readers)
            state.error?.let {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(TgErrorRed.copy(alpha = 0.14f))
                        .padding(14.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Error",
                        tint = TgErrorRed,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = it,
                        color = TextPrimary,
                        fontSize = 14.sp,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = onPrimary,
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TgBlue,
                    contentColor = Color.White,
                    disabledContainerColor = ObsidianCardHover,
                    disabledContentColor = TextMuted,
                ),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = primaryLabel,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                footer()
            }
        }
    }
}

@Composable
private fun authFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = TgBlue,
    unfocusedBorderColor = HairlineBorder,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = TgBlue,
    unfocusedLabelColor = TextMuted,
    cursorColor = TgBlue,
    focusedContainerColor = ObsidianCard,
    unfocusedContainerColor = ObsidianCard,
)

@Composable
internal fun EmailField(value: String, onChange: (String) -> Unit) = OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text("Email", fontSize = 13.sp) },
    singleLine = true,
    shape = RoundedCornerShape(12.dp),
    colors = authFieldColors(),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
    modifier = Modifier.fillMaxWidth(),
)

/**
 * Password input with a show/hide toggle. [onDone] runs when the keyboard's action key is
 * pressed, so the last field on a form can submit it.
 */
@Composable
internal fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    onDone: (() -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("Password", fontSize = 13.sp) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password",
                    tint = TextSecondary,
                )
            }
        },
        colors = authFieldColors(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Display-name + username inputs. [onDone] submits the form from the username field. */
@Composable
internal fun ProfileFields(state: AuthUiState, vm: AuthViewModel, onDone: (() -> Unit)? = null) {
    OutlinedTextField(
        value = state.displayName,
        onValueChange = vm::onDisplayNameChange,
        label = { Text("Your name", fontSize = 13.sp) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = authFieldColors(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.username,
        onValueChange = vm::onUsernameChange,
        label = { Text("Username", fontSize = 13.sp) },
        prefix = { Text("@", color = TextSecondary) },
        supportingText = {
            Text(
                "3–20 characters: a-z, 0-9, _",
                color = TextMuted,
                fontSize = 12.sp,
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = authFieldColors(),
        keyboardOptions = KeyboardOptions(
            imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "Continue with Google": gets an ID token via Credential Manager and hands it to the view model. */
@Composable
internal fun GoogleSignInButton(state: AuthUiState, vm: AuthViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    OutlinedButton(
        onClick = {
            scope.launch {
                when (val r = GoogleSignInHelper.getIdToken(context)) {
                    is GoogleIdResult.Success -> vm.signInWithGoogle(r.idToken)
                    is GoogleIdResult.Failure -> vm.onGoogleError(r.message)
                    GoogleIdResult.Cancelled -> Unit
                }
            }
        },
        enabled = !state.isLoading,
        shape = RoundedCornerShape(26.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, HairlineBorder),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(text = "Continue with Google", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}
