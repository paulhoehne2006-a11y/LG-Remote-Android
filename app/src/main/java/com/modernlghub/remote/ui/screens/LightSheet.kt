package com.modernlghub.remote.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.modernlghub.remote.data.SmartDevice
import com.modernlghub.remote.ui.components.RemoteSheet
import com.modernlghub.remote.ui.components.pressable
import com.modernlghub.remote.ui.components.rememberHaptics
import com.modernlghub.remote.ui.theme.Accent
import com.modernlghub.remote.ui.theme.Hairline
import com.modernlghub.remote.ui.theme.Motion
import com.modernlghub.remote.ui.theme.OnAccent
import com.modernlghub.remote.ui.theme.Shape
import com.modernlghub.remote.ui.theme.Spacing
import com.modernlghub.remote.ui.theme.Surface1
import com.modernlghub.remote.ui.theme.Surface2
import com.modernlghub.remote.ui.theme.TextPrimary
import com.modernlghub.remote.ui.theme.TextSecondary
import com.modernlghub.remote.ui.theme.motion
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The full wanted state of a lamp. Each change carries everything the user set in the sheet,
 * so the screen can drop intermediate slider positions and still end up right.
 * [color] and [kelvin] are only set once the user picked a colour or a white tone.
 */
data class LightChange(
    val on: Boolean,
    val brightness: Int? = null,
    val color: String? = null,
    val kelvin: Int? = null,
)

private enum class LightMode { Color, White }

private val PRESET_COLORS = listOf(
    0xFF3B1F to "Rot", 0xFF8A00 to "Orange", 0xFFD400 to "Gelb", 0x3BDC5A to "Grün",
    0x00D2FF to "Cyan", 0x2F5BFF to "Blau", 0x9B3BFF to "Violett", 0xFF3BA7 to "Pink",
)

private val WHITE_PRESETS = listOf("Warm" to 2700, "Neutral" to 4000, "Tageslicht" to 6500)

@Composable
fun LightSheet(device: SmartDevice, onChange: (LightChange) -> Unit, onDismiss: () -> Unit) {
    RemoteSheet(onDismiss) { LightControls(device, onChange) }
}

