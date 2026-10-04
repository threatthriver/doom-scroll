package com.securemessage.app.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.securemessage.app.ui.common.AppViewModelFactory
import com.securemessage.app.ui.common.DynamicFloatingNavBar
import com.securemessage.app.ui.common.NavTab
import com.securemessage.app.ui.conversations.ConversationsScreen
import com.securemessage.app.ui.conversations.ConversationsViewModel
import com.securemessage.app.ui.profile.ProfileScreen
import com.securemessage.app.ui.theme.ObsidianVoid
import com.securemessage.app.ui.users.UserSearchScreen

@Composable
fun MainScreen(
    onOpenChat: (String) -> Unit,
    onNeedsProfile: () -> Unit,
    onSignedOut: () -> Unit,
    onEditProfile: (String, String) -> Unit = { _, _ -> },
    conversationsVm: ConversationsViewModel = viewModel(factory = AppViewModelFactory.Factory),
    // True when opened from the update notification: land on Settings (tray deep-link).
    openUpdates: Boolean = false,
) {
    var selectedTab by remember { mutableStateOf(if (openUpdates) NavTab.SETTINGS else NavTab.CHATS) }
    // Handles tap-while-open (singleTop): re-route to Settings without resetting nav.
    LaunchedEffect(openUpdates) {
        if (openUpdates) selectedTab = NavTab.SETTINGS
    }
    val convState by conversationsVm.state.collectAsStateWithLifecycle()

    val totalUnreadCount = remember(convState.chats, conversationsVm.myUid) {
        val uid = conversationsVm.myUid ?: return@remember 0
        convState.chats
            .filterNot { it.archived[uid] == true }
            .sumOf { it.unreadCount[uid] ?: 0 }
    }

    val currentUser = FirebaseAuth.getInstance().currentUser
    val displayName = currentUser?.displayName ?: "User"
    val email = currentUser?.email ?: ""
    val uid = currentUser?.uid ?: ""

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
            },
            label = "tab_transition",
            modifier = Modifier.fillMaxSize(),
        ) { tab ->
            when (tab) {
                NavTab.CHATS -> {
                    ConversationsScreen(
                        onNewChat = { selectedTab = NavTab.CONTACTS },
                        onOpenChat = onOpenChat,
                        onNeedsProfile = onNeedsProfile,
                        onSignedOut = onSignedOut,
                        vm = conversationsVm,
                    )
                }

                NavTab.CONTACTS -> {
                    UserSearchScreen(
                        onBack = { selectedTab = NavTab.CHATS },
                        onOpenChat = onOpenChat,
                        showBackButton = false,
                    )
                }

                NavTab.SETTINGS -> {
                    com.securemessage.app.ui.profile.SettingsScreen(
                        onSignOut = {
                            conversationsVm.signOut()
                            onSignedOut()
                        },
                        onEditProfile = onEditProfile,
                    )
                }

                NavTab.PROFILE -> {
                    ProfileScreen(
                        displayName = displayName,
                        email = email,
                        uid = uid,
                        onSignOut = {
                            conversationsVm.signOut()
                            onSignedOut()
                        },
                        onEditProfile = onEditProfile,
                    )
                }
            }
        }

        DynamicFloatingNavBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            badgeCount = totalUnreadCount,
            profileInitials = displayName.ifBlank { email }.trim().take(1).uppercase(),
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
