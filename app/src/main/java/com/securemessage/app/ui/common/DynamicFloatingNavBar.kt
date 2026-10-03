package com.securemessage.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securemessage.app.ui.theme.DockGlassBackground
import com.securemessage.app.ui.theme.DockPillActive
import com.securemessage.app.ui.theme.DockPillInactiveText
import com.securemessage.app.ui.theme.ObsidianVoid
import com.securemessage.app.ui.theme.TgBlue

/**
 * [icon] is the outlined glyph shown while a tab is idle, [selectedIcon] the filled glyph
 * shown while it is active (Telegram swaps outline -> filled on selection).
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

private val DockHeight = 46.dp

/**
 * Floating glass dock.
 *
 * - A soft fade sits behind the dock so page content never looks cut off underneath it.
 * - One highlight pill slides (with a little spring) to the tab you tap, instead of each
 *   tab drawing its own pill.
 * - The active icon pops slightly, idle tabs are outlined, active tabs are filled.
 * - The Profile tab shows your own avatar.
 */
@Composable
fun DynamicFloatingNavBar(
    selectedTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
    profileInitials: String = "",
) {
    val dockShape = RoundedCornerShape(26.dp)
    val tabs = NavTab.entries

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, ObsidianVoid.copy(alpha = 0.92f)),
                ),
            )
            .padding(start = 28.dp, end = 28.dp, top = 14.dp, bottom = 8.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 12.dp, shape = dockShape, clip = false)
                .clip(dockShape)
                .background(DockGlassBackground)
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)), dockShape)
                .padding(4.dp),
        ) {
            val tabWidth = maxWidth / tabs.size
            val indicatorX by animateDpAsState(
                targetValue = tabWidth * selectedTab.ordinal,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "dock_indicator",
            )

            // The single sliding highlight pill
            Box(
                modifier = Modifier
                    .offset(x = indicatorX)
                    .width(tabWidth)
                    .height(DockHeight)
                    .padding(horizontal = 3.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(DockPillActive),
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                tabs.forEach { tab ->
                    NavBarItem(
                        tab = tab,
                        isSelected = tab == selectedTab,
                        onClick = { onTabSelected(tab) },
                        badgeCount = if (tab == NavTab.CHATS) badgeCount else 0,
                        profileInitials = profileInitials,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavBarItem(
    tab: NavTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    badgeCount: Int,
    profileInitials: String,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current

    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tab_icon_scale",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) TgBlue else DockPillInactiveText,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "tab_content",
    )

    Column(
        modifier = modifier
            .height(DockHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(26.dp)
                .scale(iconScale),
        ) {
            if (tab == NavTab.PROFILE && profileInitials.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .then(
                            if (isSelected) Modifier.border(2.dp, TgBlue, CircleShape) else Modifier,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    MonochromeAvatar(initials = profileInitials, size = 20.dp)
                }
            } else {
                Icon(
                    imageVector = if (isSelected) tab.selectedIcon else tab.icon,
                    contentDescription = tab.title,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                )
            }
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 9.dp, y = (-5).dp)
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(TgBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        if (isSelected) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = tab.title,
                color = contentColor,
                fontSize = 9.5f.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
