package com.securemessage.app.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Legacy token names, re-pointed to the WEAVE palette (see Weave.kt).
// "Warm Sunset" palette (historical) — friendly, cozy, human.
//
// Two moods share one language: a cream / peach LIGHT mode and a warm charcoal
// DARK mode, both lit by a coral -> amber -> pink sunset accent.
//
// The top-level tokens below (TextPrimary, ObsidianVoid, TgBlue, ...) keep their
// historical NAMES so every screen that already references them keeps compiling.
// Their VALUES now resolve to the dark-mode warm palette, which is the app's
// default mood. Light-mode equivalents live in the `Warm*` tokens and are wired
// up through MaterialTheme in Theme.kt.
// ---------------------------------------------------------------------------

val PureWhite = Color(0xFFFFFFFF)
val PureBlack = Color(0xFF000000)

// --- Sunset accent ramp (shared by both moods) ----------------------------
// Coral -> amber -> pink. These are the heart of the brand.
val SunsetCoral = Color(0xFFFF6F61)   // warm red-coral
val SunsetAmber = Color(0xFFFFB24C)   // golden amber
val SunsetPink = Color(0xFFFF8FA3)    // soft rose-pink
val SunsetPeach = Color(0xFFFFD2A6)   // pale peach highlight
val SunsetDeep = Color(0xFFE2574C)    // pressed / deep coral
val SunsetGlow = Color(0xFFFFC98A)    // warm glow for shadows & rings

// =========================================================================
// DARK MOOD — "warm charcoal at dusk" (the default; drives the legacy tokens)
// =========================================================================

// Window / page backgrounds — warm charcoal, never cold grey.
val ObsidianVoid = Color(0xFF0C0D11)           // page background (warm near-black)
val ObsidianSurface = Color(0xFF121318)        // dim surface
val ObsidianSurfaceElevated = Color(0xFF1F2027) // elevated surface
val ObsidianCard = Color(0xFF17181E)           // card / sheet
val ObsidianCardHover = Color(0xFF282A32)      // hovered / filled chip

// Hairline structural dividers and borders — warm, low contrast.
val HairlineBorder = Color(0xFF2A2B34)
val HairlineBorderSubtle = Color(0xFF1C1D24)
val HairlineBorderBright = Color(0xFF3A3C48)

// Typography & iconography — warm whites and taupes, never pure grey.
val TextPrimary = Color(0xFFF2F3FA)
val TextSecondary = Color(0xFFC3C6D4)
val TextMuted = Color(0xFF868AA0)
val TextDark = Color(0xFF2A1D18)

// Bottom navigation dock (dark mood).
val DockGlassBackground = Color(0xF22B211E)
val DockGlassBorder = Color(0x00000000)
val DockPillActive = Color(0xFF43302A)
val DockPillActiveText = SunsetCoral
val DockPillInactiveText = Color(0xFFAE8F84)

// Accent — the single most-used tint across the app. Kept named `TgBlue` so the
// hundreds of call sites keep working, but it is now sunset coral.
val TgBlue = Color(0xFF8F87FF)
val TgBluePressed = Color(0xFF6A61F0)
val TgBlueLight = SunsetPink
val TgLink = Color(0xFFFFA07A)

// Chat bubbles — outgoing rides the sunset ramp, incoming is a warm surface.
val TgBubbleOut = SunsetCoral
val TgBubbleOutEnd = SunsetAmber               // gradient end for outgoing bubbles
val TgBubbleIn = Color(0xFF2E221F)
val TgBubbleInTime = Color(0xFFB0968C)
val TgBubbleOutTime = Color(0xFFFFF0E6)

// Chat wallpaper (dark mood sunset).
val TgWallpaperBase = Color(0xFF1A1413)
val TgWallpaperTop = Color(0xFF3A211C)         // warm top of the sunset gradient
val TgWallpaperBottom = Color(0xFF171011)      // deep base of the sunset gradient
val TgWallpaperInk = Color(0x1FFFC98A)         // faint warm doodles

// Avatars / online status.
val TgOnline = Color(0xFF5FD08A)               // friendly green that reads warm
val TgAvatarNeutral = Color(0xFF4D3A34)

// Settings icon tile colours — kept warm & friendly.
val SettingsIconBlue = Color(0xFFF4A32B)
val SettingsIconOrange = SunsetCoral
val SettingsIconGreen = Color(0xFF5FD08A)
val SettingsIconRed = SunsetDeep
val SettingsIconIndigo = Color(0xFFC98BDB)

val TgErrorRed = Color(0xFFF2645B)

// =========================================================================
// LIGHT MOOD — "cream & peach in morning light"
// Consumed through MaterialTheme (Theme.kt). Screens that read raw tokens still
// show the dark mood; the warm light scheme covers Material-driven surfaces.
// =========================================================================

val WarmBackground = Color(0xFFFFF6EF)         // soft cream page
val WarmSurface = Color(0xFFFFFBF7)            // near-white warm surface
val WarmSurfaceElevated = Color(0xFFFFF1E6)    // elevated peach
val WarmCard = Color(0xFFFFFFFF)
val WarmCardHover = Color(0xFFFCE9DC)

val WarmHairline = Color(0xFFF0D9C8)
val WarmHairlineSubtle = Color(0xFFF7E7DA)
val WarmHairlineBright = Color(0xFFE7C6B2)

val WarmTextPrimary = Color(0xFF3A2A24)        // warm espresso
val WarmTextSecondary = Color(0xFF8A6F63)
val WarmTextMuted = Color(0xFFB49A8D)

val WarmBubbleIn = Color(0xFFFFFFFF)
val WarmBubbleInTime = Color(0xFFB49A8D)

val WarmWallpaperTop = Color(0xFFFFE7D4)       // peachy top
val WarmWallpaperBottom = Color(0xFFFFF6EF)    // cream base
val WarmWallpaperInk = Color(0x14E2574C)       // faint coral doodles
