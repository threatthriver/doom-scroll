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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securemessage.app.data.model.User
import com.securemessage.app.ui.common.AppViewModelFactory
import com.securemessage.app.ui.common.MonochromeAvatar
import com.securemessage.app.ui.common.MonochromeSearchBar
import com.securemessage.app.ui.theme.HairlineBorder
import com.securemessage.app.ui.theme.HairlineBorderSubtle
import com.securemessage.app.ui.theme.ObsidianCard
import com.securemessage.app.ui.theme.ObsidianVoid
import com.securemessage.app.ui.theme.PureBlack
import com.securemessage.app.ui.theme.PureWhite
import com.securemessage.app.ui.theme.TextMuted
import com.securemessage.app.ui.theme.TextPrimary
import com.securemessage.app.ui.theme.TextSecondary

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
            Spacer(Modifier.height(16.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showBackButton && onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ObsidianCard)
                            .border(1.dp, HairlineBorder, CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PureWhite,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Column {
                    Text(
                        text = "NETWORK DIRECTORY",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.2.sp,
                    )
                    Text(
                        text = "CONTACTS",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        color = PureWhite,
                        letterSpacing = (-0.5).sp,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Search Bar matching Telegram Contacts search
            MonochromeSearchBar(
                query = state.query,
                onQueryChange = vm::onQueryChange,
                placeholder = "Search people by username...",
                onClear = { vm.onQueryChange("") },
            )

            Spacer(Modifier.height(16.dp))

            // Dividing hairline
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(HairlineBorderSubtle),
            )

            // Results Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when {
                    state.isLoading -> {
                        CircularProgressIndicator(
                            color = PureWhite,
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(36.dp),
                        )
                    }

                    state.searchError != null -> {
                        Text(
                            text = state.searchError!!,
                            color = PureWhite,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                        )
                    }

                    state.hasSearched && state.results.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "NO NODES LOCATED",
                                color = PureWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Verify the exact email or username prefix.",
                                color = TextMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    !state.hasSearched -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(ObsidianCard)
                                    .border(1.dp, HairlineBorder, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Sensors,
                                    contentDescription = null,
                                    tint = PureWhite,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "GLOBAL DIRECTORY ACTIVE",
                                color = PureWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Type an email or handle above to discover encrypted peers.",
                                color = TextMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(top = 10.dp, bottom = 120.dp),
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianCard)
            .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MonochromeAvatar(
                initials = initials,
                size = 46.dp,
                showOnlineBadge = true,
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite,
                )
                Text(
                    text = "@${user.username}",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                )
            }

            // Direct connect pill button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(PureWhite)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChatBubbleOutline,
                        contentDescription = null,
                        tint = PureBlack,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = "CHAT",
                        color = PureBlack,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp,
                    )
                }
            }
        }
    }
}
