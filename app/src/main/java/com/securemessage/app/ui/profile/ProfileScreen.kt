package com.securemessage.app.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securemessage.app.data.crypto.E2EEncryption
import com.securemessage.app.data.crypto.KeyStoreManager
import com.securemessage.app.ui.common.AppViewModelFactory
import com.securemessage.app.ui.common.MonochromeAvatar
import com.securemessage.app.ui.theme.HairlineBorder
import com.securemessage.app.ui.theme.ObsidianCard
import com.securemessage.app.ui.theme.ObsidianVoid
import com.securemessage.app.ui.theme.TextMuted
import com.securemessage.app.ui.theme.TextPrimary
import com.securemessage.app.ui.theme.TextSecondary
import com.securemessage.app.ui.theme.TgBlue
import com.securemessage.app.ui.theme.TgErrorRed
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    displayName: String = "",
    email: String = "",
    uid: String = "",
    onSignOut: () -> Unit,
    onEditProfile: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    vm: ProfileViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editDisplayName by remember { mutableStateOf("") }
    var editBio by remember { mutableStateOf("") }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.userNotification) {
        state.userNotification?.let {
            snackbar.showSnackbar(it)
            vm.dismissNotification()
        }
    }

    val effectiveName = state.displayName.ifEmpty { displayName.ifEmpty { "User" } }
    val effectiveEmail = state.email.ifEmpty { email }
    val effectiveUid = state.uid.ifEmpty { uid }
    val effectiveBio = state.bio

    val initials = effectiveName.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .ifEmpty { effectiveName.take(2).uppercase() }

    // Real E2EE identity fingerprint (same safety-number style as the chat header
    // "Check security code" row), not a display hash. Falls back gracefully when
    // the Keystore is unreachable.
    val keyFingerprint = remember(effectiveUid) {
        runCatching {
            val pub = KeyStoreManager.getIdentityPublicKey(context)
            E2EEncryption.computeFingerprint(pub.encoded).chunked(4).joinToString(" : ")
        }.getOrElse { "Unavailable" }
    }

    fun openEdit() {
        editDisplayName = effectiveName
        editBio = effectiveBio
        showEditDialog = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))

            MonochromeAvatar(initials = initials, size = 96.dp)

            Spacer(Modifier.height(14.dp))

            Text(
                text = effectiveName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = (-0.3).sp,
            )
            if (state.username.isNotEmpty()) {
                Text(
                    text = "@${state.username}",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Spacer(Modifier.height(24.dp))

            // Details
            ProfileCard {
                if (effectiveEmail.isNotEmpty()) {
                    InfoRow(icon = Icons.Filled.Email, value = effectiveEmail, label = "Email")
                }
                if (state.username.isNotEmpty()) {
                    InfoRow(icon = Icons.Filled.Person, value = "@${state.username}", label = "Username")
                }
                InfoRow(
                    icon = Icons.Filled.Info,
                    value = effectiveBio.ifEmpty { "Add a few words about yourself" },
                    label = "Bio",
                    valueColor = if (effectiveBio.isEmpty()) TextMuted else TextPrimary,
                    onClick = ::openEdit,
                    onClickLabel = "Edit bio",
                )
            }

            Spacer(Modifier.height(12.dp))

            // Security code: whole row copies
            ProfileCard {
                InfoRow(
                    icon = Icons.Filled.Lock,
                    value = keyFingerprint,
                    label = "Your key fingerprint. Tap to copy. Each chat has its own shared security code.",
                    monospaceValue = true,
                    trailing = {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        val clip = ClipData.newPlainText("Key fingerprint", keyFingerprint)
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(clip)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scope.launch {
                            snackbar.currentSnackbarData?.dismiss()
                            snackbar.showSnackbar("Key fingerprint copied")
                        }
                    },
                    onClickLabel = "Copy key fingerprint",
                )
            }

            Spacer(Modifier.height(12.dp))

            ProfileCard {
                InfoRow(
                    icon = Icons.Filled.Edit,
                    value = "Edit profile",
                    label = null,
                    iconTint = TgBlue,
                    valueColor = TgBlue,
                    onClick = ::openEdit,
                )
                InfoRow(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    value = "Sign out",
                    label = null,
                    iconTint = TgErrorRed,
                    valueColor = TgErrorRed,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showSignOutConfirm = true
                    },
                )
            }

            Spacer(Modifier.height(130.dp))
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp),
        )
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = ObsidianCard,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Edit profile",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = TextPrimary,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editDisplayName,
                        onValueChange = { editDisplayName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = profileFieldColors(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editBio,
                        onValueChange = { editBio = it },
                        label = { Text("Bio") },
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        colors = profileFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = editDisplayName.isNotBlank(),
                    onClick = {
                        showEditDialog = false
                        // Saved for real through the shared ProfileViewModel, which
                        // reports success or failure through the snackbar.
                        vm.updateProfile(editDisplayName, editBio)
                    },
                ) {
                    Text("Save", color = if (editDisplayName.isNotBlank()) TgBlue else TextMuted, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
        )
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            containerColor = ObsidianCard,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Sign out?", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = TextPrimary)
            },
            text = {
                Text(
                    text = "You will be signed out of this device.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirm = false
                        vm.signOut()
                        onSignOut()
                    },
                ) {
                    Text("Sign out", color = TgErrorRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun profileFieldColors() = OutlinedTextFieldDefaults.colors(
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

/** Rounded grouped card that holds a few rows. */
@Composable
private fun ProfileCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianCard),
    ) {
        content()
    }
}

/** One row inside a [ProfileCard]: icon, value, optional caption, optional trailing content. */
@Composable
private fun InfoRow(
    icon: ImageVector,
    value: String,
    label: String?,
    modifier: Modifier = Modifier,
    iconTint: androidx.compose.ui.graphics.Color = TextSecondary,
    valueColor: androidx.compose.ui.graphics.Color = TextPrimary,
    monospaceValue: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = value,
                fontSize = if (monospaceValue) 13.sp else 16.sp,
                fontFamily = if (monospaceValue) FontFamily.Monospace else FontFamily.Default,
                color = valueColor,
            )
            if (label != null) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        trailing?.invoke()
    }
}
