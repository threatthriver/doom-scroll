package com.securemessage.app.ui.weave

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securemessage.app.data.weave.Circle
import com.securemessage.app.ui.common.formatTime
import com.securemessage.app.ui.conversations.ConversationsViewModel
import com.securemessage.app.ui.conversations.previewText
import com.securemessage.app.ui.theme.Weave

/** 7. Messages — focused, human conversations. Real E2EE chats from [ConversationsViewModel]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessagesScreen(
    vm: ConversationsViewModel,
    circles: List<Circle>,
    onOpenChat: (String) -> Unit,
    onNewChat: () -> Unit,
    onOpenCircles: () -> Unit,
    onNeedsProfile: () -> Unit,
    onNeedsVerification: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.needsProfile) { if (state.needsProfile) onNeedsProfile() }
    LaunchedEffect(state.needsVerification) { if (state.needsVerification) onNeedsVerification() }

    var tab by rememberSaveable { mutableStateOf("All") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val chats = remember(state.chats, tab, query, state.archivedChats) {
        val base = if (tab == "Archived") state.archivedChats else state.chats.filterNot { vm.isArchived(it) }
        base.filter { c ->
            (tab != "Unread" || vm.unreadCountFor(c) > 0) &&
                (query.isBlank() || vm.titleFor(c).contains(query, ignoreCase = true))
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Messages", color = Weave.Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconCircle(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, if (searching) "Close search" else "Search", {
                searching = !searching; if (!searching) query = ""
            }, background = Color.Transparent)
            IconCircle(Icons.Rounded.Add, "New chat", onNewChat, background = Color.Transparent)
        }
        if (searching) {
            WeaveInput(query, { query = it }, "Search people", Modifier.padding(horizontal = 20.dp), leading = Icons.Rounded.Search)
            Spacer(Modifier.height(10.dp))
        }
        val tabs = buildList {
            add("All"); add("People"); add("Circles"); add("Unread")
            if (state.archivedCount > 0) add("Archived")
        }
        com.securemessage.app.ui.common.NotificationsOffBanner(Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        ChipRow(tabs, tab, { tab = it })
        Spacer(Modifier.height(8.dp))

        LazyColumn(contentPadding = PaddingValues(bottom = 110.dp)) {
            if (tab == "Circles" || (tab == "All" && query.isBlank())) {
                items(circles.filter { query.isBlank() || it.name.contains(query, true) }, key = { "c" + it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth().combinedClickable(onClick = onOpenCircles).padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircleGlyph(c, 48)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, color = Weave.Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("${c.size} ${if (c.size == 1) "person" else "people"}", color = Weave.InkMuted, fontSize = 13.sp)
                        }
                    }
                }
            }
            if (tab != "Circles") {
                if (chats.isEmpty() && !state.isLoading) {
                    item {
                        EmptyHint(
                            if (query.isNotBlank()) "No matches" else "No conversations yet",
                            if (query.isNotBlank()) "Try a different name." else "Start a private, encrypted chat with someone you know.",
                            action = if (query.isBlank()) "Start a chat" else null, onAction = onNewChat,
                        )
                    }
                }
                items(chats, key = { it.id }) { chat ->
                    val title = vm.titleFor(chat)
                    val unread = vm.unreadCountFor(chat)
                    Row(
                        Modifier.fillMaxWidth()
                            .combinedClickable(
                                onClick = { onOpenChat(chat.id) },
                                onLongClick = { vm.toggleArchive(chat) },
                                onLongClickLabel = if (vm.isArchived(chat)) "Unarchive" else "Archive",
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WeaveAvatar(title, size = 48.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(title, color = Weave.Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                if (vm.isMuted(chat)) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Rounded.NotificationsOff, "Muted", tint = Weave.InkMuted, modifier = Modifier.size(13.dp))
                                }
                                Spacer(Modifier.weight(1f))
                                Text(formatTime(chat.lastMessageAt), color = Weave.InkMuted, fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    previewText(chat.lastMessage, "No messages yet"),
                                    color = if (unread > 0) Weave.InkBody else Weave.InkMuted, fontSize = 13.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                )
                                if (unread > 0) {
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        Modifier.size(20.dp).background(if (vm.isMuted(chat)) Weave.InkFaint else Weave.Online, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(if (unread > 99) "99+" else "$unread", color = Weave.Bg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (circles.isEmpty()) {
                item { EmptyHint("No circles yet", "Group the people you see often.", action = "Create a circle", onAction = onOpenCircles) }
            }
            item { Spacer(Modifier.height(4.dp)); Text("Long-press a chat to archive it.", color = Weave.InkFaint, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 20.dp)) }
        }
    }
}

