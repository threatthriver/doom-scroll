package com.securemessage.app.ui.weave

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securemessage.app.data.model.User
import com.securemessage.app.data.weave.Circle
import com.securemessage.app.ui.theme.Weave
import kotlinx.coroutines.delay

/** 8. Circles — your people, organized naturally. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CirclesScreen(vm: HomeViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }

    WeaveBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            WeaveTopBar("Your Circles", onBack = onBack) {
                IconCircle(Icons.Rounded.Add, "New circle", { creating = true }, background = Color.Transparent)
            }
            if (state.circles.isEmpty()) {
                EmptyHint("No circles yet", "Circles are small groups you come back to: close friends, a study group, your team.",
                    action = "Create a circle", onAction = { creating = true })
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.circles, key = { it.id }) { c ->
                    PhotoArt(c.name, Modifier.aspectRatio(1.15f).clip(RoundedCornerShape(Weave.RadiusM)).clickable { openId = c.id }) {
                        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                            Text("${c.emoji} ${c.name}".trim(), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("${c.size} ${if (c.size == 1) "person" else "people"}", color = Color.White.copy(0.75f), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        ModalBottomSheet(onDismissRequest = { creating = false }, containerColor = Weave.BgElevated) {
            var name by remember { mutableStateOf("") }
            var emoji by remember { mutableStateOf("") }
            Column(Modifier.padding(20.dp).imePadding()) {
                Text("New circle", color = Weave.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                WeaveInput(name, { if (it.length <= 40) name = it }, "Name (e.g. Close Friends)")
                Spacer(Modifier.height(10.dp))
                WeaveInput(emoji, { if (it.length <= 4) emoji = it }, "Emoji (optional)")
                Spacer(Modifier.height(16.dp))
                BigButton("Create", { vm.createCircle(name, emoji.trim()); creating = false }, enabled = name.isNotBlank())
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    state.circles.firstOrNull { it.id == openId }?.let { circle ->
        ModalBottomSheet(onDismissRequest = { openId = null }, sheetState = rememberModalBottomSheetState(true), containerColor = Weave.BgElevated) {
            CircleDetail(circle, vm, onLeft = { openId = null })
        }
    }
}

@Composable
private fun CircleDetail(circle: Circle, vm: HomeViewModel, onLeft: () -> Unit) {
    val isOwner = circle.ownerId == vm.myUid
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<User>>(emptyList()) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(300)
        results = vm.searchPeople(query).filter { it.uid !in circle.memberIds }
    }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleGlyph(circle, 52)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(circle.name, color = Weave.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("${circle.size} people", color = Weave.InkMuted, fontSize = 13.sp)
            }
            LavenderPill("Leave", { vm.leaveCircle(circle); onLeft() }, filled = false)
        }
        Spacer(Modifier.height(16.dp))
        circle.memberIds.forEach { uid ->
            val name = circle.memberNames[uid].orEmpty().ifBlank { "Member" }
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                WeaveAvatar(name, size = 36.dp)
                Spacer(Modifier.width(10.dp))
                Text(if (uid == vm.myUid) "$name (you)" else name, color = Weave.Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (uid == circle.ownerId) Text("Owner", color = Weave.InkMuted, fontSize = 12.sp)
            }
        }
        if (isOwner) {
            Spacer(Modifier.height(16.dp))
            Text("Add someone", color = Weave.Ink, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            WeaveInput(query, { query = it }, "Search by username", leading = Icons.Rounded.Search)
            results.forEach { u ->
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    WeaveAvatar(u.displayName, size = 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u.displayName, color = Weave.Ink, fontSize = 14.sp)
                        Text("@${u.username}", color = Weave.InkMuted, fontSize = 12.sp)
                    }
                    LavenderPill("Add", { vm.addToCircle(circle, u); query = "" })
                }
            }
        }
    }
}

