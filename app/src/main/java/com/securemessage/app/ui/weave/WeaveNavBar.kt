package com.securemessage.app.ui.weave

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securemessage.app.ui.theme.Weave

/** WEAVE tabs. The center "+" is an action (create), not a tab. */
enum class WeaveTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    EXPLORE("Explore", Icons.Rounded.Explore),
    MESSAGES("Chats", Icons.AutoMirrored.Rounded.Chat),
    ME("Me", Icons.Rounded.Person),
}

/** Flat dark bottom bar: Home · Explore · (+) · Chats · Me. */
@Composable
fun WeaveNavBar(
    selected: WeaveTab,
    onSelect: (WeaveTab) -> Unit,
    onCompose: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
) {
    val haptic = LocalHapticFeedback.current
    Column(modifier.fillMaxWidth().background(Weave.BgElevated)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Weave.HairlineSoft))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp).selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Item(WeaveTab.HOME, selected, 0, onSelect)
            Item(WeaveTab.EXPLORE, selected, 0, onSelect)
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Color.White)
                    .selectable(false, role = Role.Button) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onCompose() }
                    .semantics { contentDescription = "Create" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, null, tint = Weave.Bg, modifier = Modifier.size(26.dp)) }
            Item(WeaveTab.MESSAGES, selected, badgeCount, onSelect)
            Item(WeaveTab.ME, selected, 0, onSelect)
        }
    }
}

@Composable
private fun Item(tab: WeaveTab, selected: WeaveTab, badge: Int, onSelect: (WeaveTab) -> Unit) {
    val on = tab == selected
    val color = if (on) Weave.Ink else Weave.InkMuted
    Column(
        Modifier.clip(CircleShape)
            .selectable(on, role = Role.Tab) { onSelect(tab) }
            .semantics { contentDescription = tab.title + if (badge > 0) ", $badge unread" else "" }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Icon(tab.icon, null, tint = color, modifier = Modifier.size(22.dp))
            if (badge > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp).size(8.dp).clip(CircleShape)
                        .background(Weave.Live).border(1.5.dp, Weave.BgElevated, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(tab.title, color = color, fontSize = 10.sp)
    }
}
