package com.securemessage.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Hush mark: a glossy glass orb with two pill-shaped eyes. Drawn as a warm sunset gem —
 * a coral-to-amber body lit by a soft golden glow rising from the base, a glass sheen, and a
 * pale warm rim — so the app's logo echoes its Warm Sunset palette.
 */
@Composable
fun HushOrb(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val c = Offset(s / 2f, s / 2f)
        val r = s / 2f

        // Body — coral at the top melting into amber, with a pink lower belly.
        drawCircle(
            brush = Brush.verticalGradient(
                0f to Color(0xFFFF9A76),
                0.32f to Color(0xFFFF6F61),
                0.64f to Color(0xFFF4812E),
                0.88f to Color(0xFFFF8FA3),
                1f to Color(0xFFFFD2A6),
                startY = c.y - r,
                endY = c.y + r,
            ),
            radius = r,
            center = c,
        )
        // Warm golden glow from the base.
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0xCCFFE0A6),
                0.55f to Color(0x40FFC98A),
                1f to Color(0x00FFC98A),
                center = Offset(c.x, c.y + r * 1.0f),
                radius = r,
            ),
            radius = r,
            center = c,
        )
        // Glass sheen near the top
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0x66FFFFFF),
                1f to Color(0x00FFFFFF),
                center = Offset(c.x - r * 0.2f, c.y - r * 0.73f),
                radius = r * 0.73f,
            ),
            radius = r,
            center = c,
        )
        // Rim light
        val rim = 1.2.dp.toPx().coerceAtMost(r * 0.08f)
        drawCircle(Color(0x59FFFFFF), radius = r - rim / 2f, center = c, style = Stroke(width = rim))

        // Eyes
        val eyeW = r * 0.127f
        val eyeH = r * 0.347f
        val dx = r * 0.26f
        val dy = -r * 0.183f
        for (sign in intArrayOf(-1, 1)) {
            drawRoundRect(
                color = Color(0xF5FFFFFF),
                topLeft = Offset(c.x + sign * dx - eyeW / 2f, c.y + dy - eyeH / 2f),
                size = Size(eyeW, eyeH),
                cornerRadius = CornerRadius(eyeW / 2f),
            )
        }
    }
}