/** Switch, brightness, colour and white tone for one lamp (the body of [LightSheet]). */
@Composable
fun LightControls(device: SmartDevice, onChange: (LightChange) -> Unit) {
    val haptics = rememberHaptics()
    // Saveable: rotating the phone with the sheet open keeps what was set.
    var on by rememberSaveable(device.id) { mutableStateOf(device.isOn) }
    var brightness by rememberSaveable(device.id) { mutableFloatStateOf((device.brightness ?: 100).coerceIn(10, 100).toFloat()) }
    // null = nothing picked yet, so the lamp keeps whatever colour it shows now.
    var mode by rememberSaveable(device.id) { mutableStateOf<LightMode?>(null) }
    var tab by rememberSaveable(device.id) {
        mutableStateOf(if (device.kelvin != null || !device.canColor) LightMode.White else LightMode.Color)
    }
    var hue by rememberSaveable(device.id) { mutableFloatStateOf(device.rgb?.let(::hueOf) ?: 30f) }
    // 1 = full colour, 0 = white; in between are pastel tones.
    var sat by rememberSaveable(device.id) { mutableFloatStateOf(device.rgb?.let(::saturationOf)?.takeIf { it > 0.05f } ?: 1f) }
    var kelvin by rememberSaveable(device.id) { mutableIntStateOf((device.kelvin ?: 2700).coerceIn(SmartDevice.KELVIN_MIN, SmartDevice.KELVIN_MAX)) }

    fun send() {
        onChange(
            LightChange(
                on = on,
                brightness = if (on && device.canDim) brightness.roundToInt() else null,
                color = if (on && mode == LightMode.Color) SmartDevice.toHexColor(hsvToRgb(hue, sat)) else null,
                kelvin = if (on && mode == LightMode.White) kelvin else null,
            )
        )
    }

    /** Any adjustment switches the lamp on, like the WiZ app does. */
    fun adjust(block: () -> Unit) {
        block()
        on = true
        send()
    }

    val lampColor = when {
        mode == LightMode.White -> rgbColor(SmartDevice.kelvinToRgb(kelvin))
        mode == LightMode.Color -> rgbColor(hsvToRgb(hue, sat))
        else -> device.rgb?.let(::rgbColor) ?: rgbColor(SmartDevice.kelvinToRgb(kelvin))
    }
    val orb by animateColorAsState(if (on) lampColor else Surface2, motion(Motion.Medium), label = "orb")
    val showTabs = device.canColor && device.canWhite

    Column {
        // Lamp orb + name + switch.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(56.dp)
                    .drawBehind {
                        if (on) drawCircle(Brush.radialGradient(listOf(orb.copy(alpha = 0.45f * (brightness / 100f) + 0.15f), Color.Transparent), radius = size.minDimension))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(orb), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Lightbulb, null,
                        tint = if (on && orb.luminance() > 0.35f) Color.Black else if (on) Color.White else TextSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary, modifier = Modifier.semantics { heading() })
                Text(
                    listOfNotNull(device.room.takeIf { it.isNotBlank() }, if (on) "An" else "Aus").joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            Switch(
                checked = on,
                onCheckedChange = { on = it; haptics.confirm(); send() },
                modifier = Modifier.semantics { contentDescription = device.name },
                colors = switchColors(),
            )
        }
        Spacer(Modifier.height(Spacing.xl))

        Column(Modifier.graphicsLayer { alpha = if (on) 1f else 0.55f }) {
            if (device.canDim) {
                LabelRow("Helligkeit", "${brightness.roundToInt()} %")
                GradientSlider(
                    value = brightness,
                    range = 10f..100f,
                    brush = Brush.horizontalGradient(listOf(lampColor.copy(alpha = 0.18f), lampColor)),
                    thumbColor = lampColor,
                    description = "Helligkeit",
                    onValueChange = { v ->
                        // A light detent every 10 %.
                        if ((v / 10f).toInt() != (brightness / 10f).toInt()) haptics.light()
                        adjust { brightness = v }
                    },
                    onFinished = { haptics.tick() },
                )
                Spacer(Modifier.height(Spacing.xl))
            }

            if (showTabs) {
                Segmented(
                    options = listOf(LightMode.Color to "Farbe", LightMode.White to "Weißlicht"),
                    selected = tab,
                    onSelect = { haptics.tick(); tab = it },
                )
                Spacer(Modifier.height(Spacing.l + Spacing.xs))
            }

            if (device.canColor && (tab == LightMode.Color || !device.canWhite)) {
                if (!showTabs) LabelRow("Farbe", null)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
                    PRESET_COLORS.forEach { (rgb, name) ->
                        val selected = mode == LightMode.Color && hueDistance(hueOf(rgb), hue) < 4f && sat > 0.95f
                        Swatch(rgbColor(rgb), name, selected, Modifier.weight(1f)) {
                            haptics.tick()
                            adjust { mode = LightMode.Color; hue = hueOf(rgb); sat = 1f }
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.l))
                LabelRow("Farbton", null)
                GradientSlider(
                    value = hue,
                    range = 0f..360f,
                    brush = Brush.horizontalGradient((0..6).map { rgbColor(hueToRgb(it * 60f)) }),
                    thumbColor = rgbColor(hueToRgb(hue)),
                    description = "Farbton",
                    onValueChange = { v -> adjust { mode = LightMode.Color; hue = v } },
                    onFinished = { haptics.tick() },
                )
                Spacer(Modifier.height(Spacing.m))
                LabelRow("Sättigung", "${(sat * 100).roundToInt()} %")
                GradientSlider(
                    value = sat,
                    range = 0f..1f,
                    brush = Brush.horizontalGradient(listOf(Color.White, rgbColor(hueToRgb(hue)))),
                    thumbColor = rgbColor(hsvToRgb(hue, sat)),
                    description = "Sättigung",
                    onValueChange = { v -> adjust { mode = LightMode.Color; sat = (v * 100f).roundToInt() / 100f } },
                    onFinished = { haptics.tick() },
                )
            }

            if (device.canWhite && (tab == LightMode.White || !device.canColor)) {
                LabelRow(if (showTabs) "Farbtemperatur" else "Weißlicht", "$kelvin K")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
                    WHITE_PRESETS.forEach { (label, k) ->
                        val selected = mode == LightMode.White && abs(kelvin - k) < 150
                        PresetChip(label, rgbColor(SmartDevice.kelvinToRgb(k)), selected, Modifier.weight(1f)) {
                            haptics.tick()
                            adjust { mode = LightMode.White; kelvin = k }
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.m))
                GradientSlider(
                    value = kelvin.toFloat(),
                    range = SmartDevice.KELVIN_MIN.toFloat()..SmartDevice.KELVIN_MAX.toFloat(),
                    brush = Brush.horizontalGradient(
                        listOf(2200, 3000, 4000, 5000, 6500).map { rgbColor(SmartDevice.kelvinToRgb(it)) }
                    ),
                    thumbColor = rgbColor(SmartDevice.kelvinToRgb(kelvin)),
                    description = "Farbtemperatur",
                    onValueChange = { v -> adjust { mode = LightMode.White; kelvin = (v / 50f).roundToInt() * 50 } },
                    onFinished = { haptics.tick() },
                )
            }
        }
    }
}

