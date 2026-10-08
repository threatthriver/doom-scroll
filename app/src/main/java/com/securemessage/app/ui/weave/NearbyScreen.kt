package com.securemessage.app.ui.weave

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securemessage.app.data.weave.Session
import com.securemessage.app.ui.theme.Weave
import kotlin.math.abs

/**
 * 6. Nearby — turn online into real-world activities. The app never collects GPS, so the "map" is a
 * stylised neighbourhood: pins are placed deterministically per session and labelled with the place
 * the host typed. Filters are real: Friends = sessions with people you chat with.
 */
@Composable
fun NearbyScreen(
    vm: HomeViewModel,
    friendUids: Set<String>,
    onBack: () -> Unit,
    onOpenSession: (Session) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("All") }
    val shown = state.liveSessions.filter { s ->
        when (filter) {
            "Friends" -> s.hostId in friendUids || s.participantIds.any { it in friendUids }
            "Activities" -> !s.isEvent
            "Events" -> s.isEvent
            else -> true
        }
    }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = shown.firstOrNull { it.id == selected } ?: shown.firstOrNull { it.isEvent } ?: shown.firstOrNull()

    WeaveBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            WeaveTopBar("People & activities around you", onBack = onBack)
            ChipRow(listOf("All", "Friends", "Activities", "Events"), filter, { filter = it })
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(Weave.RadiusL))
                    .background(Color(0xFF1A1D24)).border(1.dp, Weave.Hairline, RoundedCornerShape(Weave.RadiusL)),
            ) {
                MapCanvas()
                val w = maxWidth
                val h = maxHeight
                // You are here.
                Box(Modifier.offset(w / 2 - 9.dp, h / 2 - 9.dp).size(18.dp).clip(CircleShape).background(Weave.Indigo).border(3.dp, Color.White, CircleShape))
                shown.take(8).forEach { s ->
                    val hash = abs(s.id.hashCode())
                    val fx = 0.08f + (hash % 60) / 100f
                    val fy = 0.06f + ((hash / 60) % 70) / 100f
                    Row(
                        Modifier.offset(w * fx, h * fy).widthIn(max = 170.dp)
                            .clip(RoundedCornerShape(14.dp)).background(Weave.Surface.copy(alpha = 0.95f))
                            .border(1.dp, if (s.id == focus?.id) Weave.Lavender else Weave.Hairline, RoundedCornerShape(14.dp))
                            .clickable { selected = s.id }
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WeaveAvatar(s.hostName, size = 28.dp, online = s.isLive)
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text("${s.headCount} ${sessionKindLabel(s).lowercase()}", color = Weave.InkMuted, fontSize = 9.sp, maxLines = 1)
                            Text(s.title, color = Weave.Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (s.place.isNotBlank()) Text(s.place, color = Weave.InkMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (shown.isEmpty()) {
                    Box(Modifier.align(Alignment.Center).padding(24.dp).clip(RoundedCornerShape(14.dp)).background(Weave.Surface)) {
                        EmptyHint("Nothing around yet", "Plan an event with + and it will show up here.")
                    }
                }
                focus?.let { s ->
                    Box(Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
                        SessionRow(s, joined = vm.myUid in s.participantIds || vm.myUid in s.listenerIds, onOpen = { onOpenSession(s) }) {
                            vm.joinSession(s); onOpenSession(s)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** A quiet dark street grid with a park and a river. */
@Composable
private fun MapCanvas() {
    Canvas(Modifier.fillMaxSize()) {
        val road = Color(0xFF262A33)
        val minor = Color(0xFF20232B)
        drawCircle(Color(0xFF1C2A24), radius = size.minDimension * 0.18f, center = Offset(size.width * 0.25f, size.height * 0.7f))
        val river = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.7f, 0f)
            cubicTo(size.width * 0.55f, size.height * 0.3f, size.width * 0.95f, size.height * 0.6f, size.width * 0.8f, size.height)
        }
        drawPath(river, Color(0xFF182433), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 34f))
        var x = 0f
        while (x < size.width) { drawLine(minor, Offset(x, 0f), Offset(x + 60f, size.height), 3f); x += 90f }
        var y = 0f
        while (y < size.height) { drawLine(minor, Offset(0f, y), Offset(size.width, y - 40f), 3f); y += 110f }
        drawLine(road, Offset(0f, size.height * 0.45f), Offset(size.width, size.height * 0.38f), 14f)
        drawLine(road, Offset(size.width * 0.4f, 0f), Offset(size.width * 0.48f, size.height), 14f)
    }
}
