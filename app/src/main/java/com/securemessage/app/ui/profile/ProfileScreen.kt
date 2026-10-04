package com.securemessage.app.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securemessage.app.data.crypto.E2EEncryption
import com.securemessage.app.data.crypto.KeyStoreManager
import com.securemessage.app.ui.common.AppViewModelFactory
import com.securemessage.app.ui.common.MonochromeAvatar
import com.securemessage.app.ui.theme.HairlineBorder
import com.securemessage.app.ui.theme.HairlineBorderSubtle
import com.securemessage.app.ui.theme.ObsidianCard
import com.securemessage.app.ui.theme.ObsidianSurfaceElevated
import com.securemessage.app.ui.theme.ObsidianVoid
import com.securemessage.app.ui.theme.PureBlack
import com.securemessage.app.ui.theme.PureWhite
import com.securemessage.app.ui.theme.TextMuted
import com.securemessage.app.ui.theme.TextPrimary
import com.securemessage.app.ui.theme.TextSecondary

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
    var selectedProfileTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.userNotification) {
        state.userNotification?.let {
            snackbar.showSnackbar(it)
            vm.dismissNotification()
        }
    }

    val effectiveName = state.displayName.ifEmpty { displayName.ifEmpty { "User" } }
    val effectiveEmail = state.email.ifEmpty { email.ifEmpty { "no email" } }
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))

            // Profile Avatar with Camera action
            Box(contentAlignment = Alignment.BottomEnd) {
                MonochromeAvatar(
                    initials = initials,
                    size = 96.dp,
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(PureWhite)
                        .border(2.dp, ObsidianVoid, CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            Toast.makeText(context, "Photo upload coming soon", Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "Set Photo",
                        tint = PureBlack,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // User Display Name & Status
            Text(
                text = effectiveName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = PureWhite,
                letterSpacing = (-0.5).sp,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(PureWhite),
                )
                Text(
                    text = "signed in",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted,
                )
            }

            Spacer(Modifier.height(18.dp))

            // Action Buttons: Set Photo | Edit Info | Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TelegramActionPill(
                    icon = Icons.Filled.CameraAlt,
                    label = "Set Photo",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        Toast.makeText(context, "Photo upload coming soon", Toast.LENGTH_SHORT).show()
                    },
                )
                TelegramActionPill(
                    icon = Icons.Filled.Edit,
                    label = "Edit Info",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        editDisplayName = effectiveName
                        editBio = effectiveBio
                        showEditDialog = true
                    },
                )
                TelegramActionPill(
                    icon = Icons.Filled.Settings,
                    label = "Settings",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                )
            }

            Spacer(Modifier.height(20.dp))

            // Info Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianCard)
                    .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Email
                    Column {
                        Text(
                            text = effectiveEmail,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PureWhite,
                        )
                        Text(
                            text = "User ID",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted,
                        )
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HairlineBorderSubtle))

                    // Username
                    if (state.username.isNotEmpty()) {
                        Column {
                            Text(
                                text = "@${state.username}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PureWhite,
                            )
                            Text(
                                text = "Username",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted,
                            )
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HairlineBorderSubtle))
                    }

                    // Bio
                    if (effectiveBio.isNotEmpty()) {
                        Column {
                            Text(
                                text = effectiveBio,
                                fontSize = 13.sp,
                                color = TextSecondary,
                            )
                            Text(
                                text = "Bio",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted,
                            )
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HairlineBorderSubtle))
                    }

                    // Fingerprint
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = keyFingerprint,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite,
                            )
                            Text(
                                text = "Security code (tap to copy)",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted,
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy Fingerprint",
                            tint = PureWhite,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    val clip = ClipData.newPlainText("Key Fingerprint", keyFingerprint)
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(clip)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    Toast.makeText(context, "Key copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Segmented Control
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(ObsidianCard)
                    .border(1.dp, HairlineBorder, RoundedCornerShape(24.dp))
                    .padding(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selectedProfileTab == 0) PureWhite else ObsidianCard)
                        .clickable { selectedProfileTab = 0 }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Chats",
                        color = if (selectedProfileTab == 0) PureBlack else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selectedProfileTab == 1) PureWhite else ObsidianCard)
                        .clickable { selectedProfileTab = 1 }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Archived chats",
                        color = if (selectedProfileTab == 1) PureBlack else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Empty State
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 12.dp),
            ) {
                Text(
                    text = if (selectedProfileTab == 0) "Nothing here yet" else "Nothing archived yet",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite,
                )
                Text(
                    text = "Archived chats and media will appear here.",
                    fontSize = 12.sp,
                    color = TextMuted,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PureWhite,
                        contentColor = PureBlack,
                    ),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "New Chat",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(Modifier.height(30.dp))

            // Sign out button
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showSignOutConfirm = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, HairlineBorder),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ObsidianCard,
                    contentColor = PureWhite,
                ),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Sign Out",
                        tint = PureWhite,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Sign out",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    )
                }
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

    // Edit Profile Dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = ObsidianCard,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "Edit profile",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = PureWhite,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = editDisplayName,
                        onValueChange = { editDisplayName = it },
                        label = { Text("Display Name", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
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
                    androidx.compose.material3.OutlinedTextField(
                        value = editBio,
                        onValueChange = { editBio = it },
                        label = { Text("Bio", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                        maxLines = 3,
                        shape = RoundedCornerShape(10.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
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
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEditDialog = false
                        // Saved for real through the shared ProfileViewModel (which
                        // reports success/failure). Previously this only toasted.
                        vm.updateProfile(editDisplayName, editBio)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PureWhite,
                        contentColor = PureBlack,
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "Save",
                        color = PureBlack,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(
                        text = "Cancel",
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
            },
        )
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            containerColor = ObsidianCard,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "Sign out?",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = PureWhite,
                )
            },
            text = {
                Text(
                    text = "You will be signed out of this device.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirm = false
                        vm.signOut()
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PureWhite,
                        contentColor = PureBlack,
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "Sign out",
                        color = PureBlack,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text(
                        text = "Cancel",
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
            },
        )
    }
}

@Composable
private fun TelegramActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianCard)
            .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = PureWhite,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = PureWhite,
            )
        }
    }
}
