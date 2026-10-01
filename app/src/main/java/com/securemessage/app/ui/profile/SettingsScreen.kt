package com.securemessage.app.ui.profile

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
fun SettingsScreen(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ProfileViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    var showSignOutConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.userNotification) {
        state.userNotification?.let {
            snackbar.showSnackbar(it)
            vm.dismissNotification()
        }
    }

    val displayName = state.displayName.ifEmpty { "Transmitter" }
    val initials = displayName.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .ifEmpty { displayName.take(2).uppercase() }

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
        ) {
            Spacer(Modifier.height(16.dp))

            // Telegram Settings Top Profile Summary Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MonochromeAvatar(
                    initials = initials,
                    size = 80.dp,
                    showOnlineBadge = true,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = displayName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite,
                )
                if (state.username.isNotEmpty()) {
                    Text(
                        text = "@${state.username}",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted,
                    )
                }
                Text(
                    text = state.email.ifEmpty { "Active Cryptographic Node" },
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Telegram Settings Group 1: Account & Chat Settings
            Text(
                text = "SETTINGS // PREFERENCES",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianCard)
                    .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp)),
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Filled.Person,
                        title = "Account",
                        subtitle = "Username, email, cryptographic bio",
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                    )
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HairlineBorderSubtle))
                    SettingsRow(
                        icon = Icons.Filled.Palette,
                        title = "Chat Settings",
                        subtitle = "Monochromatic theme, brutalist bubble styles",
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                    )
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HairlineBorderSubtle))
                    SettingsRow(
                        icon = Icons.Filled.Lock,
                        title = "Privacy & Security",
                        subtitle = "End-to-end encryption, secret chat protocols",
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                    )
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HairlineBorderSubtle))
                    SettingsRow(
                        icon = Icons.Filled.Notifications,
                        title = "Notifications & Sounds",
                        subtitle = "Haptic alerts, message banners",
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                    )
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HairlineBorderSubtle))
                    SettingsRow(
                        icon = Icons.Filled.Storage,
                        title = "Data and Storage",
                        subtitle = "Encrypted local cache, automatic downloads",
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                    )
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HairlineBorderSubtle))
                    SettingsRow(
                        icon = Icons.Filled.Folder,
                        title = "Chat Folders",
                        subtitle = "Sort transmissions into folders",
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Telegram Settings Group 2: GitHub Auto-Update System
            Text(
                text = "UPDATES // REPOSITORY TELEMETRY",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianCard)
                    .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ObsidianVoid)
                                .border(1.dp, HairlineBorder, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SystemUpdate,
                                contentDescription = "Auto Update",
                                tint = PureWhite,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column {
                            Text(
                                text = "App Updates (GitHub)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite,
                            )
                            Text(
                                text = "Installed: v${state.currentVersion} • threatthriver/doom-scroll",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted,
                            )
                        }
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            vm.checkForUpdates(silent = false)
                        },
                        enabled = !state.isCheckingUpdates,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PureWhite,
                            contentColor = PureBlack,
                        ),
                        modifier = Modifier.height(36.dp),
                    ) {
                        if (state.isCheckingUpdates) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = PureBlack,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                text = "CHECK",
                                color = PureBlack,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Sign Out
            OutlinedButtonRow(
                icon = Icons.AutoMirrored.Filled.Logout,
                text = "SIGN OUT // TERMINATE SESSION",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showSignOutConfirm = true
                },
            )

            // Clearance for dynamic floating navbar
            Spacer(Modifier.height(130.dp))
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp),
        )
    }

    // In-App Update Modal
    if (state.showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { if (!state.isDownloadingUpdate) vm.dismissUpdateDialog() },
            containerColor = ObsidianCard,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "SYSTEM UPDATE AVAILABLE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = PureWhite,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "PROTOCOL ${state.updateVersion}",
                            fontFamily = FontFamily.Monospace,
                            color = PureWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        if (state.apkSizeMb.isNotEmpty()) {
                            Text(
                                text = state.apkSizeMb,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    if (state.updateNotes.isNotEmpty()) {
                        Text(
                            text = state.updateNotes,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 17.sp,
                        )
                    }
                    if (state.isDownloadingUpdate) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { state.downloadProgress },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = PureWhite,
                            trackColor = HairlineBorder,
                        )
                        Text(
                            text = state.downloadedBytesText.ifEmpty { "DOWNLOADING: ${(state.downloadProgress * 100).toInt()}%" },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TextMuted,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.startUpdateDownload(context) },
                    enabled = !state.isDownloadingUpdate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PureWhite,
                        contentColor = PureBlack,
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = if (state.isDownloadingUpdate) "DOWNLOADING..." else "DOWNLOAD & INSTALL",
                        color = PureBlack,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
            },
            dismissButton = {
                if (!state.isDownloadingUpdate) {
                    TextButton(onClick = vm::dismissUpdateDialog) {
                        Text(
                            text = "LATER",
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
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
                    text = "SIGN OUT?",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = PureWhite,
                )
            },
            text = {
                Text(
                    text = "You will be disconnected from this encrypted node session.",
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
                        text = "SIGN OUT",
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
                        text = "CANCEL",
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
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ObsidianVoid)
                .border(1.dp, HairlineBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PureWhite,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = PureWhite,
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = TextMuted,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun OutlinedButtonRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
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
                imageVector = icon,
                contentDescription = null,
                tint = PureWhite,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
        }
    }
}
