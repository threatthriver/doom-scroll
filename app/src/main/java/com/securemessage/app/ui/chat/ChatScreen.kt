package com.securemessage.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securemessage.app.data.model.Message
import com.securemessage.app.data.notify.MessageNotifier
import com.securemessage.app.ui.common.AppViewModelFactory
import com.securemessage.app.ui.common.MonochromeAvatar
import com.securemessage.app.ui.common.formatTime
import com.securemessage.app.ui.common.formatDayLabel
import com.securemessage.app.ui.common.isSameDay
import com.securemessage.app.ui.common.TelegramWallpaper
import com.securemessage.app.ui.theme.DockGlassBackground
import com.securemessage.app.ui.theme.TgErrorRed
import com.securemessage.app.ui.theme.TgLink
import com.securemessage.app.ui.theme.HairlineBorder
import com.securemessage.app.ui.theme.HairlineBorderSubtle
import com.securemessage.app.ui.theme.ObsidianCard
import com.securemessage.app.ui.theme.ObsidianCardHover
import com.securemessage.app.ui.theme.ObsidianSurfaceElevated
import com.securemessage.app.ui.theme.ObsidianVoid
import com.securemessage.app.ui.theme.PureBlack
import com.securemessage.app.ui.theme.PureWhite
import com.securemessage.app.ui.theme.TgBlue
import com.securemessage.app.ui.theme.TgBubbleIn
import com.securemessage.app.ui.theme.TgBubbleInTime
import com.securemessage.app.ui.theme.SunsetAmber
import com.securemessage.app.ui.theme.SunsetCoral
import com.securemessage.app.ui.theme.SunsetPink
import com.securemessage.app.ui.theme.TgBubbleOutTime
import com.securemessage.app.ui.theme.TgWallpaperBase
import com.securemessage.app.ui.theme.TextMuted
import com.securemessage.app.ui.theme.TextPrimary
import com.securemessage.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    vm: ChatViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val myUid = vm.myUid ?: ""
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showHeaderMenu by remember { mutableStateOf(false) }
    var selectedMessageForMenu by remember { mutableStateOf<Message?>(null) }
    var replyingToMessage by remember { mutableStateOf<Message?>(null) }
    var showSecurityCode by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Message?>(null) }
    // null = search closed. Search runs on the decrypted messages already loaded on the phone.
    var searchQuery by remember { mutableStateOf<String?>(null) }

    // The list is drawn bottom-up (reverseLayout): index 0 is the NEWEST message. The chat opens
    // on the newest messages with no scrolling at all, and older pages loaded at the top never
    // move what you are looking at.
    val visibleMessages = remember(state.messages, searchQuery) {
        val q = searchQuery?.trim().orEmpty()
        if (q.isEmpty()) state.messages
        else state.messages.filter { !isLockedPlaceholder(it.text) && it.text.contains(q, ignoreCase = true) }
    }
    val rows = remember(visibleMessages) { buildChatRows(visibleMessages) }

    // "Scroll to newest" button: shown whenever the view is not at the bottom.
    val isScrolledUp by remember { derivedStateOf { listState.canScrollBackward } }

    // Follow new messages only if you are already at (or next to) the bottom, or you sent it.
    val newestId = rows.firstOrNull()?.message?.id
    val newestIsMine = rows.firstOrNull()?.message?.senderId == myUid
    LaunchedEffect(newestId) {
        // Only follow a new message to the bottom when the user is already parked at the newest
        // message, or they sent it. The previous `<= 1` threshold would yank the list down while
        // someone was deliberately reading one message up.
        if (newestId != null && (newestIsMine || listState.firstVisibleItemIndex == 0)) {
            listState.animateScrollToItem(0)
        }
    }
    LaunchedEffect(state.userMessage) {
        state.userMessage?.let {
            snackbar.showSnackbar(it)
            vm.userMessageShown()
        }
    }
    // Load older messages when the top of the history (the END of the list) comes into view.
    val nearOldest by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= 0 && last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearOldest, state.messages.size) {
        if (nearOldest && !state.isLoadingMore && state.hasMoreMessages) vm.loadMoreMessages()
    }

    // Presence for the tray notifier: no buzz for the chat on screen, and opening
    // the chat clears its pending notification.
    DisposableEffect(vm.chatId) {
        OpenChatTracker.entered(vm.chatId)
        MessageNotifier.cancelFor(context, vm.chatId)
        onDispose { OpenChatTracker.exited(vm.chatId) }
    }

    val initials = state.title.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .ifEmpty { state.title.take(2).uppercase() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TgWallpaperBase)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Own graphics layer: the pattern is painted once and reused while the list scrolls,
        // instead of being redrawn whenever something above it changes.
        TelegramWallpaper(modifier = Modifier.fillMaxSize().graphicsLayer())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            // Floating glass header (Telegram DM style): back pill | contact pill | menu pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(DockGlassBackground)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onBack()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(DockGlassBackground)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(23.dp))
                        .padding(start = 3.dp, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MonochromeAvatar(
                        initials = initials,
                        size = 40.dp,
                    )
                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            text = state.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = when {
                                state.otherTyping -> "typing…"
                                state.isEncrypted -> "Secure chat"
                                else -> "Waiting for secure connection"
                            },
                            fontSize = 12.sp,
                            color = if (state.otherTyping || state.isEncrypted) TgLink else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(DockGlassBackground)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                        .clickable { showHeaderMenu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Options",
                        tint = TextPrimary,
                    )

                    DropdownMenu(
                        expanded = showHeaderMenu,
                        onDismissRequest = { showHeaderMenu = false },
                        modifier = Modifier.background(ObsidianCard),
                    ) {
                        DropdownMenuItem(
                            text = { Text("Search messages", color = TextPrimary) },
                            onClick = {
                                showHeaderMenu = false
                                searchQuery = ""
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Check security code", color = TextPrimary) },
                            onClick = {
                                showHeaderMenu = false
                                showSecurityCode = true
                            },
                        )
                    }
                }
            }

            // In-chat search (matches decrypted text of the messages loaded on this phone).
            searchQuery?.let { query ->
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(DockGlassBackground)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text("Search in chat", fontSize = 15.sp, color = TextMuted)
                        BasicTextField(
                            value = query,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                            cursorBrush = SolidColor(TgBlue),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        )
                    }
                    if (query.isNotBlank()) {
                        Text(
                            text = "${visibleMessages.size} in view",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    }
                    IconButton(onClick = { searchQuery = null }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close search", tint = TextPrimary)
                    }
                }
                // Be honest that search only covers the messages decrypted on this device so far.
                // Full-history search would need every message pulled and decrypted locally.
                if (query.isNotBlank() && state.hasMoreMessages) {
                    Text(
                        text = "Only searching loaded messages. Scroll up to load older history.",
                        fontSize = 11.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            // The other person's key changed (new phone or reinstall, or tampering): say so.
            if (state.safetyNumberChanged) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianCard)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = TgErrorRed,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Security code changed",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${state.title} may have a new phone. Compare the code with them.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    TextButton(onClick = { showSecurityCode = true }) {
                        Text("View", color = TgBlue, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = vm::acknowledgeSafetyNumber) {
                        Text("OK", color = TextSecondary)
                    }
                }
            }

            // Message Stream (floating header above already provides the separation)
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Newest first: with reverseLayout the first item sits at the bottom.
                    items(rows, key = { it.message.id }) { row ->
                        val msg = row.message
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // One divider per calendar day, above its first message.
                            row.dayLabel?.let { TelegramDateChip(label = it) }

                            TelegramMessageBubble(
                                message = msg,
                                isMine = msg.senderId == myUid,
                                userReaction = msg.reactions[myUid],
                                reactionCount = msg.reactions.size,
                                replySenderName = if (msg.replyToSender == myUid) "You" else state.title,
                                onTap = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedMessageForMenu = msg
                                },
                                onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedMessageForMenu = msg
                                },
                            )
                        }
                    }

                    // First-load spinner: shown until the first snapshot arrives, so the empty
                    // card never flashes on a chat that actually has history.
                    if (state.isLoading && state.messages.isEmpty()) {
                        item(key = "loading") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = TgLink,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                        }
                    }

                    // Load/permission/network error: say something instead of a silent blank list.
                    if (state.error != null && state.messages.isEmpty() && !state.isLoading) {
                        item(key = "error_hint") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.Black.copy(alpha = 0.28f))
                                        .padding(horizontal = 20.dp, vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = TgErrorRed,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Text(
                                        text = "Couldn't load messages",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                    )
                                    Text(
                                        text = state.error ?: "Check your connection and try again.",
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.8f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }

                    if (state.messages.isEmpty() && !state.isLoadingMore && !state.isLoading && state.error == null) {
                        item(key = "empty_hint") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.Black.copy(alpha = 0.28f))
                                        .padding(horizontal = 20.dp, vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = TgLink,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Text(
                                        text = "No messages yet",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                    )
                                    Text(
                                        text = "Say hello. Messages in this chat are end-to-end encrypted.",
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.8f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                    if (state.isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = PureWhite,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }

                }

                if (isScrolledUp) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 16.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ObsidianCard)
                            .border(1.dp, HairlineBorder, CircleShape)
                            .clickable {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Scroll to bottom",
                            tint = PureWhite,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Edit banner: the composer below holds the text being changed.
            state.editing?.let {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianCard)
                        .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Editing message", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TgBlue)
                    IconButton(onClick = vm::cancelEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel edit", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Reply Preview Banner
            if (replyingToMessage != null) {
                val replyMsg = replyingToMessage!!
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianCard)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(34.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(PureWhite),
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            tint = PureWhite,
                            modifier = Modifier.size(16.dp),
                        )
                        Column {
                            Text(
                                text = if (replyMsg.senderId == myUid) "Replying to yourself" else "Replying to ${state.title}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite,
                            )
                            Text(
                                text = replyMsg.text,
                                fontSize = 11.sp,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(
                        onClick = { replyingToMessage = null },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cancel Reply",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // Bottom Input Dock: transparent so the wallpaper shows through, like Telegram DMs
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Note: the blue "Menu" command button is bot-only in Telegram, so a
                    // person-to-person chat intentionally has no such button.
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(DockGlassBackground)
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (state.draft.isEmpty()) {
                                Text(
                                    text = "Message",
                                    fontSize = 16.sp,
                                    color = TextMuted,
                                )
                            }
                            BasicTextField(
                                value = state.draft,
                                onValueChange = vm::onDraftChange,
                                textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
                                cursorBrush = SolidColor(TgBlue),
                                maxLines = 4,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        if (state.draft.isNotBlank()) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            vm.send(replyingToMessage)
                                            replyingToMessage = null
                                        }
                                    },
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // Send button: dimmed and inactive until there is something to send.
                    val canSend = state.draft.isNotBlank()
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (canSend) TgBlue else TgBlue.copy(alpha = 0.4f))
                            .clickable(
                                enabled = canSend,
                                role = androidx.compose.ui.semantics.Role.Button,
                                onClickLabel = "Send message",
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                vm.send(replyingToMessage)
                                replyingToMessage = null
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // Message Context Menu
        selectedMessageForMenu?.let { selectedMsg ->
            TelegramMessageActionDialog(
                message = selectedMsg,
                onDismiss = { selectedMessageForMenu = null },
                onReact = { emoji ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    vm.addReaction(selectedMsg, emoji)
                    selectedMessageForMenu = null
                },
                onReply = {
                    replyingToMessage = selectedMsg
                    selectedMessageForMenu = null
                },
                onEdit = if (selectedMsg.senderId == myUid && !isLockedPlaceholder(selectedMsg.text)) {
                    {
                        replyingToMessage = null
                        vm.startEdit(selectedMsg)
                        selectedMessageForMenu = null
                    }
                } else {
                    null
                },
                onCopy = {
                    val clip = ClipData.newPlainText("Message", selectedMsg.text)
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(clip)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    selectedMessageForMenu = null
                },
                // Only your own messages can be deleted (for everyone), so only offer it there.
                onDelete = if (selectedMsg.senderId == myUid) {
                    {
                        pendingDelete = selectedMsg
                        selectedMessageForMenu = null
                    }
                } else {
                    null
                },
            )
        }

        pendingDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                containerColor = ObsidianCard,
                shape = RoundedCornerShape(20.dp),
                title = { Text("Delete message?", fontWeight = FontWeight.SemiBold, color = TextPrimary) },
                text = {
                    Text(
                        text = "This removes the message for everyone in this chat.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.deleteMessage(target)
                        pendingDelete = null
                    }) { Text("Delete", color = TgErrorRed, fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = TextSecondary) }
                },
            )
        }

        if (showSecurityCode) {
            val code = state.encryptionFingerprint
            AlertDialog(
                onDismissRequest = { showSecurityCode = false },
                containerColor = ObsidianCard,
                shape = RoundedCornerShape(20.dp),
                title = { Text("Security code", fontWeight = FontWeight.SemiBold, color = TextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (code.isNotBlank()) code.chunked(4).joinToString(" ") else "Not available for this chat.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = TextPrimary,
                        )
                        Text(
                            text = "Compare this code with ${state.title} in person or on a call. If it matches on both phones, your messages are private.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                        )
                    }
                },
                confirmButton = {
                    if (code.isNotBlank()) {
                        TextButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Security code", code))
                            showSecurityCode = false
                            coroutineScope.launch { snackbar.showSnackbar("Security code copied") }
                        }) { Text("Copy", color = TgBlue, fontWeight = FontWeight.SemiBold) }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSecurityCode = false }) { Text("Close", color = TextSecondary) }
                },
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TelegramMessageBubble(
    message: Message,
    isMine: Boolean,
    userReaction: String?,
    reactionCount: Int,
    replySenderName: String = "",
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val bubbleShape = if (isMine) {
        RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)
    } else {
        RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)
    }

    // Warm Sunset bubbles: outgoing rides the coral -> amber -> pink sunset ramp on a soft
    // diagonal, incoming is a cozy warm surface. White text reads cleanly on both.
    val bubbleBrush = remember(isMine) {
        if (isMine) {
            Brush.linearGradient(listOf(SunsetCoral, SunsetAmber, SunsetPink))
        } else {
            Brush.linearGradient(listOf(TgBubbleIn, TgBubbleIn))
        }
    }
    val textColor = if (isMine) Color.White else TextPrimary
    val metaColor = if (isMine) TgBubbleOutTime else TgBubbleInTime

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 80.dp, max = 290.dp)
                .clip(bubbleShape)
                .background(bubbleBrush)
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = onLongPress,
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            // Quoted reply context — renders from the stored snapshot, no extra read.
            if (message.replyToText.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(metaColor),
                    )
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = replySenderName.ifEmpty { "Reply" },
                            color = metaColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = message.replyToText,
                            color = textColor.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.Bottom,
            ) {
            val locked = isLockedPlaceholder(message.text)
            Text(
                text = message.text,
                // A message we can't show is styled as a note, not as something the person wrote.
                color = if (locked) textColor.copy(alpha = 0.7f) else textColor,
                fontStyle = if (locked) androidx.compose.ui.text.font.FontStyle.Italic else null,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.padding(bottom = 2.dp),
            ) {
                Text(
                    text = if (message.edited) "edited  ${formatTime(message.timestamp)}" else formatTime(message.timestamp),
                    color = metaColor,
                    fontSize = 11.sp,
                )
                if (isMine) {
                    // The data model has no read receipts, so show a single "sent" tick
                    // instead of the double tick that implied the message was read.
                    Icon(
                        imageVector = Icons.Filled.Done,
                        contentDescription = "Sent",
                        tint = metaColor,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        }

        if (reactionCount > 0) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp, start = if (isMine) 0.dp else 6.dp, end = if (isMine) 6.dp else 0.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (userReaction != null) TgBlue else ObsidianCardHover)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val displayEmoji = message.reactions.values.groupingBy { it }.eachCount()
                        .maxByOrNull { it.value }?.key ?: message.reactions.values.firstOrNull() ?: ""
                    Text(text = displayEmoji, fontSize = 12.sp)
                    if (reactionCount > 1) {
                        Text(
                            text = reactionCount.toString(),
                            fontSize = 10.sp,
                            color = if (userReaction != null) Color.White else TextMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelegramMessageActionDialog(
    message: Message,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)?,
) {
    val reactions = listOf("👍", "❤️", "🔥", "😂", "😮", "😢", "👎")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .clickable(enabled = false) {}
                .padding(24.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(ObsidianCard)
                    .border(1.dp, HairlineBorder, RoundedCornerShape(28.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                reactions.forEach { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 22.sp,
                        modifier = Modifier
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "React with $emoji",
                            ) { onReact(emoji) }
                            .padding(4.dp)
                            .semantics { contentDescription = "React with $emoji" },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianCard)
                    .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp)),
            ) {
                Column {
                    TelegramMenuRow(Icons.AutoMirrored.Filled.Reply, "Reply", onReply)
                    Box(Modifier.fillMaxWidth().height(1.dp).background(HairlineBorderSubtle))
                    TelegramMenuRow(Icons.Filled.ContentCopy, "Copy", onCopy)
                    if (onEdit != null) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(HairlineBorderSubtle))
                        TelegramMenuRow(Icons.Filled.Edit, "Edit", onEdit)
                    }
                    if (onDelete != null) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(HairlineBorderSubtle))
                        TelegramMenuRow(Icons.Filled.Delete, "Delete", onDelete, tint = TgErrorRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun TelegramMenuRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    tint: Color = PureWhite,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = tint,
        )
    }
}

/** Centered day divider pill ("Today", "Yesterday", "October 1") like Telegram's. */
@Composable
private fun TelegramDateChip(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(ObsidianCard.copy(alpha = 0.72f))
                .padding(horizontal = 14.dp, vertical = 5.dp),
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
        }
    }
}
