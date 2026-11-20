package com.modernlghub.remote.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** 4-dp spacing scale. [Gutter] is the screen edge inset used everywhere. */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val Gutter = 20.dp

    /** Widest a single column of content gets on tablets, foldables and in landscape. */
    val ContentMaxWidth = 560.dp
}

/** Named shapes. Pills for anything you tap inline, soft rectangles for surfaces. */
object Shape {
    val Pill = CircleShape
    val Control = RoundedCornerShape(16.dp)
    val Tile = RoundedCornerShape(22.dp)
    val Card = RoundedCornerShape(24.dp)
    val Pad = RoundedCornerShape(32.dp)
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

/** Minimum touch target (Material / Android accessibility guideline). */
val MinTouch = 48.dp

internal val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = Shape.Control,
    large = Shape.Card,
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Motion: short and calm. Everything uses these durations so the app moves as one thing.
 * With "Animationen entfernen" (animator scale 0) the system already zeroes Compose animations;
 * [LocalReduceMotion] additionally switches off ambient loops (pulsing dots, glows).
 */
object Motion {
    const val Fast = 120
    const val Medium = 220
    const val Slow = 380
    const val Ambient = 800

    /** Material "emphasized decelerate": things arriving. */
    val Enter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** Material "standard": state changes in place. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Things leaving. */
    val Exit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

val LocalReduceMotion = staticCompositionLocalOf { false }

/** A tween that collapses to a snap when the user asked for reduced motion. */
fun <T> motionSpec(reduce: Boolean, durationMs: Int = Motion.Medium, easing: Easing = Motion.Standard): FiniteAnimationSpec<T> =
    if (reduce) snap() else tween(durationMs, easing = easing)

/** [motionSpec] for the current [LocalReduceMotion]. */
@Composable
@ReadOnlyComposable
fun <T> motion(durationMs: Int = Motion.Medium, easing: Easing = Motion.Standard): FiniteAnimationSpec<T> =
    motionSpec(LocalReduceMotion.current, durationMs, easing)
