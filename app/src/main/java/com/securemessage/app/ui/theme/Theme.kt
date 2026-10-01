package com.securemessage.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MonochromeDarkColors = darkColorScheme(
    primary = PureWhite,
    onPrimary = PureBlack,
    primaryContainer = PureWhite,
    onPrimaryContainer = PureBlack,
    inversePrimary = PureBlack,
    secondary = TextSecondary,
    onSecondary = PureBlack,
    secondaryContainer = ObsidianCard,
    onSecondaryContainer = PureWhite,
    tertiary = TextMuted,
    onTertiary = PureWhite,
    background = ObsidianVoid,
    onBackground = PureWhite,
    surface = ObsidianSurface,
    onSurface = PureWhite,
    surfaceDim = ObsidianVoid,
    surfaceBright = ObsidianSurfaceElevated,
    surfaceContainerLowest = ObsidianVoid,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianCard,
    surfaceContainerHighest = HairlineBorder,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = TextSecondary,
    inverseSurface = PureWhite,
    inverseOnSurface = PureBlack,
    outline = HairlineBorder,
    outlineVariant = HairlineBorderSubtle,
    error = PureWhite,
    onError = PureBlack,
    errorContainer = ObsidianCard,
    onErrorContainer = PureWhite,
)

@Composable
fun SecureMessageTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false, // Strictly false to preserve pure black and white monochromatic theme
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = MonochromeDarkColors,
        typography = AppTypography,
        content = content,
    )
}
