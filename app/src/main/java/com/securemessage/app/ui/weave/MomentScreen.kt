package com.securemessage.app.ui.weave

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securemessage.app.data.weave.Moment
import com.securemessage.app.ui.common.AppViewModelFactory
import com.securemessage.app.ui.common.relativeTime
import com.securemessage.app.ui.theme.Weave

/** 4. Moment — share a moment, invite interaction. */
@Composable
fun MomentScreen(
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    vm: MomentViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var reply by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.message) {
        state.message?.let { android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_SHORT).show(); vm.messageShown() }
    }
    val m = state.moment

    WeaveBackground {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                item(key = "hero") {
                    PhotoArt(m?.id ?: "moment", Modifier.fillMaxWidth().height(460.dp)) {
                        Row(
                            Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            WeaveAvatar(m?.authorName.orEmpty().ifBlank { "?" }, size = 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m?.authorName?.ifBlank { "Someone" } ?: "", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(m?.let { "${relativeTime(it.createdAt)} · ${intentTag(it.intent)}" } ?: "", color = Color.White.copy(0.75f), fontSize = 11.sp)
                            }
                            IconCircle(Icons.Rounded.Close, "Close", onBack, background = Color.Black.copy(alpha = 0.3f), tint = Color.White)
                        }
                        if (m != null) {
                            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                                Text(m.body, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Tag(intentTag(m.intent))
                                    if (m.responseCount + state.responses.size > 0) Tag("${state.responses.size} responses")
                                }
                            }
                        }
                    }
                }
                if (m == null && !state.loading) {
                    item { EmptyHint("This moment is gone", "It may have been removed by its author.") }
                }
                if (m != null) {
                    item(key = "reactions") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            Moment.Reaction.entries.forEach { r ->
                                val active = m.myReaction(vm.myUid) == r.name
                                val count = m.reactions.values.count { it == r.name }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) {
                                        vm.react(r)
                                        if (r == Moment.Reaction.TALK && !active) vm.talkToAuthor(onOpenChat)
                                    }.padding(6.dp),
                                ) {
                                    Box(
                                        Modifier.size(46.dp).clip(CircleShape)
                                            .background(if (active) Weave.SoftTint else Weave.Surface)
                                            .border(1.dp, if (active) Weave.Indigo else Weave.Hairline, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(r.emoji, fontSize = 20.sp) }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        r.label + if (count > 0) " · $count" else "",
                                        color = if (active) Weave.Indigo else Weave.InkBody, fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                    items(state.responses, key = { it.id }) { r ->
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                            WeaveAvatar(r.authorName, size = 30.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(
                                Modifier.clip(RoundedCornerShape(14.dp)).background(Weave.Surface).padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(if (r.authorId == vm.myUid) "You" else r.authorName, color = Weave.Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(r.text, color = Weave.InkBody, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            if (m != null) {
                WeaveInput(
                    value = reply,
                    onChange = { if (it.length <= 1000) reply = it },
                    placeholder = "Respond...",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    onSend = { vm.respond(reply) { reply = "" } },
                )
            }
        }
    }
}

fun intentTag(i: Moment.Intent): String = when (i) {
    Moment.Intent.SHARE -> "Moment"
    Moment.Intent.ASK_HELP -> "Asking for help"
    Moment.Intent.INVITE -> "Invite"
    Moment.Intent.CELEBRATE -> "Celebrating"
    Moment.Intent.LOOKING_FOR -> "Looking for people"
}
