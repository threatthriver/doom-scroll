package com.securemessage.app.ui.users

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securemessage.app.data.model.User
import com.securemessage.app.ui.common.AppViewModelFactory
import com.securemessage.app.ui.common.MonochromeAvatar
import com.securemessage.app.ui.common.MonochromeSearchBar
import com.securemessage.app.ui.theme.ObsidianCard
import com.securemessage.app.ui.theme.ObsidianCardHover
import com.securemessage.app.ui.theme.ObsidianVoid
import com.securemessage.app.ui.theme.PureWhite
import com.securemessage.app.ui.theme.TextSecondary
import com.securemessage.app.ui.theme.TgBlue

@Composable
fun UserSearchScreen(
    onBack: (() -> Unit)? = null,
    onOpenChat: (String) -> Unit,
    showBackButton: Boolean = false,
    modifier: Modifier = Modifier,
    vm: UserSearchViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(state.navigateToChatId) {
        state.navigateToChatId?.let {
            vm.navigationHandled()
            onOpenChat(it)
        }
    }
    LaunchedEffect(state.userMessage) {
        state.userMessage?.let {
            snackbar.showSnackbar(it)
            vm.userMessageShown()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showBackButton && onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(ObsidianCard),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PureWhite,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Text(
                    text = "Contacts",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite,
                    letterSpacing = (-0.5).sp,
                )
            }

            Spacer(Modifier.height(8.dp))

            MonochromeSearchBar(
                query = state.query,
                onQueryChange = vm::onQueryChange,
                placeholder = "Search by username or email",
                onClear = { vm.onQueryChange("") },
            )

            Spacer(Modifier.height(12.dp))

            // Results Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when {
                    state.isLoading -> {
                        CircularProgressIndicator(
                            color = TgBlue,
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(36.dp),
                        )
                    }

                    state.searchError != null -> {
                        UserSearchMessage(
                            icon = Icons.Filled.ErrorOutline,
                            title = "Search failed",
                            body = state.searchError!!,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    state.hasSearched && state.results.isEmpty() -> {
                        UserSearchMessage(
                            icon = Icons.Filled.PersonSearch,
                            title = "No one found",
                            body = "Check the spelling and try again.",
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    !state.hasSearched -> {
                        UserSearchMessage(
                            icon = Icons.Filled.PersonSearch,
                            title = "Find someone to chat with",
                            body = "Type a username or email above.",
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 140.dp),
                        ) {
                            items(state.results, key = { it.uid }) { user ->
                                UserSearchRow(
                                    user = user,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        vm.onUserClick(user)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp),
        )
    }
}

@Composable
private fun UserSearchRow(
    user: User,
    onClick: () -> Unit,
) {
    val initials = user.displayName.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .ifEmpty { user.username.take(2).uppercase() }

    // Flat Telegram-style row: whole row is the tap target (min 56dp high).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Chat with ${user.displayName}", onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MonochromeAvatar(
            initials = initials,
            size = 50.dp,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = PureWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${user.username}",
                fontSize = 14.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Icon(
            imageVector = Icons.Filled.ChatBubbleOutline,
            contentDescription = null,
            tint = TgBlue,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun UserSearchMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(ObsidianCardHover),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TgBlue,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            color = PureWhite,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}
