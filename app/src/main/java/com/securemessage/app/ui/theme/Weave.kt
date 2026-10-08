package com.securemessage.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * WEAVE design system — the single source of truth for the relationship-first app's look.
 *
 * Dark, warm, minimal, matching the product mockups: a deep charcoal canvas, soft raised
 * surfaces, and the indigo→violet→cyan accent pulled from the app icon's orb. Plain `val`s
 * (not a MaterialTheme) so any composable or draw scope can read them cheaply on hot paths.
 *
 * Everything WEAVE renders references this object, so the whole product re-themes from one file.
 */
object Weave {
    // --- Accent ramp (the app icon's orb) --------------------------------
    val Violet = Color(0xFF8E86F7)
    val Indigo = Color(0xFF8F87FF)   // primary accent — soft periwinkle
    val IndigoDeep = Color(0xFF6A61F0)
    val Blue = Color(0xFF4D8DFF)
    val Cyan = Color(0xFF5FE9FF)

    /** Pale lavender pill used for "Join" / "Next" actions, with its dark ink. */
    val Lavender = Color(0xFFCBC7FF)
    val OnLavender = Color(0xFF221E52)

    /** Warm "photo" palettes used as artwork where the mockups show imagery. */
    val PhotoPalettes = listOf(
        listOf(Color(0xFF2A1B2E), Color(0xFF7A3E4A), Color(0xFFE08A5B)), // sunset
        listOf(Color(0xFF0F1B2D), Color(0xFF2E4B73), Color(0xFF8FA9C9)), // dusk blue
        listOf(Color(0xFF1B1428), Color(0xFF4B2E6B), Color(0xFFB07ACB)), // violet evening
        listOf(Color(0xFF1A1A12), Color(0xFF5A4A26), Color(0xFFD7A65A)), // golden hour
        listOf(Color(0xFF0F2220), Color(0xFF2B5A52), Color(0xFF7FC2A8)), // forest
        listOf(Color(0xFF241417), Color(0xFF6B3036), Color(0xFFF0A07A)), // ember
    )

    /** Primary call-to-action gradient endpoints. */
    val AccentStart = Indigo
    val AccentEnd = IndigoDeep

    // --- Canvas & surfaces (warm-neutral charcoal, never cold black) -----
    val Bg = Color(0xFF0C0D11)         // app background
    val BgElevated = Color(0xFF121318) // elevated backdrop (sheets, headers)
    val Surface = Color(0xFF17181E)    // cards, bars, bubbles
    val SurfaceAlt = Color(0xFF1F2027) // search fields, pressed rows, chips
    val SurfaceHi = Color(0xFF282A32)  // hovered / selected surface
    val SoftTint = Color(0x298F87FF)   // 10% indigo wash for selected pills
    val SoftCircle = Color(0xFF25263A) // icon-circle background with cool tint

    // --- Lines -----------------------------------------------------------
    val Hairline = Color(0xFF25262E)
    val HairlineSoft = Color(0xFF1C1D24)

    // --- Text ------------------------------------------------------------
    val Ink = Color(0xFFF2F3FA)        // primary text (near-white, cool)
    val InkBody = Color(0xFFC3C6D4)    // body text
    val InkMuted = Color(0xFF868AA0)   // captions, timestamps, placeholders
    val InkFaint = Color(0xFF5B5F74)   // disabled / very subtle

    // --- Status / feedback -----------------------------------------------
    val Online = Color(0xFF4ADE80)
    val Live = Color(0xFFFF5A6E)       // "LIVE" dot on sessions
    val Error = Color(0xFFF2645B)
    val Warn = Color(0xFFF5B74C)

    // --- Intent reaction accents (replace likes) -------------------------
    val Celebrate = Color(0xFFFFC24C)  // "Celebrate"
    val Relate = Color(0xFF8E86F7)     // "I relate"
    val Help = Color(0xFF4ADE80)       // "I can help"
    val Talk = Color(0xFF4D8DFF)       // "Let's talk"

    // --- Chat / message bubbles ------------------------------------------
    val BubbleInBg = Surface
    val BubbleInText = Ink
    val BubbleInMeta = InkMuted
    val BubbleOutText = Color(0xFFFFFFFF)
    val BubbleOutMeta = Color(0xFFDCE1FF)

    // --- Relationship layers (product layers, not hard limits) -----------
    enum class Layer(val label: String) {
        INNER("Inner"),
        CLOSE("Close"),
        FRIENDS("Friends"),
        ACQUAINTANCES("Acquaintances"),
        WIDER("Wider world"),
    }

    // --- Spacing scale ---------------------------------------------------
    val SpaceXs = 4.dp
    val SpaceS = 8.dp
    val SpaceM = 12.dp
    val SpaceL = 16.dp
    val SpaceXl = 20.dp
    val SpaceXxl = 28.dp

    // --- Corner radii ----------------------------------------------------
    val RadiusS = 12.dp
    val RadiusM = 16.dp
    val RadiusL = 20.dp
    val RadiusXl = 24.dp
    val RadiusPill = 100.dp
    val BgTint = Color(0xFF15161C)

    /** Soft cool avatar gradients (indigo/violet/blue/cyan family). */
    val AvatarGradients = listOf(
        Violet to Indigo,
        Indigo to Blue,
        Blue to Cyan,
        Color(0xFF7B74F0) to Color(0xFF4D8DFF),
        Color(0xFF5FE9FF) to Color(0xFF6C63FF),
    )

    /** Deterministic avatar gradient index for a name/id. */
    fun avatarIndex(key: String): Int {
        if (key.isEmpty()) return 0
        return (key.sumOf { it.code } + key.length) % AvatarGradients.size
    }
}
