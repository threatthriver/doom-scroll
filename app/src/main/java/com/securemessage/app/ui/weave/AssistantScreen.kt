package com.securemessage.app.ui.weave

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securemessage.app.data.weave.Session
import com.securemessage.app.ui.theme.Weave

/** One assistant reply: text plus tappable follow-ups. */
private data class Turn(val fromMe: Boolean, val text: String, val actions: List<Pair<String, () -> Unit>> = emptyList())

private enum class Ask(val label: String, val icon: ImageVector, val keywords: List<String>) {
    SIMILAR("Find people with similar interests", Icons.Rounded.Groups, listOf("similar", "interest", "people like", "find")),
    INTRODUCE("Introduce me to someone", Icons.Rounded.PersonAdd, listOf("introduce", "meet someone", "new people")),
    PLAN("Plan a group activity", Icons.Rounded.CalendarMonth, listOf("plan", "activity", "event", "organize")),
    MESSAGE("Help me message someone", Icons.Rounded.ChatBubbleOutline, listOf("message", "text", "say", "opener", "reach out")),
    NOW("What's happening right now", Icons.Rounded.Sensors, listOf("happening", "now", "live", "nearby")),
    IDEAS("Give me ideas for a good meetup", Icons.Rounded.Lightbulb, listOf("idea", "meetup", "suggest")),
}

/**
 * 11. WEAVE Assistant — your social coordinator. It works only from what the app already knows
 * (your interests, spaces, live sessions); it never reads your encrypted chats and never pretends
 * to be a friend. Every answer ends in a real action that connects you to people.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(
    vm: HomeViewModel,
    onBack: () -> Unit,
    onOpenSession: (Session) -> Unit,
    onCreateEvent: () -> Unit,
    onOpenExplore: () -> Unit,
    onNewChat: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val turns = remember { mutableStateListOf<Turn>() }
    var input by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(turns.size) { if (turns.isNotEmpty()) list.animateScrollToItem(turns.size) }

    fun answer(ask: Ask): Turn {
        val interests = state.me.interests
        val live = state.liveSessions.filter { it.isLive }
        return when (ask) {
            Ask.SIMILAR -> {
                val spaces = state.spaces.filter { s -> interests.any { s.name.contains(it, true) || s.description.contains(it, true) } }.take(3)
                when {
                    interests.isEmpty() -> Turn(false, "Add a few interests to your profile first, then I can match you with spaces and sessions around them.")
                    spaces.isEmpty() -> Turn(false, "No spaces match ${interests.take(3).joinToString()} yet. Starting one is the fastest way to find your people.", listOf("Browse spaces" to onOpenExplore))
                    else -> Turn(false, "People who share your interests gather here:\n" + spaces.joinToString("\n") { "• ${it.name} (${compact(it.memberCount)} members)" }, listOf("Open Explore" to onOpenExplore))
                }
            }
            Ask.INTRODUCE -> {
                val hosts = live.filter { it.hostId != vm.myUid }.distinctBy { it.hostId }.take(3)
                if (hosts.isEmpty()) Turn(false, "No one is hosting right now. Host a session and people can find you.", listOf("Plan something" to onCreateEvent))
                else Turn(false, "These people are hosting right now. Joining is a low-pressure way to meet:", hosts.map { s -> "${s.hostName.ifBlank { "Host" }} · ${s.title}" to { onOpenSession(s) } })
            }
            Ask.PLAN -> Turn(false, "Small and specific works best: one place, one time, 3–6 people. Want to set it up?", listOf("Create an event" to onCreateEvent))
            Ask.MESSAGE -> {
                val openers = listOf(
                    "Hey! Saw you're into ${interests.firstOrNull() ?: "this too"}. What got you started?",
                    "I'm planning a small meetup this week, would you be up for it?",
                    "Been a while! How's everything going on your side?",
                )
                Turn(false, "Short and specific beats perfect. Tap one to copy it:", openers.map { o -> o to { copy(ctx, o) } } + ("Start a chat" to onNewChat))
            }
            Ask.NOW -> if (live.isEmpty()) Turn(false, "It's quiet right now. You could be the one who starts something.", listOf("Plan something" to onCreateEvent))
            else Turn(false, "Happening now:", live.take(4).map { s -> "${sessionEmoji(s.kind)} ${s.title} · ${s.headCount}" to { onOpenSession(s) } })
            Ask.IDEAS -> {
                val base = interests.firstOrNull()?.let { listOf("A $it show-and-tell over coffee", "A 1-hour $it co-working session") }.orEmpty()
                Turn(false, "A few ideas:\n" + (base + listOf("Sunset walk + photo swap", "Board games at a café", "Cook-and-share potluck")).take(4).joinToString("\n") { "• $it" },
                    listOf("Create an event" to onCreateEvent))
            }
        }
    }

    fun ask(a: Ask) { turns += Turn(true, a.label); turns += answer(a) }
    fun freeText() {
        val q = input.trim()
        if (q.isEmpty()) return
        input = ""
        turns += Turn(true, q)
        val match = Ask.entries.firstOrNull { a -> a.keywords.any { q.contains(it, ignoreCase = true) } }
        turns += match?.let { answer(it) } ?: Turn(false, "I help you connect with people. Try one of these:", Ask.entries.map { a -> a.label to { ask(a) } })
    }

    WeaveBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                IconCircle(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, background = Color.Transparent)
            }
            LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item(key = "intro") {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = Weave.Ink, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("WEAVE Assistant", color = Weave.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(14.dp))
                        Box(Modifier.clip(RoundedCornerShape(Weave.RadiusM)).background(Weave.Surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text("How can I help you connect today?", color = Weave.Ink, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(14.dp))
                        WeaveCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(vertical = 6.dp)) {
                                Ask.entries.forEach { a ->
                                    Row(Modifier.fillMaxWidth().clickable { ask(a) }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(a.icon, null, tint = Weave.InkBody, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Text(a.label, color = Weave.Ink, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                itemsIndexed(turns) { _, t ->
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (t.fromMe) Alignment.End else Alignment.Start) {
                        Box(
                            Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (t.fromMe) Weave.Indigo else Weave.Surface).padding(horizontal = 14.dp, vertical = 10.dp),
                        ) { Text(t.text, color = if (t.fromMe) Color.White else Weave.Ink, fontSize = 13.sp, lineHeight = 19.sp) }
                        if (t.actions.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                t.actions.forEach { (label, act) -> LavenderPill(label, act) }
                            }
                        }
                    }
                }
            }
            WeaveInput(input, { if (it.length <= 300) input = it }, "Ask anything...", Modifier.padding(horizontal = 16.dp, vertical = 10.dp), onSend = ::freeText)
        }
    }
}

private fun copy(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Message", text))
    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
}
