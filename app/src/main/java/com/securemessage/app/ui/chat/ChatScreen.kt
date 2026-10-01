package com.securemessage.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.securemessage.app.ui.common.AppViewModelFactory
import com.securemessage.app.ui.common.MonochromeAvatar
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
    var showAttachmentSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Store reactions for messages locally in state
    val messageReactions = remember { mutableStateMapOf<String, String>() }

    val isScrolledUp by remember {
        derivedStateOf { listState.canScrollForward }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }
    LaunchedEffect(state.userMessage) {
        state.userMessage?.let {
            snackbar.showSnackbar(it)
            vm.userMessageShown()
        }
    }

    val initials = state.title.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .ifEmpty { state.title.take(2).uppercase() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            // Telegram Chat Header: Back button, Avatar, User Name, online status, 3-dot overflow
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onBack()
                        },
                        modifier = Modifier.size(38.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PureWhite,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    MonochromeAvatar(
                        initials = initials,
                        size = 40.dp,
                        showOnlineBadge = true,
                    )

                    Column {
                        Text(
                            text = state.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(PureWhite),
                            )
                            Text(
                                text = "online",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted,
                            )
                        }
                    }
                }

                Box {
                    IconButton(onClick = { showHeaderMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Options",
                            tint = PureWhite,
                        )
                    }

                    DropdownMenu(
                        expanded = showHeaderMenu,
                        onDismissRequest = { showHeaderMenu = false },
                        modifier = Modifier
                            .background(ObsidianCard)
                            .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp)),
                    ) {
                        DropdownMenuItem(
                            text = { Text("Search in Chat", color = PureWhite, fontSize = 13.sp) },
                            onClick = {
                                showHeaderMenu = false
                                Toast.makeText(context, "Search ready", Toast.LENGTH_SHORT).show()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Mute Notifications", color = PureWhite, fontSize = 13.sp) },
                            onClick = {
                                showHeaderMenu = false
                                Toast.makeText(context, "Notifications muted", Toast.LENGTH_SHORT).show()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Clear History", color = PureWhite, fontSize = 13.sp) },
                            onClick = {
                                showHeaderMenu = false
                                Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }

            // Hairline separator
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(HairlineBorderSubtle),
            )

            // Message Stream with Grouped Date Pill
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Telegram Floating Date Pill
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(ObsidianSurfaceElevated)
                                    .border(1.dp, HairlineBorderSubtle, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = "TODAY",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted,
                                    letterSpacing = 1.sp,
                                )
                            }
                        }
                    }

                    items(state.messages, key = { it.id }) { msg ->
                        val isMine = msg.senderId == myUid
                        val reaction = messageReactions[msg.id]

                        TelegramMessageBubble(
                            message = msg,
                            isMine = isMine,
                            reaction = reaction,
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

                // Scroll to bottom FAB
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
                                    listState.animateScrollToItem(state.messages.lastIndex)
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

            // Telegram Reply Preview Banner (Anchored right above input)
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

            // Telegram Bottom Input Dock: Smiley, Text Field, Attachment Paperclip, Mic / Send Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianVoid)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Integrated Pill Container for Emoji, Input, and Paperclip
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(26.dp))
                            .background(ObsidianCard)
                            .border(1.dp, HairlineBorder, RoundedCornerShape(26.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Emoji / Smiley Button
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                Toast.makeText(context, "Stickers and Emojis", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SentimentSatisfiedAlt,
                                contentDescription = "Emoji & Stickers",
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        // Message Text Field
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (state.draft.isEmpty()) {
                                Text(
                                    text = "Message",
                                    fontSize = 14.sp,
                                    color = TextMuted,
                                )
                            }
                            BasicTextField(
                                value = state.draft,
                                onValueChange = vm::onDraftChange,
                                textStyle = TextStyle(
                                    color = PureWhite,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.SansSerif,
                                ),
                                cursorBrush = SolidColor(PureWhite),
                                maxLines = 4,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        if (state.draft.isNotBlank()) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            vm.send()
                                            replyingToMessage = null
                                        }
                                    },
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        // Telegram Paperclip Attachment Button
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showAttachmentSheet = true
                            },
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AttachFile,
                                contentDescription = "Attach Media",
                                tint = PureWhite,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    // Dynamic Send / Mic Record Button
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(PureWhite)
                            .clickable {
                                if (state.draft.isNotBlank()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    vm.send()
                                    replyingToMessage = null
                                } else {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    Toast.makeText(context, "Hold to record audio note", Toast.LENGTH_SHORT).show()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.draft.isNotBlank()) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = PureBlack,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = "Record Audio",
                                tint = PureBlack,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // Telegram Message Context Menu Modal with Reaction Strip
        selectedMessageForMenu?.let { selectedMsg ->
            TelegramMessageActionDialog(
                message = selectedMsg,
                onDismiss = { selectedMessageForMenu = null },
                onReact = { emoji ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    messageReactions[selectedMsg.id] = emoji
                    selectedMessageForMenu = null
                },
                onReply = {
                    replyingToMessage = selectedMsg
                    selectedMessageForMenu = null
                },
                onCopy = {
                    val clip = ClipData.newPlainText("Message", selectedMsg.text)
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(clip)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    selectedMessageForMenu = null
                },
                onForward = {
                    Toast.makeText(context, "Forward message", Toast.LENGTH_SHORT).show()
                    selectedMessageForMenu = null
                },
                onPin = {
                    Toast.makeText(context, "Message pinned", Toast.LENGTH_SHORT).show()
                    selectedMessageForMenu = null
                },
                onDelete = {
                    Toast.makeText(context, "Message deleted", Toast.LENGTH_SHORT).show()
                    selectedMessageForMenu = null
                },
            )
        }

        // Telegram Attachment Bottom Sheet
        if (showAttachmentSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAttachmentSheet = false },
                sheetState = sheetState,
                containerColor = ObsidianCard,
                contentColor = PureWhite,
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
                TelegramAttachmentSheetContent(
                    onActionSelected = { action ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        Toast.makeText(context, "Selected: $action", Toast.LENGTH_SHORT).show()
                        showAttachmentSheet = false
                    },
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TelegramMessageBubble(
    message: Message,
    isMine: Boolean,
    reaction: String?,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    // Telegram-style asymmetric bubble corners: subtle tail on bottom-right for outgoing, bottom-left for incoming
    val bubbleShape = if (isMine) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }

    val bubbleBg = if (isMine) PureWhite else ObsidianCard
    val textColor = if (isMine) PureBlack else PureWhite
    val metaColor = if (isMine) TextMuted else TextMuted

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 80.dp, max = 290.dp)
                .clip(bubbleShape)
                .background(bubbleBg)
                .border(1.dp, if (isMine) Color.Transparent else HairlineBorder, bubbleShape)
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = onLongPress,
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Column {
                Text(
                    text = message.text,
                    color = textColor,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = formatTime(message.timestamp).lowercase(),
                        color = metaColor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    if (isMine) {
                        Icon(
                            imageVector = Icons.Filled.DoneAll,
                            contentDescription = "Read",
                            tint = PureBlack,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        }

        // Reaction badge displayed beneath the bubble
        reaction?.let { r ->
            Box(
                modifier = Modifier
                    .padding(top = 2.dp, start = if (isMine) 0.dp else 6.dp, end = if (isMine) 6.dp else 0.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianCard)
                    .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(text = r, fontSize = 12.sp)
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
    onForward: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    val reactions = listOf("🖤", "🔥", "⚡", "👍", "👎", "💀", "🔒")

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
            // Telegram Floating Reaction Strip
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
                            .clickable { onReact(emoji) }
                            .padding(4.dp),
                    )
                }
            }

            // Telegram Action Popup Card
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
                    Box(Modifier.fillMaxWidth().height(1.dp).background(HairlineBorderSubtle))
                    TelegramMenuRow(Icons.AutoMirrored.Filled.Send, "Forward", onForward)
                    Box(Modifier.fillMaxWidth().height(1.dp).background(HairlineBorderSubtle))
                    TelegramMenuRow(Icons.Filled.PushPin, "Pin", onPin)
                    Box(Modifier.fillMaxWidth().height(1.dp).background(HairlineBorderSubtle))
                    TelegramMenuRow(Icons.Filled.Delete, "Delete", onDelete)
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = PureWhite,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = PureWhite,
        )
    }
}

@Composable
private fun TelegramAttachmentSheetContent(
    onActionSelected: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Quick Action: "Open Camera"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ObsidianVoid)
                .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
                .clickable { onActionSelected("Open Camera") }
                .padding(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PureWhite),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "Camera",
                        tint = PureBlack,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column {
                    Text(
                        text = "Open Camera",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite,
                    )
                    Text(
                        text = "Take a photo or record encrypted video",
                        fontSize = 11.sp,
                        color = TextMuted,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Telegram Attachment Category Tabs
        val options = listOf(
            Triple(Icons.Filled.PhotoLibrary, "Gallery", "Photos & Videos"),
            Triple(Icons.Filled.InsertDriveFile, "File", "Documents up to 2GB"),
            Triple(Icons.Filled.LocationOn, "Location", "Share live coordinates"),
            Triple(Icons.Filled.MusicNote, "Audio", "Music and voice notes"),
            Triple(Icons.Filled.BarChart, "Poll", "Create encrypted poll"),
            Triple(Icons.Filled.Person, "Contact", "Share vCard contact"),
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            options.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowItems.forEach { (icon, title, subtitle) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ObsidianVoid)
                                .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp))
                                .clickable { onActionSelected(title) }
                                .padding(12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = title,
                                    tint = PureWhite,
                                    modifier = Modifier.size(22.dp),
                                )
                                Column {
                                    Text(
                                        text = title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PureWhite,
                                    )
                                    Text(
                                        text = subtitle,
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}
