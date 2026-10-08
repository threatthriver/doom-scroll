package com.securemessage.app.ui.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.securemessage.app.ui.theme.Weave
import kotlin.math.abs
import kotlin.math.min

/**
 * Wraps a chat bubble with a Telegram/WhatsApp-style swipe-to-reply gesture.
 *
 * Drag the row horizontally toward the center; a reply glyph fades in behind it. Past the
 * threshold you get a haptic tick and, on release, [onReply] fires. The row always springs back —
 * swipe-to-reply never leaves the bubble displaced.
 *
 * The direction follows the bubble's side: your own messages (right-aligned) are pulled left,
 * incoming messages are pulled right, so the gesture always moves "inward".
 */
@Composable
fun SwipeToReply(
    isMine: Boolean,
    onReply: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val triggerPx = with(density) { 56.dp.toPx() }
    val maxDragPx = with(density) { 80.dp.toPx() }
    val reply by rememberUpdatedState(onReply)

    // Signed live drag (negative for "mine", positive for incoming). Animated so release springs back.
    var dragging by remember { mutableFloatStateOf(0f) }
    val offset by animateFloatAsState(targetValue = dragging, animationSpec = spring(), label = "swipe_offset")
    val sign = if (isMine) -1f else 1f

    Box(modifier = modifier) {
        val progress = min(1f, abs(offset) / triggerPx)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .align(if (isMine) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 10.dp)
                .graphicsLayer { alpha = progress; scaleX = 0.6f + 0.4f * progress; scaleY = 0.6f + 0.4f * progress }
                .size(34.dp)
                .clip(CircleShape)
                .background(if (progress >= 1f) Weave.Indigo else Weave.SurfaceHi),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Reply,
                contentDescription = null,
                tint = if (progress >= 1f) Color.White else Weave.InkBody,
                modifier = Modifier.size(18.dp),
            )
        }

        Box(
            modifier = Modifier
                .graphicsLayer { translationX = offset }
                .pointerInput(isMine) {
                    // Per-gesture state lives inside this block, so each bubble/gesture is isolated.
                    var current = 0f
                    var crossed = false
                    detectHorizontalDragGestures(
                        onDragStart = { current = 0f; crossed = false },
                        onDragEnd = { if (crossed) reply(); dragging = 0f },
                        onDragCancel = { dragging = 0f },
                    ) { change, dragAmount ->
                        change.consume()
                        // Accumulate inward motion only; outward motion relaxes back toward 0.
                        current = (current + dragAmount * sign).coerceIn(0f, maxDragPx)
                        val nowCrossed = current >= triggerPx
                        if (nowCrossed && !crossed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        crossed = nowCrossed
                        dragging = current * sign
                    }
                },
        ) { content() }
    }
}
