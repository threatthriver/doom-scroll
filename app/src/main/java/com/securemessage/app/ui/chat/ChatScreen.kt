package com.securemessage.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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
import com.securemessage.app.ui.theme.TgBubbleOut
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
    var showAttachmentSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

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
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (listState.firstVisibleItemIndex <= 2 && !state.isLoadingMore && state.hasMoreMessages) {
            vm.loadMoreMessages()
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
            .background(TgWallpaperBase)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        TelegramWallpaper(modifier = Modifier.fillMaxSize())
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
                            text = if (state.isEncrypted) "Secure chat" else "Not encrypted",
                            fontSize = 12.sp,
                            color = if (state.isEncrypted) TgLink else TgErrorRed,
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
                            text = { Text("Check security code", color = TextPrimary) },
                            onClick = {
                                showHeaderMenu = false
                                Toast.makeText(context, "Security code: ${state.encryptionFingerprint}", Toast.LENGTH_LONG).show()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Clear History", color = TextPrimary) },
                            onClick = {
                                showHeaderMenu = false
                                Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }

            // Message Stream (floating header above already provides the separation)
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
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

                    itemsIndexed(state.messages, key = { _, m -> m.id }) { index, msg ->
                        val isMine = msg.senderId == myUid
                        val userReaction = msg.reactions[myUid]
                        val reactionCount = msg.reactions.size
                        val previous = state.messages.getOrNull(index - 1)
                        val startsNewDay = previous == null ||
                            !isSameDay(previous.timestamp, msg.timestamp)

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // One divider per calendar day, instead of a single hard-coded "TODAY"
                            if (startsNewDay) {
                                TelegramDateChip(label = formatDayLabel(msg.timestamp))
                            }

                            TelegramMessageBubble(
                                message = msg,
                                isMine = isMine,
                                userReaction = userReaction,
                                reactionCount = reactionCount,
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
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                Toast.makeText(context, "Emoji picker coming soon", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SentimentSatisfiedAlt,
                                contentDescription = "Emoji",
                                tint = TextSecondary,
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp),
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
                                            vm.send()
                                            replyingToMessage = null
                                        }
                                    },
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showAttachmentSheet = true
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AttachFile,
                                contentDescription = "Attach",
                                tint = TextSecondary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(TgBlue)
                            .clickable {
                                if (state.draft.isNotBlank()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    vm.send()
                                    replyingToMessage = null
                                } else {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    Toast.makeText(context, "Voice notes coming soon", Toast.LENGTH_SHORT).show()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (state.draft.isNotBlank()) Icons.AutoMirrored.Filled.Send else Icons.Filled.Mic,
                            contentDescription = if (state.draft.isNotBlank()) "Send" else "Record Audio",
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
                onCopy = {
                    val clip = ClipData.newPlainText("Message", selectedMsg.text)
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(clip)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    selectedMessageForMenu = null
                },
                onDelete = {
                    vm.deleteMessage(selectedMsg)
                    selectedMessageForMenu = null
                },
            )
        }

        // Attachment Bottom Sheet
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
                        Toast.makeText(context, "$action coming soon", Toast.LENGTH_SHORT).show()
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
    userReaction: String?,
    reactionCount: Int,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val bubbleShape = if (isMine) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }

    // Telegram bubbles: outgoing blue->violet gradient, incoming dark slate, white text on both
    val bubbleBrush = if (isMine) {
        Brush.horizontalGradient(listOf(TgBubbleOut, Color(0xFF4F8DEB)))
    } else {
        Brush.horizontalGradient(listOf(TgBubbleIn, TgBubbleIn))
    }
    val textColor = Color.White
    val metaColor = if (isMine) TgBubbleOutTime else TgBubbleInTime

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        Row(
            modifier = Modifier
                .widthIn(min = 80.dp, max = 290.dp)
                .clip(bubbleShape)
                .background(bubbleBrush)
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = onLongPress,
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = message.text,
                color = textColor,
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
                    text = formatTime(message.timestamp),
                    color = metaColor,
                    fontSize = 11.sp,
                )
                if (isMine) {
                    Icon(
                        imageVector = Icons.Filled.DoneAll,
                        contentDescription = "Read",
                        tint = metaColor,
                        modifier = Modifier.size(14.dp),
                    )
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
                        text = "Take a photo or record video",
                        fontSize = 11.sp,
                        color = TextMuted,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        val options = listOf(
            Triple(Icons.Filled.PhotoLibrary, "Gallery", "Photos & Videos"),
            Triple(Icons.Filled.InsertDriveFile, "File", "Documents up to 2GB"),
            Triple(Icons.Filled.LocationOn, "Location", "Share live coordinates"),
            Triple(Icons.Filled.MusicNote, "Audio", "Music and voice notes"),
            Triple(Icons.Filled.BarChart, "Poll", "Create a poll"),
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
                .background(Color.Black.copy(alpha = 0.28f))
                .padding(horizontal = 14.dp, vertical = 5.dp),
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}
