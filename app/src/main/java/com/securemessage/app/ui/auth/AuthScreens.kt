package com.securemessage.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securemessage.app.ui.theme.HairlineBorder
import com.securemessage.app.ui.theme.HairlineBorderBright
import com.securemessage.app.ui.theme.ObsidianCard
import com.securemessage.app.ui.theme.ObsidianVoid
import com.securemessage.app.ui.theme.PureBlack
import com.securemessage.app.ui.theme.PureWhite
import com.securemessage.app.ui.theme.TextMuted
import com.securemessage.app.ui.theme.TextPrimary
import com.securemessage.app.ui.theme.TextSecondary

/** Monochromatic Brutalist layout for Auth Screens. */
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
            // Navigation icon row if present
            Box(modifier = Modifier.fillMaxWidth()) {
                navigationIcon()
            }

            Spacer(Modifier.height(16.dp))

            // App Identity & Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(PureWhite),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Chat",
                        tint = PureBlack,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column {
                    Text(
                        text = "CHAT",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        color = PureWhite,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(
                        text = "Private chats, made simple",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif,
                        color = TextMuted,
                        letterSpacing = 0.2.sp,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = title.uppercase(),
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                color = PureWhite,
                letterSpacing = (-0.5).sp,
            )

            Spacer(Modifier.height(24.dp))

            // Form Fields
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                fields()
            }

            // Error display card
            state.error?.let {
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ObsidianCard)
                        .border(1.dp, PureWhite, RoundedCornerShape(8.dp))
                        .padding(14.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Alert",
                            tint = PureWhite,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = it,
                            color = PureWhite,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // Primary stark white button
            Button(
                onClick = onPrimary,
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PureWhite,
                    contentColor = PureBlack,
                    disabledContainerColor = HairlineBorderBright,
                    disabledContentColor = TextMuted,
                ),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = PureBlack,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = primaryLabel.uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp,
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
internal fun EmailField(value: String, onChange: (String) -> Unit) = OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text("Email", fontSize = 13.sp) },
    singleLine = true,
    shape = RoundedCornerShape(10.dp),
    colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = PureWhite,
        unfocusedBorderColor = HairlineBorder,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedLabelColor = PureWhite,
        unfocusedLabelColor = TextMuted,
        cursorColor = PureWhite,
        focusedContainerColor = ObsidianCard,
        unfocusedContainerColor = ObsidianCard,
    ),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
    modifier = Modifier.fillMaxWidth(),
)

@Composable
internal fun PasswordField(value: String, onChange: (String) -> Unit) = OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text("Password", fontSize = 13.sp) },
    singleLine = true,
    shape = RoundedCornerShape(10.dp),
    visualTransformation = PasswordVisualTransformation(),
    colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = PureWhite,
        unfocusedBorderColor = HairlineBorder,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedLabelColor = PureWhite,
        unfocusedLabelColor = TextMuted,
        cursorColor = PureWhite,
        focusedContainerColor = ObsidianCard,
        unfocusedContainerColor = ObsidianCard,
    ),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    modifier = Modifier.fillMaxWidth(),
)

@Composable
internal fun ProfileFields(state: AuthUiState, vm: AuthViewModel) {
    OutlinedTextField(
        value = state.displayName,
        onValueChange = vm::onDisplayNameChange,
        label = { Text("Your name", fontSize = 13.sp) },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PureWhite,
            unfocusedBorderColor = HairlineBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedLabelColor = PureWhite,
            unfocusedLabelColor = TextMuted,
            cursorColor = PureWhite,
            focusedContainerColor = ObsidianCard,
            unfocusedContainerColor = ObsidianCard,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.username,
        onValueChange = vm::onUsernameChange,
        label = { Text("Username", fontSize = 13.sp) },
        supportingText = {
            Text(
                "3–20 characters: a-z, 0-9, _",
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PureWhite,
            unfocusedBorderColor = HairlineBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedLabelColor = PureWhite,
            unfocusedLabelColor = TextMuted,
            cursorColor = PureWhite,
            focusedContainerColor = ObsidianCard,
            unfocusedContainerColor = ObsidianCard,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
