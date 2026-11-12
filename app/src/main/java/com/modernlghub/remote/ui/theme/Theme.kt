package com.modernlghub.remote.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val ObsidianScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Obsidian,
    primaryContainer = AccentDim,
    onPrimaryContainer = Accent,
    secondary = TextSecondary,
    onSecondary = Obsidian,
    background = Obsidian,
    onBackground = TextPrimary,
    surface = Obsidian,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Obsidian,
    surfaceContainerLow = Surface1,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface3,
    outline = Hairline,
    outlineVariant = Hairline,
    error = StatusOff,
    inverseSurface = TextPrimary,
    inverseOnSurface = Obsidian,
)

@Composable
fun ModernLgTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ObsidianScheme, typography = Typography) {
        // Default text/icon colour everywhere, also outside Scaffold/Surface (overlay, sheets).
        CompositionLocalProvider(LocalContentColor provides TextPrimary, content = content)
    }
}