@Composable
internal fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = OnAccent,
    checkedTrackColor = Accent,
    checkedBorderColor = Accent,
    uncheckedThumbColor = TextSecondary,
    uncheckedTrackColor = Surface2,
    uncheckedBorderColor = Hairline,
)

private fun hueDistance(a: Float, b: Float): Float {
    val d = abs(a - b) % 360f
    return if (d > 180f) 360f - d else d
}

@Composable
private fun LabelRow(label: String, value: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = MaterialTheme.typography.labelLarge, color = TextSecondary)
    }
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Shape.Pill)
            .background(Surface2)
            .padding(3.dp),
    ) {
        options.forEach { (value, label) ->
            val isSel = value == selected
            val bg by animateColorAsState(if (isSel) Surface1 else Color.Transparent, motion(Motion.Medium), label = "seg")
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .semantics { role = Role.Tab; this.selected = isSel }
                    .pressable(role = Role.Tab, pressedScale = 0.98f) { onSelect(value) }
                    .clip(Shape.Pill)
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (isSel) TextPrimary else TextSecondary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradientSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    brush: Brush,
    thumbColor: Color,
    description: String,
    onValueChange: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onFinished,
        valueRange = range,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        thumb = {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(thumbColor)
                    .border(3.dp, Color.White, CircleShape)
            )
        },
        track = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(Shape.Pill)
                    .background(brush)
                    .border(1.dp, Hairline, Shape.Pill)
            )
        },
    )
}

@Composable
private fun Swatch(color: Color, name: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .widthIn(max = 44.dp)
            .aspectRatio(1f)
            .semantics {
                contentDescription = name
                this.selected = selected
                if (selected) stateDescription = "Ausgewählt"
            }
            .pressable(onClick = onClick)
            .border(2.dp, if (selected) Color.White else Color.Transparent, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun PresetChip(label: String, tone: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .heightIn(min = 44.dp)
            .semantics { this.selected = selected }
            .pressable(onClick = onClick)
            .clip(Shape.Pill)
            .background(if (selected) Surface1 else Surface2)
            .border(1.dp, if (selected) TextSecondary else Hairline, Shape.Pill)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(tone)
        )
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) TextPrimary else TextSecondary, maxLines = 1)
    }
}

internal fun rgbColor(rgb: Int) = Color(0xFF000000.toInt() or (rgb and 0xFFFFFF))

/** Fully saturated colour at [hue] degrees, as 0xRRGGBB. */
internal fun hueToRgb(hue: Float): Int = Color.hsv(hue.coerceIn(0f, 360f) % 360f, 1f, 1f).toArgb() and 0xFFFFFF

/** Colour at [hue] degrees with saturation [sat] (0 = white, 1 = full colour) at full value, as 0xRRGGBB. */
internal fun hsvToRgb(hue: Float, sat: Float): Int =
    Color.hsv(hue.coerceIn(0f, 360f) % 360f, sat.coerceIn(0f, 1f), 1f).toArgb() and 0xFFFFFF

/** Saturation (0..1) of 0xRRGGBB. */
internal fun saturationOf(rgb: Int): Float {
    val r = (rgb shr 16 and 0xFF) / 255f
    val g = (rgb shr 8 and 0xFF) / 255f
    val b = (rgb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    return if (max == 0f) 0f else (max - minOf(r, g, b)) / max
}

/** Hue in degrees (0..360) of 0xRRGGBB. */
internal fun hueOf(rgb: Int): Float {
    val r = (rgb shr 16 and 0xFF) / 255f
    val g = (rgb shr 8 and 0xFF) / 255f
    val b = (rgb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val d = max - minOf(r, g, b)
    if (d == 0f) return 0f
    val h = when (max) {
        r -> ((g - b) / d).mod(6f)
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    }
    return h * 60f
}
