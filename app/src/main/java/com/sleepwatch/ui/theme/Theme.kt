package com.sleepwatch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Sage,
    onPrimary = Midnight,
    primaryContainer = SageContainer,
    onPrimaryContainer = SageLight,
    secondary = Amber,
    onSecondary = Midnight,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = AmberLight,
    tertiary = Coral,
    onTertiary = Color.White,
    tertiaryContainer = CoralContainer,
    onTertiaryContainer = CoralLight,
    background = Midnight,
    onBackground = TextPrimary,
    surface = DeepNavy,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = TextSecondary,
    outline = TextMuted,
    outlineVariant = DarkSurface,
    error = Coral,
    onError = Color.White,
    errorContainer = CoralContainer,
    onErrorContainer = CoralLight,
    inverseSurface = TextPrimary,
    inverseOnSurface = Midnight
)

private val LightColorScheme = lightColorScheme(
    primary = SageDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F5E9),
    onPrimaryContainer = SageDark,
    secondary = AmberDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF3E0),
    onSecondaryContainer = AmberDark,
    tertiary = CoralDark,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEBEE),
    onTertiaryContainer = CoralDark,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = LightTextSecondary,
    outline = LightTextSecondary,
    outlineVariant = LightDivider,
    error = CoralDark,
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = CoralDark
)

@Composable
fun SleepWatchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
