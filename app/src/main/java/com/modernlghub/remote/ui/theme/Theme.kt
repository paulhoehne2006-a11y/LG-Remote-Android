package com.modernlghub.remote.ui.theme

import android.provider.Settings
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private val ObsidianScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentDim,
    onPrimaryContainer = Accent,
    secondary = TextSecondary,
    onSecondary = Obsidian,
    secondaryContainer = Surface3,
    onSecondaryContainer = TextPrimary,
    tertiary = Accent,
    background = Obsidian,
    onBackground = TextPrimary,
    surface = Obsidian,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    surfaceTint = Obsidian, // no tint overlays: elevation is expressed by the surface steps alone
    surfaceBright = Surface3,
    surfaceDim = Obsidian,
    surfaceContainerLowest = Obsidian,
    surfaceContainerLow = Surface1,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface3,
    outline = Hairline,
    outlineVariant = Hairline,
    error = StatusOff,
    onError = Obsidian,
    inverseSurface = Surface3,
    inverseOnSurface = TextPrimary,
    inversePrimary = Accent,
    scrim = Obsidian,
)

@Composable
fun ModernLgTheme(reduceMotion: Boolean? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduce = reduceMotion ?: remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    MaterialTheme(colorScheme = ObsidianScheme, typography = Typography, shapes = AppShapes) {
        // Default text/icon colour everywhere, also outside Scaffold/Surface (overlay, sheets).
        CompositionLocalProvider(
            LocalContentColor provides TextPrimary,
            LocalReduceMotion provides reduce,
            content = content,
        )
    }
}
