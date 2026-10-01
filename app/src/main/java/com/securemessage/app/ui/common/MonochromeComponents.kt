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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securemessage.app.ui.theme.HairlineBorder
import com.securemessage.app.ui.theme.ObsidianSurfaceElevated
import com.securemessage.app.ui.theme.PureBlack
import com.securemessage.app.ui.theme.PureWhite
import com.securemessage.app.ui.theme.TextMuted
import com.securemessage.app.ui.theme.TextPrimary
import com.securemessage.app.ui.theme.TextSecondary

/**
 * Stark high-contrast Monochromatic Avatar with pure white background and bold black initials.
 */
@Composable
fun MonochromeAvatar(
    initials: String,
    size: Dp = 44.dp,
    showOnlineBadge: Boolean = false,
    inverted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val cleanInitials = initials.trim().take(2).uppercase().ifEmpty { "?" }
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (inverted) PureBlack else PureWhite)
                .border(1.dp, if (inverted) HairlineBorder else PureBlack, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = cleanInitials,
                color = if (inverted) PureWhite else PureBlack,
                fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.4f).sp,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = (-0.5).sp,
            )
        }
        if (showOnlineBadge) {
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(PureBlack)
                    .border(1.5.dp, HairlineBorder, CircleShape)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(PureWhite),
            )
        }
    }
}

/**
 * High-contrast stark white pill badge for unread counts and status tokens.
 */
@Composable
fun MonochromePillBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9999.dp))
            .background(PureWhite)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = PureBlack,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp,
        )
    }
}

/**
 * Sleek monochromatic inline search input field.
 */
@Composable
fun MonochromeSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "SEARCH TRANSMISSIONS...",
    onClear: () -> Unit = { onQueryChange("") },
    onSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianSurfaceElevated)
            .border(1.dp, HairlineBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Search",
            tint = TextMuted,
            modifier = Modifier.size(18.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.8.sp,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                ),
                cursorBrush = SolidColor(PureWhite),
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
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Clear",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
