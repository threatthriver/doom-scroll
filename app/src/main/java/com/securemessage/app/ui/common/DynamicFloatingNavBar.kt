package com.securemessage.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securemessage.app.ui.theme.DockPillActive
import com.securemessage.app.ui.theme.DockPillInactiveText
import com.securemessage.app.ui.theme.ObsidianVoid
import com.securemessage.app.ui.theme.TgBlue

/**
 * [icon] is the outlined glyph shown while a tab is idle, [selectedIcon] the filled glyph
 * shown while it is active.
 */
enum class NavTab(
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    CHATS("Chats", Icons.Outlined.ChatBubbleOutline, Icons.Filled.Chat),
    CONTACTS("Contacts", Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
    PROFILE("Profile", Icons.Filled.Person, Icons.Filled.Person),
}

private val DockShape = RoundedCornerShape(32.dp)
private val ItemShape = RoundedCornerShape(26.dp)
private val ItemHeight = 52.dp

/** How much wider the selected tab is than an idle one. The tabs animate their widths, so the
 *  highlight appears to flow from one tab to the next. */
private const val SELECTED_WEIGHT = 2.2f

/**
 * Floating dock.
 *
 * - The selected tab grows to show its name next to its icon; the others shrink to icons. All
 *   widths are animated with one spring, so switching tabs reads as a single smooth motion.
 * - Tabs press in slightly under the finger and spring back.
 * - Idle icons are outlined, the active one is filled and tinted.
 * - Unread messages show as a pill badge on Chats; Profile shows your own avatar.
 * - Each tab reports itself as a selectable tab to screen readers.
 */
@Composable
fun DynamicFloatingNavBar(
    selectedTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
    profileInitials: String = "",
) {
    val tabs = NavTab.entries
    val haptic = LocalHapticFeedback.current
    val dockBrush = remember {
        Brush.verticalGradient(listOf(Color(0xF53A2C28), Color(0xF5241B19)))
    }
    val borderBrush = remember {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.03f)))
    }
    val fadeBrush = remember {
        Brush.verticalGradient(listOf(Color.Transparent, ObsidianVoid.copy(alpha = 0.94f)))
    }

    // A soft fade behind the dock so page content never looks cut off underneath it.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(fadeBrush)
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 18.dp,
                    shape = DockShape,
                    clip = false,
                    ambientColor = Color.Black,
                    spotColor = TgBlue.copy(alpha = 0.6f),
                )
                .clip(DockShape)
                .background(dockBrush)
                .border(1.dp, borderBrush, DockShape)
                .padding(6.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                NavSlot(
                    tab = tab,
                    selected = tab == selectedTab,
                    badgeCount = if (tab == NavTab.CHATS) badgeCount else 0,
                    profileInitials = profileInitials,
                    onClick = {
                        if (tab != selectedTab) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTabSelected(tab)
                    },
                )
            }
        }
    }
}

/** One tab's share of the dock. Its width is animated, so the dock reshapes smoothly. */
@Composable
private fun RowScope.NavSlot(
    tab: NavTab,
    selected: Boolean,
    badgeCount: Int,
    profileInitials: String,
    onClick: () -> Unit,
) {
    val weight by animateFloatAsState(
        targetValue = if (selected) SELECTED_WEIGHT else 1f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "nav_weight",
    )
    NavItem(
        tab = tab,
        selected = selected,
        badgeCount = badgeCount,
        profileInitials = profileInitials,
        onClick = onClick,
        modifier = Modifier.weight(weight.coerceAtLeast(0.1f)),
    )
}

@Composable
private fun NavItem(
    tab: NavTab,
    selected: Boolean,
    badgeCount: Int,
    profileInitials: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "nav_press",
    )
    val pillAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 240),
        label = "nav_pill",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) TgBlue else DockPillInactiveText,
        animationSpec = tween(durationMillis = 200),
        label = "nav_content",
    )

    val description = buildString {
        append(tab.title)
        if (badgeCount > 0) append(", $badgeCount unread")
    }

    Row(
        modifier = modifier
            .height(ItemHeight)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(ItemShape)
            .background(DockPillActive.copy(alpha = pillAlpha))
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { contentDescription = description }
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
            if (tab == NavTab.PROFILE && profileInitials.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .then(if (selected) Modifier.border(2.dp, TgBlue, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    MonochromeAvatar(initials = profileInitials, size = 21.dp)
                }
            } else {
                Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp),
                )
            }
            UnreadBadge(count = badgeCount, modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-6).dp))
        }

        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(220, delayMillis = 60)) + expandHorizontally(tween(260), expandFrom = Alignment.Start),
            exit = fadeOut(tween(120)) + shrinkHorizontally(tween(220), shrinkTowards = Alignment.Start),
        ) {
            Text(
                text = tab.title,
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                // The row already announces the tab's name; don't read the label twice.
                modifier = Modifier
                    .padding(start = 7.dp)
                    .clearAndSetSemantics { },
            )
        }
    }
}

/** Pill-shaped unread count that pops in when the first unread message arrives. */
@Composable
private fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(
        targetValue = if (count > 0) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "badge_scale",
    )
    if (scale <= 0.01f) return
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .widthIn(min = 17.dp)
            .height(17.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xFFFF5A4D))
            .border(1.5.dp, Color(0xFF241B19), RoundedCornerShape(9.dp))
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
