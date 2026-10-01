package com.securemessage.app.ui.conversations

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securemessage.app.data.model.Chat
import com.securemessage.app.ui.common.AppViewModelFactory
import com.securemessage.app.ui.common.MonochromeAvatar
import com.securemessage.app.ui.common.MonochromeSearchBar
import com.securemessage.app.ui.common.formatTime
import com.securemessage.app.ui.theme.DockGlassBackground
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
fun ConversationsScreen(
    onNewChat: () -> Unit,
    onOpenChat: (String) -> Unit,
    onNeedsProfile: () -> Unit,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ConversationsViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    LaunchedEffect(state.needsProfile) { if (state.needsProfile) onNeedsProfile() }

    var searchQuery by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var selectedFolderIndex by remember { mutableIntStateOf(0) }
    val folders = remember { listOf("All Chats", "Direct", "Channels", "Encrypted") }

    val filteredChats = remember(state.chats, searchQuery, selectedFolderIndex) {
        val base = if (searchQuery.isBlank()) {
            state.chats
        } else {
            state.chats.filter { chat ->
                val title = vm.titleFor(chat)
                title.contains(searchQuery, ignoreCase = true) ||
                    chat.lastMessage.contains(searchQuery, ignoreCase = true)
            }
        }
        when (selectedFolderIndex) {
            1 -> base.filter { it.participants.size <= 2 }
            2 -> base.filter { it.participants.size > 2 }
            else -> base
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

            // Telegram Header: App Title, Verified Badge & 3-dot overflow menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PureWhite),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "Verified Secure",
                            tint = PureBlack,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = "Doom Scroll",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        color = PureWhite,
                        letterSpacing = (-0.5).sp,
                    )
                }

                Box {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showMenu = true
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Options",
                            tint = PureWhite,
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .background(ObsidianCard)
                            .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp)),
                    ) {
                        DropdownMenuItem(
                            text = { Text("New Group", color = PureWhite, fontSize = 13.sp) },
                            onClick = {
                                showMenu = false
                                onNewChat()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Saved Messages", color = PureWhite, fontSize = 13.sp) },
                            onClick = {
                                showMenu = false
                                Toast.makeText(context, "Saved Messages vault", Toast.LENGTH_SHORT).show()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Toggle Contrast", color = PureWhite, fontSize = 13.sp) },
                            onClick = {
                                showMenu = false
                                Toast.makeText(context, "High contrast active", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Telegram Stories / Active Transmitters Carousel
            LazyRow(
                contentPadding = PaddingValues(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // My Story item
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            Toast.makeText(context, "Create Story", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(ObsidianCard)
                                    .border(1.5.dp, HairlineBorder, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CameraAlt,
                                    contentDescription = "My Story",
                                    tint = PureWhite,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(PureWhite),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Add Story",
                                    tint = PureBlack,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "My Story",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted,
                        )
                    }
                }

                // Peer stories
                items(state.chats.take(6), key = { "story_${it.id}" }) { chat ->
                    val title = vm.titleFor(chat)
                    val initials = title.split(" ")
                        .mapNotNull { it.firstOrNull()?.toString() }
                        .take(2)
                        .joinToString("")
                        .ifEmpty { title.take(2).uppercase() }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenChat(chat.id)
                        },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .border(2.dp, PureWhite, CircleShape)
                                .padding(2.dp),
                        ) {
                            MonochromeAvatar(
                                initials = initials,
                                size = 46.dp,
                                showOnlineBadge = true,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = title.take(8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PureWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Telegram Rounded Pill Search Bar
            MonochromeSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search Chats",
                onClear = { searchQuery = "" },
            )

            Spacer(Modifier.height(12.dp))

            // Telegram Chat Folders Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                items(folders.indices.toList()) { index ->
                    val isSelected = index == selectedFolderIndex
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) PureWhite else ObsidianCard)
                            .border(1.dp, if (isSelected) PureWhite else HairlineBorder, RoundedCornerShape(20.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedFolderIndex = index
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = folders[index],
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) PureBlack else TextMuted,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Content Area: Archived chats + Conversations List
            Box(modifier = Modifier.weight(1f)) {
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

                    filteredChats.isEmpty() && state.chats.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(ObsidianCard)
                                    .border(1.dp, HairlineBorder, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = PureWhite,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "NO CONVERSATIONS YET",
                                color = PureWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Tap the [+] button below to connect with secure peers.",
                                color = TextMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 130.dp),
                        ) {
                            // Telegram Pinned Item: Archived Chats
                            item {
                                ArchivedChatsItem(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        Toast.makeText(context, "Archived Chats Vault", Toast.LENGTH_SHORT).show()
                                    },
                                )
                            }

                            items(filteredChats, key = { it.id }) { chat ->
                                ConversationRowItem(
                                    chat = chat,
                                    title = vm.titleFor(chat),
                                    unreadCount = if (chat.lastMessage.isNotEmpty()) 1 else 0,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onOpenChat(chat.id)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Telegram Stacked Floating Action Buttons (Camera + New Chat)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Camera FAB
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(ObsidianCard)
                    .border(1.dp, HairlineBorder, CircleShape)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        Toast.makeText(context, "Camera capture", Toast.LENGTH_SHORT).show()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = "Quick Camera",
                    tint = PureWhite,
                    modifier = Modifier.size(20.dp),
                )
            }

            // Telegram New Message FAB (High Contrast Pure White)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(PureWhite)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNewChat()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "New Message",
                    tint = PureBlack,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun ArchivedChatsItem(
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianCard)
            .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(ObsidianVoid)
                    .border(1.dp, HairlineBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Archive,
                    contentDescription = "Archived Chats",
                    tint = PureWhite,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Archived Chats",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite,
                )
                Text(
                    text = "Encrypted archives & dormant frequencies",
                    fontSize = 11.sp,
                    color = TextMuted,
                )
            }
        }
    }
}

@Composable
private fun ConversationRowItem(
    chat: Chat,
    title: String,
    unreadCount: Int = 0,
    onClick: () -> Unit,
) {
    val initials = title.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .ifEmpty { title.take(2).uppercase() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianCard)
            .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MonochromeAvatar(
                initials = initials,
                size = 50.dp,
                showOnlineBadge = true,
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        Text(
                            text = title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Verified",
                            tint = PureWhite,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Text(
                        text = formatTime(chat.lastMessageAt).uppercase(),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = chat.lastMessage.ifEmpty { "Transmission established" },
                        fontSize = 13.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(PureWhite)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = unreadCount.toString(),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = PureBlack,
                            )
                        }
                    }
                }
            }
        }
    }
}
