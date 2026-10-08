package com.securemessage.app.ui.weave

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securemessage.app.data.weave.Moment
import com.securemessage.app.ui.theme.Weave

/**
 * Intent-first moment composer. You pick WHAT you're inviting (share / help / invite / celebrate /
 * looking for) before you write, because the intent is what turns a moment into interaction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentComposer(
    isPosting: Boolean,
    onDismiss: () -> Unit,
    onPost: (String, Moment.Intent) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf("") }
    var intent by remember { mutableStateOf(Moment.Intent.SHARE) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = Weave.BgElevated,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("Share a moment", color = Weave.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("What are you inviting?", color = Weave.InkMuted, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Moment.Intent.entries.toList()) { opt ->
                    val active = opt == intent
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Weave.RadiusPill))
                            .background(if (active) Weave.SoftTint else Weave.SurfaceAlt)
                            .then(if (active) Modifier.border(1.dp, Weave.Indigo, RoundedCornerShape(Weave.RadiusPill)) else Modifier)
                            .clickable { intent = opt }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            intentChip(opt),
                            color = if (active) Weave.Indigo else Weave.InkBody,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 110.dp)
                    .clip(RoundedCornerShape(Weave.RadiusM))
                    .background(Weave.Surface)
                    .border(1.dp, Weave.Hairline, RoundedCornerShape(Weave.RadiusM))
                    .padding(14.dp),
            ) {
                if (text.isEmpty()) {
                    Text(intentPlaceholder(intent), color = Weave.InkFaint, fontSize = 15.sp)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { if (it.length <= 2000) text = it },
                    textStyle = TextStyle(color = Weave.Ink, fontSize = 15.sp, lineHeight = 21.sp),
                    cursorBrush = SolidColor(Weave.Indigo),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))
            val canPost = text.isNotBlank() && !isPosting
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(Weave.RadiusPill))
                    .background(
                        if (canPost) Brush.horizontalGradient(listOf(Weave.Indigo, Weave.IndigoDeep))
                        else Brush.horizontalGradient(listOf(Weave.SurfaceHi, Weave.SurfaceHi)),
                    )
                    .clickable(enabled = canPost) { onPost(text, intent) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isPosting) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Text("Share", color = if (canPost) Color.White else Weave.InkMuted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun intentChip(i: Moment.Intent): String = when (i) {
    Moment.Intent.SHARE -> "Share"
    Moment.Intent.ASK_HELP -> "Ask for help"
    Moment.Intent.INVITE -> "Invite"
    Moment.Intent.CELEBRATE -> "Celebrate"
    Moment.Intent.LOOKING_FOR -> "Looking for"
}

private fun intentPlaceholder(i: Moment.Intent): String = when (i) {
    Moment.Intent.SHARE -> "Something on your mind…"
    Moment.Intent.ASK_HELP -> "What do you need help with?"
    Moment.Intent.INVITE -> "What are you inviting people to?"
    Moment.Intent.CELEBRATE -> "What are you celebrating?"
    Moment.Intent.LOOKING_FOR -> "Who or what are you looking for?"
}
