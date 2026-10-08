package com.securemessage.app.ui.weave

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securemessage.app.ui.theme.Weave

/** Full-screen WEAVE canvas. */
@Composable
fun WeaveBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize().background(Weave.Bg)) { content() }
}

/** Initials for a name ("Aniket Kumar" -> "AK"). */
fun initialsOf(name: String): String =
    name.trim().split(" ").mapNotNull { it.firstOrNull()?.toString() }
        .take(2).joinToString("").ifEmpty { name.take(1) }.uppercase()

/** Soft gradient avatar with initials, optional online dot. */
@Composable
fun WeaveAvatar(
    name: String,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
    ring: Boolean = false,
    online: Boolean = false,
) {
    val palette = Weave.PhotoPalettes[Weave.avatarIndex(name.lowercase()) % Weave.PhotoPalettes.size]
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .then(if (ring) Modifier.border(2.dp, Weave.Indigo, CircleShape) else Modifier)
                .background(Brush.linearGradient(listOf(palette[1], palette[2]))),
            contentAlignment = Alignment.Center,
        ) {
            Text(initialsOf(name.ifBlank { "?" }), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp)
        }
        if (online) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(size * 0.26f).clip(CircleShape)
                    .background(Weave.Online).border(2.dp, Weave.Bg, CircleShape),
            )
        }
    }
}

/**
 * Painted "photo" — a warm sky gradient with a sun and layered hills. Stands in for imagery in the
 * mockups (the app has no image hosting), deterministic per [seed] so a card always looks the same.
 */
@Composable
fun PhotoArt(seed: String, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val p = Weave.PhotoPalettes[Weave.avatarIndex(seed) % Weave.PhotoPalettes.size]
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(p[0], p[1], p[2])))
            val h = size.height
            val w = size.width
            drawCircle(Color.White.copy(alpha = 0.28f), radius = w * 0.09f, center = Offset(w * 0.68f, h * 0.55f))
            fun hill(top: Float, color: Color, phase: Float) {
                val path = Path().apply {
                    moveTo(0f, h)
                    lineTo(0f, h * top)
                    cubicTo(w * (0.25f + phase), h * (top - 0.14f), w * (0.55f - phase), h * (top + 0.1f), w, h * (top - 0.05f))
                    lineTo(w, h)
                    close()
                }
                drawPath(path, color)
            }
            hill(0.68f, p[0].copy(alpha = 0.55f), 0.05f)
            hill(0.8f, p[0].copy(alpha = 0.85f), -0.05f)
            drawRect(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f)))
        }
        content()
    }
}

/** Screen top bar: optional back, title, trailing actions. */
@Composable
fun WeaveTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconCircle(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, background = Color.Transparent)
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title, color = Weave.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        actions()
    }
}

@Composable
fun IconCircle(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Weave.Surface,
    tint: Color = Weave.Ink,
    size: Dp = 42.dp,
) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(background)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size * 0.5f)) }
}

/** Small lavender action pill ("Join", "Next"). */
@Composable
fun LavenderPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Weave.RadiusPill))
            .background(if (!filled) Weave.SurfaceAlt else if (enabled) Weave.Lavender else Weave.SurfaceHi)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (!filled) Weave.InkBody else if (enabled) Weave.OnLavender else Weave.InkMuted,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Big full-width pill button. White by default like the splash's "Get Started". */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(Weave.RadiusPill))
            .background(if (outlined) Color.Transparent else if (enabled) Color.White else Weave.SurfaceHi)
            .then(if (outlined) Modifier.border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(Weave.RadiusPill)) else Modifier)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = Weave.Bg, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text(
                text,
                color = if (outlined) Color.White else if (enabled) Weave.Bg else Weave.InkMuted,
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Filter chip (All / Friends / …). */
@Composable
fun WeaveChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Weave.RadiusPill))
            .background(if (selected) Color.White else Weave.Surface)
            .border(1.dp, if (selected) Color.White else Weave.Hairline, RoundedCornerShape(Weave.RadiusPill))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) Weave.Bg else Weave.InkBody, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = if (selected) Weave.Bg else Weave.InkBody, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
    ) {
        items(options) { o -> WeaveChip(o, o == selected, { onSelect(o) }) }
    }
}

/** Static tag ("Machine Learning"). */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(Weave.RadiusPill)).background(Weave.SurfaceAlt)
            .border(1.dp, Weave.Hairline, RoundedCornerShape(Weave.RadiusPill))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(text, color = Weave.InkBody, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Weave.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(action, color = Weave.Indigo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(4.dp))
        }
    }
}

/** Rounded dark card container. */
@Composable
fun WeaveCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Weave.RadiusM))
            .background(Weave.Surface)
            .border(1.dp, Weave.Hairline, RoundedCornerShape(Weave.RadiusM))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}

/** Single-line rounded text input, optional leading icon and send action. */
@Composable
fun WeaveInput(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    singleLine: Boolean = true,
    minHeight: Dp = 48.dp,
    onSend: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(if (singleLine) Weave.RadiusPill else Weave.RadiusM))
            .background(Weave.Surface)
            .border(1.dp, Weave.Hairline, RoundedCornerShape(if (singleLine) Weave.RadiusPill else Weave.RadiusM))
            .padding(start = 16.dp, end = if (onSend != null) 6.dp else 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
    ) {
        if (leading != null) {
            Icon(leading, null, tint = Weave.InkMuted, modifier = Modifier.size(18.dp).padding(top = if (singleLine) 0.dp else 8.dp))
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f).padding(vertical = if (singleLine) 0.dp else 8.dp)) {
            if (value.isEmpty()) Text(placeholder, color = Weave.InkFaint, fontSize = 14.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = singleLine,
                textStyle = TextStyle(color = Weave.Ink, fontSize = 14.sp, lineHeight = 20.sp),
                cursorBrush = SolidColor(Weave.Indigo),
                keyboardOptions = KeyboardOptions(imeAction = if (onSend != null) ImeAction.Send else ImeAction.Default),
                keyboardActions = KeyboardActions(onSend = { onSend?.invoke() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (onSend != null) {
            val can = value.isNotBlank()
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(if (can) Weave.Indigo else Color.Transparent)
                    .clickable(enabled = can, role = Role.Button, onClickLabel = "Send", onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = if (can) Color.White else Weave.InkMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun EmptyHint(title: String, body: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Weave.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Weave.InkMuted, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            LavenderPill(action, onAction)
        }
    }
}
