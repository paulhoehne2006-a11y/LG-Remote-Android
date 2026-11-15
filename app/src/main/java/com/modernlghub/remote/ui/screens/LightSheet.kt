package com.modernlghub.remote.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.modernlghub.remote.data.SmartDevice
import com.modernlghub.remote.ui.components.RemoteSheet
import com.modernlghub.remote.ui.components.SectionLabel
import com.modernlghub.remote.ui.components.pressable
import com.modernlghub.remote.ui.components.rememberHaptics
import com.modernlghub.remote.ui.theme.Accent
import com.modernlghub.remote.ui.theme.AccentDim
import com.modernlghub.remote.ui.theme.Hairline
import com.modernlghub.remote.ui.theme.Surface2
import com.modernlghub.remote.ui.theme.Surface3
import com.modernlghub.remote.ui.theme.TextPrimary
import com.modernlghub.remote.ui.theme.TextSecondary
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
    0xFF3B1F, 0xFF8A00, 0xFFD400, 0x3BDC5A, 0x00D2FF, 0x2F5BFF, 0x9B3BFF, 0xFF3BA7,
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
    var on by remember { mutableStateOf(device.isOn) }
    var brightness by remember { mutableFloatStateOf((device.brightness ?: 100).coerceIn(10, 100).toFloat()) }
    // null = the user hasn't picked a colour/white yet, so the lamp keeps whatever it shows now.
    var mode by remember { mutableStateOf<LightMode?>(null) }
    val shownMode = mode ?: if (device.kelvin != null || !device.canColor) LightMode.White else LightMode.Color
    var hue by remember { mutableFloatStateOf(device.rgb?.let(::hueOf) ?: 30f) }
    // 1 = full colour, 0 = white; in between are pastel tones
    var sat by remember { mutableFloatStateOf(device.rgb?.let(::saturationOf)?.takeIf { it > 0.05f } ?: 1f) }
    var kelvin by remember { mutableIntStateOf((device.kelvin ?: 2700).coerceIn(SmartDevice.KELVIN_MIN, SmartDevice.KELVIN_MAX)) }

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
        shownMode == LightMode.White && device.canWhite -> rgbColor(SmartDevice.kelvinToRgb(kelvin))
        mode == LightMode.Color -> rgbColor(hsvToRgb(hue, sat))
        else -> device.rgb?.let(::rgbColor) ?: rgbColor(SmartDevice.kelvinToRgb(2700))
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                if (device.room.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(device.room, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
            Switch(
                checked = on,
                onCheckedChange = { on = it; haptics.confirm(); send() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = Surface2,
                    uncheckedBorderColor = Hairline,
                ),
            )
        }
        Spacer(Modifier.height(24.dp))

        Column(Modifier.alpha(if (on) 1f else 0.55f)) {
            if (device.canDim) {
                LabelRow("Helligkeit", "${brightness.roundToInt()} %")
                GradientSlider(
                    value = brightness,
                    range = 10f..100f,
                    brush = Brush.horizontalGradient(listOf(lampColor.copy(alpha = 0.18f), lampColor)),
                    thumbColor = lampColor,
                    description = "Helligkeit",
                    onValueChange = { v -> adjust { brightness = v } },
                    onFinished = { haptics.tick() },
                )
                Spacer(Modifier.height(20.dp))
            }

            if (device.canColor) {
                SectionLabel("Farbe")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PRESET_COLORS.forEach { rgb ->
                        val selected = shownMode == LightMode.Color && abs(hueOf(rgb) - hue) < 4f && sat > 0.95f
                        Swatch(rgbColor(rgb), selected, Modifier.weight(1f)) {
                            haptics.tick()
                            adjust { mode = LightMode.Color; hue = hueOf(rgb); sat = 1f }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                GradientSlider(
                    value = hue,
                    range = 0f..360f,
                    brush = Brush.horizontalGradient((0..6).map { rgbColor(hueToRgb(it * 60f)) }),
                    thumbColor = rgbColor(hueToRgb(hue)),
                    description = "Farbton",
                    onValueChange = { v -> adjust { mode = LightMode.Color; hue = v } },
                    onFinished = { haptics.tick() },
                )
                Spacer(Modifier.height(14.dp))
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
                Spacer(Modifier.height(20.dp))
            }

            if (device.canWhite) {
                LabelRow("Weißlicht", "$kelvin K")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    WHITE_PRESETS.forEach { (label, k) ->
                        val selected = shownMode == LightMode.White && abs(kelvin - k) < 150
                        PresetChip(label, rgbColor(SmartDevice.kelvinToRgb(k)), selected, Modifier.weight(1f)) {
                            haptics.tick()
                            adjust { mode = LightMode.White; kelvin = k }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
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
private fun LabelRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        SectionLabel(label, Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
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
                    .clip(RoundedCornerShape(7.dp))
                    .background(brush)
                    .border(1.dp, Hairline, RoundedCornerShape(7.dp))
            )
        },
    )
}

@Composable
private fun Swatch(color: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .widthIn(max = 44.dp)
            .aspectRatio(1f)
            .clip(CircleShape)
            .border(2.dp, if (selected) Color.White else Color.Transparent, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(color)
            .pressable(onClick = onClick)
    )
}

@Composable
private fun PresetChip(label: String, tone: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (selected) AccentDim else Surface2)
            .border(1.dp, if (selected) Accent else Hairline, shape)
            .pressable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(tone)
                .border(1.dp, Surface3, CircleShape)
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
