package com.securemessage.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.securemessage.app.ui.theme.SunsetAmber
import com.securemessage.app.ui.theme.SunsetCoral
import com.securemessage.app.ui.theme.TgWallpaperBottom
import com.securemessage.app.ui.theme.TgWallpaperInk
import com.securemessage.app.ui.theme.TgWallpaperTop

/**
 * Warm Sunset chat wallpaper: a soft vertical sunset gradient (warm dusk at the top fading to a
 * deep cozy base) with a faint scatter of friendly doodles — hearts, speech bubbles, plus signs,
 * sparkles, rings — drawn at very low contrast so they feel like texture, not clutter.
 *
 * A gentle warm "sun glow" sits in the upper third, like late-afternoon light spilling in.
 */
@Composable
fun TelegramWallpaper(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Base sunset gradient.
            drawRect(
                brush = Brush.verticalGradient(
                    0f to TgWallpaperTop,
                    0.55f to TgWallpaperBottom,
                    1f to TgWallpaperBottom,
                ),
            )
            // Warm sun glow spilling from the upper area.
            drawRect(
                brush = Brush.radialGradient(
                    0f to SunsetCoral.copy(alpha = 0.10f),
                    0.6f to SunsetAmber.copy(alpha = 0.04f),
                    1f to SunsetAmber.copy(alpha = 0f),
                    center = Offset(size.width * 0.72f, size.height * 0.14f),
                    radius = size.width * 0.9f,
                ),
            )

            // Faint doodle texture.
            val tile = 112.dp.toPx()
            var row = 0
            var y = -tile
            while (y < size.height + tile) {
                var col = 0
                var x = -tile
                while (x < size.width + tile) {
                    val cx = x + if (row % 2 == 0) 0f else tile / 2f
                    val cy = y
                    when ((row * 3 + col * 5) % 5) {
                        0 -> drawBubble(Offset(cx + tile * 0.35f, cy + tile * 0.35f), tile * 0.16f)
                        1 -> drawPlus(Offset(cx + tile * 0.62f, cy + tile * 0.55f), tile * 0.07f)
                        2 -> drawRing(Offset(cx + tile * 0.45f, cy + tile * 0.72f), tile * 0.06f)
                        3 -> drawHeart(Offset(cx + tile * 0.28f, cy + tile * 0.66f), tile * 0.08f)
                        else -> drawSparkle(Offset(cx + tile * 0.70f, cy + tile * 0.28f), tile * 0.07f)
                    }
                    x += tile
                    col++
                }
                y += tile
                row++
            }
        }
    }
}

private fun DrawScope.drawBubble(center: Offset, radius: Float) {
    val w = radius * 2f
    val h = radius * 1.5f
    val path = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = center.x - w / 2f,
                top = center.y - h / 2f,
                right = center.x + w / 2f,
                bottom = center.y + h / 2f,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius * 0.5f),
            ),
        )
        moveTo(center.x - w * 0.28f, center.y + h / 2f - 1f)
        lineTo(center.x - w * 0.36f, center.y + h / 2f + radius * 0.55f)
        lineTo(center.x - w * 0.02f, center.y + h / 2f - 1f)
        close()
    }
    drawPath(path, TgWallpaperInk, style = Stroke(width = 1.6.dp.toPx()))
}

private fun DrawScope.drawPlus(center: Offset, arm: Float) {
    val stroke = 1.8.dp.toPx()
    drawLine(TgWallpaperInk, Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), stroke)
    drawLine(TgWallpaperInk, Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), stroke)
}

private fun DrawScope.drawRing(center: Offset, radius: Float) {
    drawCircle(TgWallpaperInk, radius, center, style = Stroke(width = 1.6.dp.toPx()))
    drawCircle(TgWallpaperInk, radius * 0.28f, center, style = Stroke(width = 1.4.dp.toPx()))
}

/** A small outlined heart — the friendly centrepiece of the warm doodle set. */
private fun DrawScope.drawHeart(center: Offset, radius: Float) {
    val path = Path().apply {
        val top = center.y - radius * 0.4f
        moveTo(center.x, center.y + radius * 0.9f)
        cubicTo(
            center.x - radius * 1.4f, center.y - radius * 0.2f,
            center.x - radius * 0.6f, top - radius,
            center.x, top,
        )
        cubicTo(
            center.x + radius * 0.6f, top - radius,
            center.x + radius * 1.4f, center.y - radius * 0.2f,
            center.x, center.y + radius * 0.9f,
        )
        close()
    }
    drawPath(path, TgWallpaperInk, style = Stroke(width = 1.5.dp.toPx()))
}

/** A four-point sparkle, softer and friendlier than a sharp star. */
private fun DrawScope.drawSparkle(center: Offset, radius: Float) {
    val path = Path()
    val inner = radius * 0.32f
    val pts = 4
    for (i in 0 until pts * 2) {
        val r = if (i % 2 == 0) radius else inner
        val angle = Math.toRadians((i * 180.0 / pts) - 90.0)
        val px = center.x + (r * kotlin.math.cos(angle)).toFloat()
        val py = center.y + (r * kotlin.math.sin(angle)).toFloat()
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    drawPath(path, TgWallpaperInk, style = Stroke(width = 1.5.dp.toPx()))
}
