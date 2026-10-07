package com.securemessage.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Warm charcoal dusk — the app's signature mood.
private val WarmDarkColors = darkColorScheme(
    primary = TgBlue,
    onPrimary = PureWhite,
    primaryContainer = SunsetDeep,
    onPrimaryContainer = PureWhite,
    inversePrimary = SunsetAmber,
    secondary = SunsetAmber,
    onSecondary = TextDark,
    secondaryContainer = ObsidianSurfaceElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = SunsetPink,
    onTertiary = TextDark,
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = ObsidianVoid,
    onSurface = TextPrimary,
    surfaceDim = ObsidianSurface,
    surfaceBright = ObsidianCardHover,
    surfaceContainerLowest = ObsidianSurface,
    surfaceContainerLow = ObsidianVoid,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianCardHover,
    surfaceContainerHighest = HairlineBorder,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    inverseSurface = WarmTextPrimary,
    inverseOnSurface = PureWhite,
    outline = HairlineBorderBright,
    outlineVariant = HairlineBorderSubtle,
    error = TgErrorRed,
    onError = PureWhite,
    errorContainer = ObsidianCardHover,
    onErrorContainer = PureWhite,
)

// Cream & peach morning — the friendly light mood.
private val WarmLightColors = lightColorScheme(
    primary = SunsetCoral,
    onPrimary = PureWhite,
    primaryContainer = SunsetPeach,
    onPrimaryContainer = WarmTextPrimary,
    inversePrimary = SunsetAmber,
    secondary = SunsetAmber,
    onSecondary = WarmTextPrimary,
    secondaryContainer = WarmSurfaceElevated,
    onSecondaryContainer = WarmTextPrimary,
    tertiary = SunsetPink,
    onTertiary = PureWhite,
    background = WarmBackground,
    onBackground = WarmTextPrimary,
    surface = WarmBackground,
    onSurface = WarmTextPrimary,
    surfaceDim = WarmSurfaceElevated,
    surfaceBright = WarmSurface,
    surfaceContainerLowest = WarmSurface,
    surfaceContainerLow = WarmBackground,
    surfaceContainer = WarmSurfaceElevated,
    surfaceContainerHigh = WarmCardHover,
    surfaceContainerHighest = WarmHairline,
    surfaceVariant = WarmSurfaceElevated,
    onSurfaceVariant = WarmTextSecondary,
    inverseSurface = ObsidianVoid,
    inverseOnSurface = TextPrimary,
    outline = WarmHairlineBright,
    outlineVariant = WarmHairlineSubtle,
    error = TgErrorRed,
    onError = PureWhite,
    errorContainer = WarmCardHover,
    onErrorContainer = SunsetDeep,
)

/**
 * Warm Sunset theme.
 *
 * Defaults to the warm charcoal dusk mood, which the screens paint with directly through the raw
 * palette tokens, so the app is cohesive out of the box. The cream & peach light scheme is fully
 * defined and can be opted into by passing `darkTheme = false`. Dynamic color stays off on purpose
 * — the sunset palette is the brand and shouldn't be overridden by wallpaper-derived Material You
 * colours.
 */
@Composable
fun SecureMessageTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) WarmDarkColors else WarmLightColors,
        typography = AppTypography,
        content = content,
    )
}
