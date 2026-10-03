package com.securemessage.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TelegramDarkColors = darkColorScheme(
    primary = TgBlue,
    onPrimary = PureWhite,
    primaryContainer = TgBlue,
    onPrimaryContainer = PureWhite,
    inversePrimary = TgBlue,
    secondary = TgBlueLight,
    onSecondary = PureBlack,
    secondaryContainer = ObsidianSurfaceElevated,
    onSecondaryContainer = PureWhite,
    tertiary = TgLink,
    onTertiary = PureBlack,
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
    inverseSurface = PureWhite,
    inverseOnSurface = PureBlack,
    outline = HairlineBorderBright,
    outlineVariant = HairlineBorderSubtle,
    error = TgErrorRed,
    onError = PureWhite,
    errorContainer = ObsidianCardHover,
    onErrorContainer = PureWhite,
)

@Composable
fun SecureMessageTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false, // Strictly false to preserve the Telegram dark palette
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = TelegramDarkColors,
        typography = AppTypography,
        content = content,
    )
}