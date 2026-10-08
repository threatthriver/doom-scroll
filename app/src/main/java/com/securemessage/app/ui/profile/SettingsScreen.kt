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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securemessage.app.data.PrivacySettings
import com.securemessage.app.ui.common.AppViewModelFactory
import com.securemessage.app.ui.common.openNotificationSettings

private val W = com.securemessage.app.ui.theme.Weave
private val HairlineBorder = W.Hairline
private val HairlineBorderSubtle = W.HairlineSoft
private val ObsidianCard = W.Surface
private val ObsidianVoid = W.Bg
private val PureWhite = W.Ink
private val TextPrimary = W.Ink
private val TextSecondary = W.InkBody
private val TextMuted = W.InkMuted
private val TgBlue = W.Indigo
private val TgErrorRed = W.Error
private val SettingsIconBlue = W.Blue
private val SettingsIconGreen = Color(0xFF3FB27F)
private val SettingsIconOrange = Color(0xFFE59A3C)
private val SettingsIconRed = Color(0xFFE0566B)
private val SettingsIconIndigo = W.IndigoDeep

@Composable
fun SettingsScreen(
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ProfileViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    var screenSecurity by remember { mutableStateOf(PrivacySettings.isScreenSecurityEnabled(context)) }
    var notifPreview by remember { mutableStateOf(PrivacySettings.isNotificationPreviewEnabled(context)) }
    fun toggleNotifPreview() {
        notifPreview = !notifPreview
        PrivacySettings.setNotificationPreviewEnabled(context, notifPreview)
    }
    var appLock by remember { mutableStateOf(PrivacySettings.isAppLockEnabled(context)) }
    fun toggleAppLock() {
        val activity = context as? com.securemessage.app.MainActivity
        if (!appLock && activity != null && !activity.canAuthenticate()) {
            android.widget.Toast.makeText(
                context, "Set up a screen lock or fingerprint in your phone settings first", android.widget.Toast.LENGTH_LONG,
            ).show()
            return
        }
        appLock = !appLock
        PrivacySettings.setAppLockEnabled(context, appLock)
    }
    fun toggleScreenSecurity() {
        screenSecurity = !screenSecurity
        PrivacySettings.setScreenSecurityEnabled(context, screenSecurity)
        // Apply immediately to the running window, not just on the next resume.
        (context as? com.securemessage.app.MainActivity)?.applyScreenSecurity()
    }

    LaunchedEffect(state.userNotification) {
        state.userNotification?.let {
            snackbar.showSnackbar(it)
            vm.dismissNotification()
        }
    }

    // Re-validate the cached APK each time Settings opens (installer may have run,
    // or the file may have been cleared) and pass context so silent checks cool down.
    LaunchedEffect(Unit) {
        vm.refreshCachedApk(context.applicationContext)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            com.securemessage.app.ui.weave.WeaveTopBar("Settings", onBack = onBack)
            Spacer(Modifier.height(8.dp))

            SectionTitle("Settings")
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
                        icon = Icons.Filled.Lock,
                        title = "Encryption key",
                        subtitle = "See your key fingerprint",
                        iconColor = SettingsIconGreen,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showKey = true
                        },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Filled.Notifications,
                        title = "Message text in notifications",
                        subtitle = "Show who wrote and what (hidden on lock screen)",
                        iconColor = SettingsIconGreen,
                        onClick = { toggleNotifPreview() },
                        toggleState = notifPreview,
                        trailing = { Switch(checked = notifPreview, onCheckedChange = null) },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Filled.Lock,
                        title = "App lock",
                        subtitle = "Ask for fingerprint, face or screen lock",
                        iconColor = SettingsIconOrange,
                        onClick = { toggleAppLock() },
                        toggleState = appLock,
                        trailing = { Switch(checked = appLock, onCheckedChange = null) },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Filled.Visibility,
                        title = "Screen security",
                        subtitle = "Block screenshots and hide app preview",
                        iconColor = SettingsIconIndigo,
                        onClick = { toggleScreenSecurity() },
                        toggleState = screenSecurity,
                        trailing = {
                            Switch(
                                checked = screenSecurity,
                                onCheckedChange = null,
                            )
                        },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Filled.Notifications,
                        title = "Notifications",
                        subtitle = if (com.securemessage.app.data.notify.MessageNotifier.messageAlertsEnabled(context))
                            "On. Sounds and alerts in system settings"
                        else "OFF. Tap to turn on message alerts",
                        iconColor = SettingsIconRed,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            openNotificationSettings(context)
                        },
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            SectionTitle("App updates")
            Spacer(Modifier.height(8.dp))

            val apkFile = state.downloadedApkFile
            val hasDownloadedApk = apkFile != null && apkFile.exists()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianCard)
                    .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SettingsIconBlue),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SystemUpdate,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        // weight(1f) is what keeps the button from being squeezed
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "WEAVE updates",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PureWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = when {
                                    state.updateAvailable -> "New version ${state.updateVersion} is ready"
                                    else -> "You have version ${state.currentVersion}"
                                },
                                fontSize = 13.sp,
                                color = if (state.updateAvailable) TgBlue else TextMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        val buttonLabel = when {
                            hasDownloadedApk -> "Install"
                            state.updateAvailable -> "Update"
                            else -> "Check"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (state.updateAvailable || hasDownloadedApk) TgBlue else PureWhite)
                                .clickable(enabled = !state.isCheckingUpdates && !state.isDownloadingUpdate) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    when {
                                        hasDownloadedApk -> vm.installDownloadedApk(context)
                                        state.updateAvailable -> vm.openUpdateDialog()
                                        else -> vm.checkForUpdates(silent = false, appContext = context.applicationContext)
                                    }
                                }
                                .heightIn(min = 38.dp)
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.isCheckingUpdates) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(
                                    text = buttonLabel,
                                    color = if (state.updateAvailable || hasDownloadedApk) Color.White else Color.Black,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }

                    // Live download progress, visible even if the dialog was closed
                    if (state.isDownloadingUpdate) {
                        LinearProgressIndicator(
                            progress = { state.downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = TgBlue,
                            trackColor = HairlineBorder,
                        )
                        Text(
                            text = "Downloading... ${state.downloadedBytesText}",
                            fontSize = 12.sp,
                            color = TextMuted,
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // Sign out
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianCard)
                    .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showSignOutConfirm = true
                    }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = TgErrorRed,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "Sign out",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TgErrorRed,
                )
            }

            Spacer(Modifier.height(130.dp))
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp),
        )
    }

    // Update dialog: opens only when the user asks (tapping "Check" or "Update")
    if (state.showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { vm.dismissUpdateDialog() },
            containerColor = ObsidianCard,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "New version available",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = PureWhite,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Version ${state.updateVersion}",
                            color = TgBlue,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (state.apkSizeMb.isNotEmpty()) {
                            Text(
                                text = state.apkSizeMb,
                                color = TextMuted,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    if (state.updateNotes.isNotEmpty()) {
                        Text(
                            text = "What's new",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                        )
                        Text(
                            text = state.updateNotes,
                            fontSize = 14.sp,
                            color = TextSecondary,
                            lineHeight = 20.sp,
                            modifier = Modifier
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                    if (state.isDownloadingUpdate) {
                        LinearProgressIndicator(
                            progress = { state.downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = TgBlue,
                            trackColor = HairlineBorder,
                        )
                        Text(
                            text = state.downloadedBytesText.ifEmpty { "Downloading..." },
                            fontSize = 12.sp,
                            color = TextMuted,
                        )
                    }
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (state.isDownloadingUpdate) HairlineBorder else TgBlue)
                        .clickable(enabled = !state.isDownloadingUpdate) {
                            vm.startUpdateDownload(context)
                        }
                        .heightIn(min = 40.dp)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.isDownloadingUpdate) "Downloading..." else "Download and install",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissUpdateDialog) {
                    Text(
                        text = if (state.isDownloadingUpdate) "Hide" else "Later",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            },
        )
    }

    if (showKey) {
        val fingerprint = remember {
            runCatching {
                val pub = com.securemessage.app.data.crypto.KeyStoreManager.getIdentityPublicKey(context)
                com.securemessage.app.data.crypto.E2EEncryption.computeFingerprint(pub.encoded).chunked(4).joinToString(" ")
            }.getOrElse { "Unavailable" }
        }
        AlertDialog(
            onDismissRequest = { showKey = false },
            containerColor = ObsidianCard,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Your key fingerprint", fontWeight = FontWeight.Bold, color = PureWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(fingerprint, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 14.sp, color = TextPrimary)
                    Text("Each chat also has its own security code under the chat menu.", fontSize = 13.sp, color = TextSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Key fingerprint", fingerprint))
                    showKey = false
                }) { Text("Copy", color = TgBlue, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { showKey = false }) { Text("Close", color = TextSecondary) } },
        )
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            containerColor = ObsidianCard,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Sign out?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = PureWhite,
                )
            },
            text = {
                Text(
                    text = "You will be signed out of this phone.",
                    fontSize = 15.sp,
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
                    Text(
                        text = "Sign out",
                        color = TgErrorRed,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text(
                        text = "Cancel",
                        color = TextSecondary,
                        fontSize = 15.sp,
                    )
                }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = TgBlue,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 68.dp)
            .height(1.dp)
            .background(HairlineBorderSubtle),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconColor: Color,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    /** When non-null, the whole row acts as a switch: it reports Role.Switch + on/off state to
     *  screen readers, and the trailing Switch should be decorative (onCheckedChange = null) so a
     *  single tap doesn't toggle twice. */
    toggleState: Boolean? = null,
) {
    val rowSemantics = if (toggleState != null) {
        Modifier.semantics {
            role = Role.Switch
            stateDescription = if (toggleState) "On" else "Off"
        }
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(rowSemantics)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Telegram-style coloured rounded-square icon tile
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = PureWhite,
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = TextMuted,
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
