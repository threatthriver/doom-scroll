package com.securemessage.app.ui.weave

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securemessage.app.ui.theme.Weave

/** 12. Profile — about you, without follower counts. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WeaveProfileScreen(vm: HomeViewModel, peopleCount: Int, onOpenSettings: () -> Unit, onOpenCircles: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val me = state.me
    var editing by rememberSaveable { mutableStateOf(false) }
    val name = me.displayName.ifBlank { "You" }
    val spaces = state.spaces.count { vm.isMember(it) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 110.dp)) {
        Box {
            PhotoArt(me.uid.ifBlank { name }, Modifier.fillMaxWidth().height(250.dp)) {
                Row(Modifier.statusBarsPadding().fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconCircle(Icons.Rounded.Settings, "Settings", onOpenSettings, background = Color.Black.copy(alpha = 0.35f), tint = Color.White)
                    Box(
                        Modifier.clip(RoundedCornerShape(Weave.RadiusPill)).background(Color.Black.copy(alpha = 0.35f))
                            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(Weave.RadiusPill))
                            .clickable(role = Role.Button, onClickLabel = "Edit profile") { editing = true }
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                    ) { Text("Edit", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
            Row(Modifier.padding(start = 20.dp, top = 205.dp), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.size(88.dp).clip(CircleShape).background(Weave.Bg).padding(3.dp)) { WeaveAvatar(name, size = 82.dp) }
            }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(10.dp))
            Text(name, color = Weave.Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (me.username.isNotBlank()) Text("@${me.username}", color = Weave.InkMuted, fontSize = 13.sp)
            if (me.headline.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(me.headline, color = Weave.InkBody, fontSize = 14.sp)
            }
            if (me.place.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(Icons.Rounded.LocationOn, null, tint = Weave.InkMuted, modifier = Modifier.size(14.dp))
                    Text(me.place, color = Weave.InkMuted, fontSize = 13.sp)
                }
            }
            if (me.bio.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(me.bio, color = Weave.InkBody, fontSize = 14.sp, lineHeight = 20.sp)
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(state.circles.size, "Circles", Modifier.weight(1f), onOpenCircles)
                Stat(spaces, "Spaces", Modifier.weight(1f))
                Stat(peopleCount, "People", Modifier.weight(1f))
            }
            Spacer(Modifier.height(22.dp))
            Text("Interests", color = Weave.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            if (me.interests.isEmpty()) {
                Text("Add what you love so the right people can find you.", color = Weave.InkMuted, fontSize = 13.sp,
                    modifier = Modifier.clickable { editing = true })
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    me.interests.forEach { Tag(it) }
                }
            }
        }
    }

    if (editing) {
        ModalBottomSheet(onDismissRequest = { editing = false }, sheetState = rememberModalBottomSheetState(true), containerColor = Weave.BgElevated) {
            var n by remember { mutableStateOf(me.displayName) }
            var headline by remember { mutableStateOf(me.headline) }
            var place by remember { mutableStateOf(me.place) }
            var bio by remember { mutableStateOf(me.bio) }
            var interests by remember { mutableStateOf(me.interests.joinToString(", ")) }
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Edit profile", color = Weave.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                WeaveInput(n, { if (it.length <= 50) n = it }, "Name")
                WeaveInput(headline, { if (it.length <= 80) headline = it }, "Headline, e.g. Student · ML & Robotics")
                WeaveInput(place, { if (it.length <= 60) place = it }, "City or campus", leading = Icons.Rounded.LocationOn)
                WeaveInput(bio, { if (it.length <= 160) bio = it }, "Bio", singleLine = false, minHeight = 80.dp)
                WeaveInput(interests, { interests = it }, "Interests, separated by commas", singleLine = false)
                Spacer(Modifier.height(4.dp))
                BigButton("Save", {
                    vm.saveDetails(n, bio, headline, place, interests.split(",").map { it.trim() }.filter { it.isNotEmpty() }) { editing = false }
                }, enabled = n.isNotBlank())
            }
        }
    }
}

@Composable
private fun Stat(value: Int, label: String, modifier: Modifier, onClick: (() -> Unit)? = null) {
    WeaveCard(modifier, onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", color = Weave.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(label, color = Weave.InkMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

