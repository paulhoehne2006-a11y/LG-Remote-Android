package com.modernlghub.remote.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * "Obsidian" palette: true black for the OLED panel, three neutral surface steps,
 * one accent, and two status colours. Nothing else.
 */
val Obsidian = Color(0xFF000000)
val Surface1 = Color(0xFF111113)
val Surface2 = Color(0xFF1A1A1E)
val Surface3 = Color(0xFF242429)
val Hairline = Color(0xFF2C2C32)

val TextPrimary = Color(0xFFF4F4F6)
val TextSecondary = Color(0xFFA0A0A8)
val TextTertiary = Color(0xFF6B6B73)

/** The single accent. A cool, slightly luminous blue that reads well on black. */
val Accent = Color(0xFF8AB4FF)
val AccentDim = Color(0xFF8AB4FF).copy(alpha = 0.16f)

val StatusOn = Color(0xFF4ADE80)
val StatusWarn = Color(0xFFFBBF24)
val StatusOff = Color(0xFFF87171)
