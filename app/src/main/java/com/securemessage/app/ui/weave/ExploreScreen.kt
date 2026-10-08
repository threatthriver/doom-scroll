package com.securemessage.app.ui.weave

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securemessage.app.data.weave.Space
import com.securemessage.app.ui.theme.Weave

/** 9. Explore — discover communities and opportunities. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(vm: HomeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("For You") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }

    val interests = state.me.interests.map { it.lowercase() }
    val shown = state.spaces.filter { s ->
        (query.isBlank() || s.name.contains(query, true) || s.description.contains(query, true)) && when (tab) {
            "Interests" -> s.category == Space.Category.INTEREST || s.category == Space.Category.PROFESSION
            "Location" -> s.category == Space.Category.LOCATION
            "University" -> s.category == Space.Category.UNIVERSITY
            else -> true
        }
    }.let { list ->
        if (tab != "For You" || interests.isEmpty()) list
        else list.sortedByDescending { s -> interests.count { s.name.contains(it, true) || s.description.contains(it, true) } }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Explore Spaces", color = Weave.Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconCircle(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, "Search spaces", {
                searching = !searching; if (!searching) query = ""
            }, background = Color.Transparent)
            IconCircle(Icons.Rounded.Add, "Create a space", { creating = true }, background = Color.Transparent)
        }
        if (searching) {
            WeaveInput(query, { query = it }, "Search spaces", Modifier.padding(horizontal = 20.dp), leading = Icons.Rounded.Search)
            Spacer(Modifier.height(10.dp))
        }
        ChipRow(listOf("For You", "Interests", "Location", "University"), tab, { tab = it })
        Spacer(Modifier.height(8.dp))
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 110.dp)) {
            if (shown.isEmpty()) {
                item { EmptyHint("No spaces here yet", "Start one for your interest, city or campus.", action = "Create a space", onAction = { creating = true }) }
            }
            items(shown, key = { it.id }) { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PhotoArt(s.name, Modifier.size(width = 64.dp, height = 52.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name, color = Weave.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${compact(s.memberCount)} members" + if (s.onlineCount > 0) " · ${compact(s.onlineCount)} online" else "",
                            color = Weave.InkMuted, fontSize = 12.sp,
                        )
                    }
                    val member = vm.isMember(s)
                    LavenderPill(if (member) "Joined" else "Join", { vm.toggleSpace(s) }, filled = !member)
                }
            }
        }
    }

    if (creating) {
        ModalBottomSheet(onDismissRequest = { creating = false }, containerColor = Weave.BgElevated) {
            var name by remember { mutableStateOf("") }
            var desc by remember { mutableStateOf("") }
            var cat by remember { mutableStateOf(Space.Category.INTEREST) }
            Column(Modifier.padding(20.dp).imePadding()) {
                Text("Create a space", color = Weave.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                WeaveInput(name, { if (it.length <= 60) name = it }, "Name (e.g. Photography)")
                Spacer(Modifier.height(10.dp))
                WeaveInput(desc, { if (it.length <= 300) desc = it }, "What's it about?", singleLine = false, minHeight = 80.dp)
                Spacer(Modifier.height(10.dp))
                val labels = mapOf(
                    Space.Category.INTEREST to "Interest", Space.Category.LOCATION to "Location",
                    Space.Category.UNIVERSITY to "University", Space.Category.PROFESSION to "Profession",
                )
                ChipRow(labels.values.toList(), labels[cat].orEmpty(), { l -> cat = labels.entries.first { it.value == l }.key })
                Spacer(Modifier.height(16.dp))
                BigButton("Create", { vm.createSpace(name, desc, cat); creating = false }, enabled = name.isNotBlank())
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

fun compact(n: Int): String = when {
    n >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", n / 1_000_000f).replace(".0", "")
    n >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", n / 1_000f).replace(".0", "")
    else -> n.toString()
}
