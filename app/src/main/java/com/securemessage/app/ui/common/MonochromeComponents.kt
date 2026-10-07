package com.securemessage.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securemessage.app.ui.theme.ObsidianCardHover
import com.securemessage.app.ui.theme.ObsidianVoid
import com.securemessage.app.ui.theme.SunsetAmber
import com.securemessage.app.ui.theme.SunsetPink
import com.securemessage.app.ui.theme.TgBlue
import com.securemessage.app.ui.theme.TgOnline
import com.securemessage.app.ui.theme.TextMuted
import com.securemessage.app.ui.theme.TextPrimary

/** Deterministic warm avatar colour derived from the title — a sunset-friendly palette. */
fun avatarColorFor(title: String): Color {
    val palette = listOf(
        Color(0xFFF2765E), // coral
        Color(0xFFF4A93C), // amber
        Color(0xFFEF8DA3), // rose
        Color(0xFFE2634C), // deep coral
        Color(0xFFD98C5F), // terracotta
        Color(0xFFCB8ADB), // soft orchid
        Color(0xFFE0A94B), // honey
        Color(0xFF7FBE8F), // sage (warm green)
        Color(0xFFF08C6A), // apricot
        Color(0xFFC9736E), // dusty rose
    )
    val key = title.trim().lowercase()
    if (key.isEmpty()) return palette[0]
    return palette[(key.sumOf { it.code } + key.length) % palette.size]
}

/** Circular avatar with bold initials and a soft warm sheen. */
@Composable
fun MonochromeAvatar(
    initials: String,
    size: Dp = 44.dp,
    showOnlineBadge: Boolean = false,
    inverted: Boolean = false,
    modifier: Modifier = Modifier,
    color: Color? = null,
    ring: Boolean = false,
) {
    val cleanInitials = initials.trim().take(2).uppercase().ifEmpty { "?" }
    val base = color ?: if (inverted) Color(0xFF4D3A34) else avatarColorFor(initials)
    // A gentle top-to-bottom warm sheen gives the flat initials circle a little depth.
    val fillBrush = Brush.verticalGradient(
        listOf(base.lighten(0.12f), base, base.darken(0.10f)),
    )
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .then(
                    if (ring) {
                        Modifier.border(
                            width = (size.value * 0.045f).dp.coerceAtLeast(1.5.dp),
                            brush = Brush.linearGradient(listOf(SunsetAmber, SunsetPink)),
                            shape = CircleShape,
                        )
                    } else {
                        Modifier
                    },
                )
                .clip(CircleShape)
                .background(fillBrush),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = cleanInitials,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.38f).sp,
                letterSpacing = (-0.3).sp,
            )
        }
        if (showOnlineBadge) {
            Box(
                modifier = Modifier
                    .size(size * 0.28f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(ObsidianVoid)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(TgOnline),
            )
        }
    }
}

/** Nudge a colour toward white by [fraction] (0..1) for soft highlights. */
private fun Color.lighten(fraction: Float): Color = Color(
    red = red + (1f - red) * fraction,
    green = green + (1f - green) * fraction,
    blue = blue + (1f - blue) * fraction,
    alpha = alpha,
)

/** Nudge a colour toward black by [fraction] (0..1) for soft shadows. */
private fun Color.darken(fraction: Float): Color = Color(
    red = red * (1f - fraction),
    green = green * (1f - fraction),
    blue = blue * (1f - fraction),
    alpha = alpha,
)

/** Blue circular unread badge. */
@Composable
fun MonochromePillBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(TgBlue)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Telegram flat rounded search pill — no border, filled surface. */
@Composable
fun MonochromeSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search Chats",
    onClear: () -> Unit = { onQueryChange("") },
    onSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(ObsidianCardHover)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Search",
            tint = TextMuted,
            modifier = Modifier.size(20.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    color = TextMuted,
                    fontSize = 15.sp,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(TgBlue),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(ObsidianCardHover)
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Clear",
                    tint = TextPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}