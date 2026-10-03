package com.securemessage.app.ui.conversations

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.securemessage.app.ui.theme.HairlineBorder
import com.securemessage.app.ui.theme.ObsidianCard
import com.securemessage.app.ui.theme.ObsidianCardHover
import com.securemessage.app.ui.theme.ObsidianVoid
import com.securemessage.app.ui.theme.TgAvatarNeutral
import com.securemessage.app.ui.theme.TgBlue
import com.securemessage.app.ui.theme.TextMuted
import com.securemessage.app.ui.theme.TextPrimary
import com.securemessage.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
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
    var showArchiveSheet by remember { mutableStateOf(false) }
    val archiveSheetState = rememberModalBottomSheetState()

    // Archived chats live in their own vault, so they never appear in the main list.
    val activeChats = remember(state.chats) { state.chats.filterNot { vm.isArchived(it) } }
    val archivedChats = state.archivedChats

    val filteredChats = remember(activeChats, searchQuery) {
        if (searchQuery.isBlank()) {
            activeChats
        } else {
            activeChats.filter { chat ->
                val title = vm.titleFor(chat)
                title.contains(searchQuery, ignoreCase = true) ||
                    chat.lastMessage.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            // Header: logo + title + overflow menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ObsidianCardHover),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "Verified Secure",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        text = "Doom Scroll",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = (-0.5).sp,
                    )
                }

                Box {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showMenu = true
                    }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Options",
                            tint = TextPrimary,
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(ObsidianCard),
                    ) {
                        DropdownMenuItem(
                            text = { Text("New Group", color = TextPrimary) },
                            onClick = {
                                showMenu = false
                                onNewChat()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Saved Messages", color = TextPrimary) },
                            onClick = {
                                showMenu = false
                                Toast.makeText(context, "Saved Messages vault", Toast.LENGTH_SHORT).show()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Settings", color = TextPrimary) },
                            onClick = {
                                showMenu = false
                                Toast.makeText(context, "Settings", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }

            MonochromeSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search Chats",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            Spacer(Modifier.height(6.dp))

            Box(modifier = Modifier.weight(1f)) {
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

                    // True "empty account" state only when nothing exists at all, including
                    // the archive vault. If chats are archived we still render the list so the
                    // vault row stays reachable.
                    activeChats.isEmpty() && archivedChats.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(32.dp),
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
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = TgBlue,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "No conversations yet",
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Tap the button below to start a secure chat.",
                                color = TextSecondary,
                                fontSize = 14.sp,
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 140.dp),
                        ) {
                            // Telegram pinned row: Archived Chats — only when something is archived
                            if (archivedChats.isNotEmpty()) {
                                item(key = "archived_header") {
                                    ArchivedChatsItem(
                                        count = archivedChats.size,
                                        preview = archivedChats.first().lastMessage
                                            .ifEmpty { "Archived chats" },
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            showArchiveSheet = true
                                        },
                                    )
                                }
                            }

                            items(filteredChats, key = { it.id }) { chat ->
                                ConversationRowItem(
                                    chat = chat,
                                    title = vm.titleFor(chat),
                                    unreadCount = vm.unreadCountFor(chat),
                                    isMuted = vm.isMuted(chat),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onOpenChat(chat.id)
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        vm.toggleArchive(chat)
                                        Toast.makeText(
                                            context,
                                            "${vm.titleFor(chat)} archived",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Telegram stacked FABs: quick camera + new message
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        Toast.makeText(context, "Camera capture", Toast.LENGTH_SHORT).show()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = "Quick Camera",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(TgBlue)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNewChat()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "New Message",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        // Archived Chats Vault — only reachable when at least one chat is archived.
        if (showArchiveSheet && archivedChats.isNotEmpty()) {
            ModalBottomSheet(
                onDismissRequest = { showArchiveSheet = false },
                sheetState = archiveSheetState,
                containerColor = ObsidianCard,
                contentColor = TextPrimary,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(top = 10.dp, bottom = 14.dp)
                            .size(width = 38.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(HairlineBorder),
                    )
                },
            ) {
                ArchivedChatsSheet(
                    chats = archivedChats,
                    vm = vm,
                    onOpenChat = { chatId ->
                        showArchiveSheet = false
                        onOpenChat(chatId)
                    },
                    onUnarchive = { chat ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        vm.toggleArchive(chat)
                        Toast.makeText(context, "${vm.titleFor(chat)} unarchived", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }
}

@Composable
private fun ArchivedChatsItem(
    count: Int,
    preview: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(TgAvatarNeutral),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Archive,
                contentDescription = "Archived Chats",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Archived Chats",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = preview,
                fontSize = 14.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (count > 1) {
            Text(
                text = count.toString(),
                fontSize = 13.sp,
                color = TextMuted,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRowItem(
    chat: Chat,
    title: String,
    unreadCount: Int = 0,
    isMuted: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val initials = title.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .ifEmpty { title.take(2).uppercase() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonochromeAvatar(
            initials = initials,
            size = 54.dp,
        )

        Spacer(Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Verified",
                        tint = TgBlue,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    text = formatTime(chat.lastMessageAt).uppercase(),
                    fontSize = 13.sp,
                    color = TextSecondary,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = chat.lastMessage.ifEmpty { "No messages yet" },
                    fontSize = 14.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unreadCount > 0) {
                    Spacer(Modifier.size(8.dp))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) TextMuted else TgBlue),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchivedChatsSheet(
    chats: List<Chat>,
    vm: ConversationsViewModel,
    onOpenChat: (String) -> Unit,
    onUnarchive: (Chat) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Archive,
                contentDescription = null,
                tint = TgBlue,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Archived Chats",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
        ) {
            items(chats, key = { it.id }) { chat ->
                ArchivedChatRow(
                    title = vm.titleFor(chat),
                    subtitle = chat.lastMessage.ifEmpty { "No messages yet" },
                    time = formatTime(chat.lastMessageAt),
                    onClick = { onOpenChat(chat.id) },
                    onUnarchive = { onUnarchive(chat) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArchivedChatRow(
    title: String,
    subtitle: String,
    time: String,
    onClick: () -> Unit,
    onUnarchive: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onUnarchive)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonochromeAvatar(initials = title.take(2).uppercase(), size = 44.dp)
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = time.uppercase(),
                    fontSize = 12.sp,
                    color = TextMuted,
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        Icon(
            imageVector = Icons.Filled.Unarchive,
            contentDescription = "Move out of archive",
            tint = TextSecondary,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ObsidianCardHover)
                .clickable(onClick = onUnarchive)
                .padding(9.dp),
        )
    }
}