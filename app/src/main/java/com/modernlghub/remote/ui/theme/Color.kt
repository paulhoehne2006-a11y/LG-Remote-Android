package com.modernlghub.remote.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * "Obsidian" palette, made for an OLED panel.
 *
 * - Background is true black; depth comes from tone (Surface1 < Surface2 < Surface3), never from shadows.
 * - One accent. Everything else that has colour takes it from content: the running app's brand colour,
 *   the lamp's light colour (see [ContentTint]).
 * - Text steps all pass WCAG AA (4.5:1) on black and on Surface1.
 */

// Tonal elevation: background → card → control on a card → raised/pressed.
val Obsidian = Color(0xFF000000)
val Surface1 = Color(0xFF111113)
val Surface2 = Color(0xFF1B1B1F)
val Surface3 = Color(0xFF26262B)
val Hairline = Color(0xFF2C2C32)

val TextPrimary = Color(0xFFF4F4F6)   // 18.6:1 on black
val TextSecondary = Color(0xFFA6A6AE) //  8.4:1
val TextTertiary = Color(0xFF85858E)  //  5.6:1 (was 6B6B73 = 3.9:1, below AA for small labels)

/** The single accent: a cool, slightly luminous blue that reads well on black. */
val Accent = Color(0xFF8AB4FF)
val AccentDim = Accent.copy(alpha = 0.16f)
val AccentLine = Accent.copy(alpha = 0.38f)
/** Content on a filled accent surface. */
val OnAccent = Color(0xFF07152E)

val StatusOn = Color(0xFF4ADE80)
val StatusWarn = Color(0xFFFBBF24)
val StatusOff = Color(0xFFF87171)

val Scrim = Color.Black.copy(alpha = 0.6f)

/** Alpha for disabled content (Material's 38 %). */
const val DisabledAlpha = 0.38f
