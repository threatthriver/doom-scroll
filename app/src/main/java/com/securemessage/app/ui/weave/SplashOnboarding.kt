package com.securemessage.app.ui.weave

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securemessage.app.ui.theme.Weave

/** 1. Splash — warm, human, minimal. */
@Composable
fun SplashScreen(onGetStarted: () -> Unit, onHaveAccount: () -> Unit) {
    PhotoArt(seed = "splash-sunset", modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(80.dp))
            Text("WEAVE", color = Color.White, fontSize = 58.sp, fontFamily = FontFamily.Cursive, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            listOf("Real People", "Real Moments", "A More Connected You").forEach {
                Text(it, color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp)
            }
            Spacer(Modifier.weight(1f))
            BigButton("Get Started", onGetStarted)
            Spacer(Modifier.height(12.dp))
            BigButton("I already have an account", onHaveAccount, outlined = true)
            Spacer(Modifier.height(28.dp))
            Text(
                "Not another social media.\nA more human world.",
                color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** What someone came to WEAVE for. Stored locally and used to tailor suggestions. */
enum class WeaveIntent(val title: String, val subtitle: String) {
    TALK("Talk to people", "I'm feeling social"),
    FIND("Find my people", "Same interests"),
    WORK("Work on something", "Build or learn"),
    ACTIVITY("Join an activity", "Online or nearby"),
    OFFLINE("Meet offline", "Events around me"),
    EXPLORE("Just explore", "Show me what's happening"),
}

object WeavePrefs {
    private const val FILE = "weave_prefs"
    private const val KEY_ONBOARDED = "onboarded"
    private const val KEY_INTENTS = "intents"

    fun isOnboarded(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_ONBOARDED, false)

    fun intents(ctx: Context): Set<WeaveIntent> =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getStringSet(KEY_INTENTS, emptySet()).orEmpty()
            .mapNotNull { runCatching { WeaveIntent.valueOf(it) }.getOrNull() }.toSet()

    fun save(ctx: Context, intents: Set<WeaveIntent>) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ONBOARDED, true)
            .putStringSet(KEY_INTENTS, intents.map { it.name }.toSet())
            .apply()
    }
}

/** 2. Onboarding — choose your intent (multi-select). */
@Composable
fun OnboardingScreen(name: String, onContinue: () -> Unit) {
    val ctx = LocalContext.current
    var picked by remember { mutableStateOf(WeavePrefs.intents(ctx)) }
    WeaveBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(28.dp))
            Text(
                "What brings you here\ntoday${if (name.isNotBlank()) ", ${name.substringBefore(' ')}" else ""}?",
                color = Weave.Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(), lineHeight = 32.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text("You can choose multiple", color = Weave.InkMuted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(WeaveIntent.entries.toList()) { intent ->
                    val on = intent in picked
                    PhotoArt(
                        seed = intent.name,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(Weave.RadiusM))
                            .border(2.dp, if (on) Weave.Lavender else Color.Transparent, RoundedCornerShape(Weave.RadiusM))
                            .semantics { selected = on }
                            .clickable(role = Role.Checkbox) { picked = if (on) picked - intent else picked + intent },
                    ) {
                        if (on) {
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(10.dp).size(24.dp).clip(CircleShape).background(Weave.Lavender),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Check, null, tint = Weave.OnLavender, modifier = Modifier.size(16.dp)) }
                        }
                        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                            Text(intent.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(intent.subtitle, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                        }
                    }
                }
            }
            BigButton("Continue  →", onClick = { WeavePrefs.save(ctx, picked); onContinue() }, enabled = picked.isNotEmpty())
            Spacer(Modifier.height(16.dp))
        }
    }
}
