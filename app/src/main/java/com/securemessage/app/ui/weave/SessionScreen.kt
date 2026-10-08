package com.securemessage.app.ui.weave

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securemessage.app.ui.common.AppViewModelFactory
import com.securemessage.app.ui.theme.Weave

/** 5. Live session — talk, build, learn together. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionScreen(
    onBack: () -> Unit,
    vm: SessionViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var draft by rememberSaveable { mutableStateOf("") }
    var muted by remember { mutableStateOf(true) }
    var camera by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    LaunchedEffect(state.message) {
        state.message?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.messageShown() }
    }
    LaunchedEffect(state.messages.size) { if (state.messages.isNotEmpty()) list.animateScrollToItem(list.layoutInfo.totalItemsCount - 1) }

    val s = state.session
    val joined = s != null && (vm.myUid in s.participantIds || vm.myUid in s.listenerIds)
    fun nameOf(uid: String) = if (uid == vm.myUid) "You" else state.names[uid].orEmpty().ifBlank { "…" }

    WeaveBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, background = Color.Transparent)
            }
            if (s == null) {
                EmptyHint(if (state.loading) "Loading…" else "Session not found", if (state.loading) "" else "It may have ended.")
                return@Column
            }
            LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
                item(key = "head") {
                    Column {
                        Text(sessionKindLabel(s), color = Weave.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(s.title, color = Weave.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (s.isLive) {
                                Row(
                                    Modifier.clip(RoundedCornerShape(6.dp)).background(Weave.Live).padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Live", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Text("Ended · ", color = Weave.InkMuted, fontSize = 12.sp)
                            }
                            Text("${s.headCount} people · ${sessionWhereWhen(s)}", color = Weave.InkMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (s.topics.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                s.topics.forEach { Tag(it) }
                            }
                        }
                        if (s.description.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(s.description, color = Weave.InkBody, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(18.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            maxItemsInEachRow = 4,
                        ) {
                            s.participantIds.take(7).forEach { uid ->
                                Participant(nameOf(uid), if (uid == s.hostId) "Host" else null)
                            }
                            if (s.listenerIds.isNotEmpty() || s.participantIds.size > 7) {
                                val extra = s.listenerIds.size + (s.participantIds.size - 7).coerceAtLeast(0)
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
                                    Box(Modifier.size(56.dp).clip(CircleShape).background(Weave.Surface), contentAlignment = Alignment.Center) {
                                        Text("+$extra", color = Weave.Ink, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text("Listeners", color = Weave.InkMuted, fontSize = 11.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        if (joined && s.isLive) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Control(if (muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, if (muted) "Unmute" else "Mute") {
                                    muted = !muted
                                    Toast.makeText(ctx, "Voice is coming soon. Use the chat below for now.", Toast.LENGTH_SHORT).show()
                                }
                                Control(if (camera) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff, "Camera") {
                                    camera = !camera
                                    Toast.makeText(ctx, "Video is coming soon. Use the chat below for now.", Toast.LENGTH_SHORT).show()
                                }
                                Control(Icons.Rounded.Share, "Share") {
                                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                        .putExtra(Intent.EXTRA_TEXT, "Join me on WEAVE: ${s.title}")
                                    ctx.startActivity(Intent.createChooser(send, "Share session"))
                                }
                                Control(Icons.Rounded.CallEnd, "Leave", tint = Color.White, bg = Weave.Live) { vm.leave(onBack) }
                            }
                        } else if (s.isLive) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                LavenderPill("Join", { vm.join(false) }, modifier = Modifier.weight(1f))
                                LavenderPill("Listen in", { vm.join(true) }, filled = false, modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Weave.Hairline))
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (state.messages.isEmpty()) {
                    item { Text("No messages yet. Say hi.", color = Weave.InkMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp)) }
                }
                items(state.messages, key = { it.id }) { m ->
                    Row(Modifier.padding(vertical = 6.dp)) {
                        WeaveAvatar(m.senderName, size = 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(if (m.senderId == vm.myUid) "You" else m.senderName, color = Weave.Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(m.text, color = Weave.InkBody, fontSize = 13.sp)
                        }
                    }
                }
            }
            if (s.isLive) {
                WeaveInput(
                    value = draft,
                    onChange = { if (it.length <= 1000) draft = it },
                    placeholder = "Send a message...",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    onSend = { vm.send(draft) { draft = "" } },
                )
            }
        }
    }
}

@Composable
private fun Participant(name: String, badge: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        WeaveAvatar(name, size = 56.dp, online = true)
        Spacer(Modifier.height(4.dp))
        Text(name, color = Weave.Ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (badge != null) Text(badge, color = Weave.InkMuted, fontSize = 10.sp)
    }
}

@Composable
private fun Control(icon: ImageVector, label: String, tint: Color = Weave.Ink, bg: Color = Weave.Surface, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClickLabel = label, onClick = onClick).padding(6.dp),
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Weave.InkBody, fontSize = 11.sp)
    }
}
