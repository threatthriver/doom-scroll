package com.securemessage.app.ui.weave

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securemessage.app.data.weave.Session
import com.securemessage.app.ui.theme.Weave

enum class CreateChoice { MOMENT, SESSION, EVENT }

/** What the center "+" offers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSheet(onDismiss: () -> Unit, onPick: (CreateChoice) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Weave.BgElevated) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Create", color = Weave.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Option(Icons.Rounded.AutoAwesome, "Share a moment", "Invite people to respond") { onPick(CreateChoice.MOMENT) }
            Option(Icons.Rounded.Sensors, "Start a live session", "Talk, build or study together now") { onPick(CreateChoice.SESSION) }
            Option(Icons.Rounded.CalendarMonth, "Create an event", "Plan and meet in real life") { onPick(CreateChoice.EVENT) }
        }
    }
}

@Composable
private fun Option(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Weave.RadiusM)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(icon, title, onClick, tint = Weave.Lavender)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Weave.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Weave.InkMuted, fontSize = 12.sp)
        }
    }
}

/** Start a live session: title, kind, topics. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartSessionSheet(posting: Boolean, onDismiss: () -> Unit, onStart: (String, Session.Kind, List<String>) -> Unit) {
    var title by remember { mutableStateOf("") }
    var topics by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(Session.Kind.DISCUSSION) }
    val labels = mapOf(
        Session.Kind.DISCUSSION to "Discussion", Session.Kind.STUDY to "Study", Session.Kind.BUILD to "Build",
        Session.Kind.PLAY to "Play", Session.Kind.WATCH to "Watch", Session.Kind.VOICE to "Hangout",
    )
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Weave.BgElevated) {
        Column(Modifier.padding(bottom = 24.dp).imePadding()) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Start a live session", color = Weave.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                WeaveInput(title, { if (it.length <= 100) title = it }, "What are you doing? e.g. Recent advances in LLMs")
            }
            Spacer(Modifier.height(12.dp))
            ChipRow(labels.values.toList(), labels[kind].orEmpty(), { l -> kind = labels.entries.first { it.value == l }.key })
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = 20.dp)) {
                WeaveInput(topics, { if (it.length <= 120) topics = it }, "Topics, comma separated (optional)")
                Spacer(Modifier.height(16.dp))
                BigButton("Go live", {
                    onStart(title, kind, topics.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(5))
                }, enabled = title.isNotBlank(), loading = posting)
            }
        }
    }
}

