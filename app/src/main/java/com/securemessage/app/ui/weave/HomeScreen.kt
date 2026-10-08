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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securemessage.app.data.weave.Circle
import com.securemessage.app.data.weave.Moment
import com.securemessage.app.data.weave.Session
import com.securemessage.app.ui.common.relativeTime
import com.securemessage.app.ui.theme.Weave
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Someone you talk to, for the "Your circle" row. */
data class PersonChip(val chatId: String, val name: String)

/** 3. Home — your social command center. Bounded sections, ends with "You're caught up". */
@Composable
fun WeaveHomeScreen(
    vm: HomeViewModel,
    people: List<PersonChip>,
    onOpenSession: (Session) -> Unit,
    onOpenMoment: (Moment) -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenCircles: () -> Unit,
    onOpenNearby: () -> Unit,
    onOpenAssistant: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item(key = "greeting") {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                WeaveAvatar(state.displayName.ifBlank { "You" }, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${greeting()},", color = Weave.InkMuted, fontSize = 13.sp)
                    Text(state.displayName.ifBlank { "there" }.substringBefore(' '), color = Weave.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                IconCircle(Icons.Rounded.Map, "People & activities nearby", onOpenNearby)
                Spacer(Modifier.width(8.dp))
                IconCircle(Icons.Rounded.AutoAwesome, "WEAVE Assistant", onOpenAssistant, tint = Weave.Celebrate)
            }
        }

        item(key = "circle") {
            Column {
                SectionHeader("Your circle", action = "Circles", onAction = onOpenCircles, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                ) {
                    items(people.take(12), key = { "p" + it.chatId }) { p ->
                        BubbleClickable(p.name, { onOpenChat(p.chatId) }) { WeaveAvatar(p.name, size = 56.dp, ring = true) }
                    }
                    items(state.circles, key = { "c" + it.id }) { c ->
                        BubbleClickable(c.name, onOpenCircles) { CircleGlyph(c, 56) }
                    }
                    item(key = "add") {
                        BubbleClickable("New circle", onOpenCircles) {
                            Box(
                                Modifier.size(56.dp).clip(CircleShape).background(Weave.Surface).border(1.dp, Weave.Hairline, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Add, null, tint = Weave.InkBody) }
                        }
                    }
                }
            }
        }

        item(key = "happening") {
            Column(Modifier.padding(horizontal = 20.dp)) {
                SectionHeader("Happening Now", action = "See all", onAction = onOpenNearby)
                Spacer(Modifier.height(12.dp))
                if (state.liveSessions.isEmpty()) {
                    WeaveCard(Modifier.fillMaxWidth()) {
                        EmptyHint("Nothing live yet", "Tap + to start a session or plan an event.")
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.liveSessions.take(4).forEach { s ->
                            SessionRow(s, joined = vm.myUid in s.participantIds || vm.myUid in s.listenerIds, onOpen = { onOpenSession(s) }) {
                                vm.joinSession(s); onOpenSession(s)
                            }
                        }
                    }
                }
            }
        }

        item(key = "for_you_header") {
            SectionHeader("For You", modifier = Modifier.padding(horizontal = 20.dp))
        }
        when {
            state.loading -> item(key = "loading") { EmptyHint("Loading…", "Gathering what your people are up to.") }
            state.moments.isEmpty() -> item(key = "empty") {
                EmptyHint("Quiet for now", "Share a moment to start something.")
            }
            else -> {
                items(state.moments.take(15), key = { it.id }) { m ->
                    MomentRow(m, Modifier.padding(horizontal = 20.dp)) { onOpenMoment(m) }
                }
                item(key = "caught_up") {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("You're caught up", color = Weave.InkBody, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Go be with your people.", color = Weave.InkMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BubbleClickable(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(64.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
    ) {
        content()
        Spacer(Modifier.height(6.dp))
        Text(label.substringBefore(' ').ifBlank { label }, color = Weave.InkBody, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CircleGlyph(circle: Circle, sizeDp: Int) {
    PhotoArt(circle.name, Modifier.size(sizeDp.dp).clip(CircleShape)) {
        Text(
            circle.emoji.ifEmpty { initialsOf(circle.name) },
            fontSize = (sizeDp * 0.36f).sp, color = Color.White, modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
fun SessionRow(session: Session, joined: Boolean, onOpen: () -> Unit, onJoin: () -> Unit) {
    WeaveCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PhotoArt(session.title, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))) {
                Text(sessionEmoji(session.kind), fontSize = 20.sp, modifier = Modifier.align(Alignment.Center))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${sessionKindLabel(session)} · ${session.headCount} ${if (session.headCount == 1) "person" else "people"}",
                    color = Weave.InkMuted, fontSize = 11.sp,
                )
                Text(session.title, color = Weave.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sessionWhereWhen(session), color = Weave.InkMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            LavenderPill(if (joined) "Open" else "Join", onJoin, filled = !joined)
        }
    }
}

@Composable
private fun MomentRow(m: Moment, modifier: Modifier, onClick: () -> Unit) {
    WeaveCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PhotoArt(m.id, Modifier.size(width = 84.dp, height = 64.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.body, color = Weave.Ink, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${m.authorName.ifBlank { "Someone" }} · ${relativeTime(m.createdAt)}" +
                        if (m.reactionCount > 0) " · ${m.reactionCount} responded" else "",
                    color = Weave.InkMuted, fontSize = 11.sp,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Weave.InkMuted)
        }
    }
}

fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

fun sessionEmoji(kind: Session.Kind): String = when (kind) {
    Session.Kind.DISCUSSION -> "\uD83D\uDCAC"
    Session.Kind.STUDY -> "\uD83D\uDCDA"
    Session.Kind.PLAY -> "\uD83C\uDFF8"
    Session.Kind.BUILD -> "\uD83D\uDEE0\uFE0F"
    Session.Kind.WATCH -> "\uD83C\uDFAC"
    Session.Kind.VOICE -> "\uD83C\uDF99\uFE0F"
    Session.Kind.MEETUP -> "\u2615"
}

fun sessionKindLabel(s: Session): String = when {
    s.isEvent -> "Event"
    else -> when (s.kind) {
        Session.Kind.DISCUSSION -> "Live Discussion"
        Session.Kind.STUDY -> "Study Session"
        Session.Kind.PLAY -> "Playing"
        Session.Kind.BUILD -> "Building"
        Session.Kind.WATCH -> "Watch Party"
        Session.Kind.VOICE -> "Voice Room"
        Session.Kind.MEETUP -> "Meetup"
    }
}

fun sessionWhereWhen(s: Session): String {
    val whenText = s.startsAt?.let { SimpleDateFormat("EEE, d MMM · h:mm a", Locale.getDefault()).format(it.toDate()) }
        ?: "Started ${relativeTime(s.startedAt)}".replace("Started now", "Just started")
    val where = s.place.ifBlank { "Hosted by ${s.hostName.ifBlank { "someone" }}" }
    return "$where · $whenText"
}
