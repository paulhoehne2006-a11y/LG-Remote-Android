package com.modernlghub.remote.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.modernlghub.remote.data.TvPreferences

/**
 * Haptics built on Android's composition primitives, which the Galaxy S-series renders with
 * its linear motor: a light TICK for swipe steps, CLICK for buttons, THUD for power.
 * Older devices fall back to the predefined effects.
 */
class Haptics(context: Context) {
    private val prefs = TvPreferences(context)
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private enum class Kind(val predefined: Int) {
        TICK(VibrationEffect.EFFECT_TICK),
        CLICK(VibrationEffect.EFFECT_CLICK),
        HEAVY(VibrationEffect.EFFECT_HEAVY_CLICK),
        CONFIRM(VibrationEffect.EFFECT_DOUBLE_CLICK),
    }

    private fun play(kind: Kind) {
        val v = vibrator ?: return
        if (!prefs.hapticFeedbackEnabled) return
        runCatching { v.vibrate(composed(v, kind) ?: VibrationEffect.createPredefined(kind.predefined)) }
    }

    private fun composed(v: Vibrator, kind: Kind): VibrationEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val (primitive, scale) = when (kind) {
            Kind.TICK -> VibrationEffect.Composition.PRIMITIVE_TICK to 0.7f
            Kind.CLICK -> VibrationEffect.Composition.PRIMITIVE_CLICK to 0.8f
            Kind.HEAVY -> VibrationEffect.Composition.PRIMITIVE_THUD to 1f
            Kind.CONFIRM -> VibrationEffect.Composition.PRIMITIVE_QUICK_RISE to 0.6f
        }
        if (!v.areAllPrimitivesSupported(primitive)) return null
        return VibrationEffect.startComposition().addPrimitive(primitive, scale).compose()
    }

    fun tick() = play(Kind.TICK)
    fun click() = play(Kind.CLICK)
    fun heavy() = play(Kind.HEAVY)
    fun confirm() = play(Kind.CONFIRM)
}

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember(context) { Haptics(context.applicationContext) }
}
