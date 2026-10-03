package com.securemessage.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.securemessage.app.ui.theme.TgWallpaperBase
import com.securemessage.app.ui.theme.TgWallpaperInk

/**
 * Telegram-style chat wallpaper: flat navy base with a sparse doodle pattern
 * (speech bubbles, plus signs, stars, rings) drawn at low contrast.
 */
@Composable
fun TelegramWallpaper(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(TgWallpaperBase),
    ) {
        val tile = 104.dp.toPx()
        var row = 0
        var y = -tile
        while (y < size.height + tile) {
            var col = 0
            var x = -tile
            while (x < size.width + tile) {
                // Offset every other row for a scattered, non-grid look
                val cx = x + if (row % 2 == 0) 0f else tile / 2f
                val cy = y
                when ((row * 3 + col * 5) % 4) {
                    0 -> drawBubble(Offset(cx + tile * 0.35f, cy + tile * 0.35f), tile * 0.16f)
                    1 -> drawPlus(Offset(cx + tile * 0.62f, cy + tile * 0.55f), tile * 0.07f)
                    2 -> drawRing(Offset(cx + tile * 0.45f, cy + tile * 0.72f), tile * 0.06f)
                    else -> drawStar(Offset(cx + tile * 0.70f, cy + tile * 0.28f), tile * 0.06f)
                }
                x += tile
                col++
            }
            y += tile
            row++
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
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius * 0.45f),
            ),
        )
        // small tail
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

private fun DrawScope.drawStar(center: Offset, radius: Float) {
    val path = Path()
    val points = 5
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) radius else radius * 0.45f
        val angle = Math.toRadians((i * 180.0 / points) - 90.0)
        val px = center.x + (r * kotlin.math.cos(angle)).toFloat()
        val py = center.y + (r * kotlin.math.sin(angle)).toFloat()
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    drawPath(path, TgWallpaperInk, style = Stroke(width = 1.5.dp.toPx()))
}