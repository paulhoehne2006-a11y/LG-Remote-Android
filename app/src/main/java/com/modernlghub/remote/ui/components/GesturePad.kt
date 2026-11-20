package com.modernlghub.remote.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.modernlghub.remote.ui.theme.Accent
import com.modernlghub.remote.ui.theme.Hairline
import com.modernlghub.remote.ui.theme.LocalReduceMotion
import com.modernlghub.remote.ui.theme.Motion
import com.modernlghub.remote.ui.theme.Shape
import com.modernlghub.remote.ui.theme.Surface1
import com.modernlghub.remote.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** How long OK must be held to count as a long press (the hub's own remote uses 600 ms). */
const val LONG_PRESS_MS = 550L

/** Pointer loops wake up this often to drive long press and auto-repeat while the finger rests. */
internal const val PAD_TICK_MS = 40L

enum class Direction(val key: String, val label: String, val icon: ImageVector) {
    UP("UP", "Hoch", Icons.Rounded.KeyboardArrowUp),
    DOWN("DOWN", "Runter", Icons.Rounded.KeyboardArrowDown),
    LEFT("LEFT", "Links", Icons.AutoMirrored.Rounded.KeyboardArrowLeft),
    RIGHT("RIGHT", "Rechts", Icons.AutoMirrored.Rounded.KeyboardArrowRight),
}

/** Tunables of the swipe pad, in dp / ms. Kept together so the feel can be tuned in one place. */
internal object SwipeFeel {
    /** Travel per step. */
    const val STEP_DP = 36f
    /** Swipe at least this many steps far, then rest, to start auto-repeat. */
    const val REPEAT_REACH_STEPS = 2.5f
    /** Finger jitter below this radius still counts as resting. */
    const val REST_RADIUS_DP = 6f
    /** Resting this long starts auto-repeat. */
    const val REST_MS = 300L
    /** Repeat interval right at the repeat reach … */
    const val REPEAT_SLOW_MS = 170L
    /** … and with the finger two steps further out (a joystick: further = faster). */
    const val REPEAT_FAST_MS = 75L
    /** Switching axis mid-swipe needs clearly more perpendicular travel than this many steps. */
    const val AXIS_SWITCH_STEPS = 1.5f
    /** Height of the band (centred on the pad) where the system back gesture is turned off. */
    const val GESTURE_EXCLUSION_DP = 200f
}

/**
 * Swipe surface in the style of a modern streaming remote:
 * - every ~36 dp of travel = one step; the axis locks after the first step, so a slightly
 *   diagonal swipe never produces stray perpendicular steps
 * - swipe far and keep the finger resting = continuous scrolling, faster the further out
 *   (the edge in that direction glows while it repeats)
 * - tap anywhere = OK, press and hold without moving = long OK (options menu on the hub)
 * - a gesture taken over by the system (back swipe from the screen edge) never sends OK
 * TalkBack: double tap = OK, long press = options, the four directions are custom actions.
 */
@Composable
fun GesturePad(
    modifier: Modifier = Modifier,
    tint: Color = Accent,
    showHint: Boolean = true,
    onDirection: (Direction) -> Unit,
    onSelect: () -> Unit,
    onSelectLong: (() -> Unit)? = null,
    /** Called when a held, repeating press ends: drop keys still waiting to be sent. */
    onRepeatEnd: () -> Unit = {},
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current
    val exclusionPx = with(LocalDensity.current) { SwipeFeel.GESTURE_EXCLUSION_DP.dp.toPx() }
    val currentOnDirection by rememberUpdatedState(onDirection)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnSelectLong by rememberUpdatedState(onSelectLong)
    val currentOnRepeatEnd by rememberUpdatedState(onRepeatEnd)

    // Read only in draw/layer lambdas: moving the finger redraws, it never recomposes.
    val touchPos = remember { mutableStateOf(Offset.Unspecified) }
    var repeating by remember { mutableStateOf<Direction?>(null) }
    val glow = remember { Animatable(0f) }
    val flashes = remember { Direction.entries.associateWith { Animatable(0f) } }

    fun flash(dir: Direction) {
        scope.launch {
            val a = flashes.getValue(dir)
            a.snapTo(1f)
            if (!reduceMotion) a.animateTo(0f, tween(360, easing = Motion.Standard)) else a.snapTo(0f)
        }
    }

    fun setTouching(on: Boolean) {
        scope.launch {
            if (reduceMotion) glow.snapTo(if (on) 1f else 0f)
            else glow.animateTo(if (on) 1f else 0f, tween(if (on) Motion.Fast else Motion.Slow))
        }
    }

    Box(
        modifier = modifier
            .clip(Shape.Pad)
            .background(Surface1)
            .border(1.dp, Hairline, Shape.Pad)
            .systemGestureExclusion { coords ->
                val band = exclusionPx
                val h = coords.size.height.toFloat()
                val top = ((h - band) / 2f).coerceAtLeast(0f)
                Rect(0f, top, coords.size.width.toFloat(), (top + band).coerceAtMost(h))
            }
            .semantics {
                contentDescription = "Wischfeld"
                stateDescription = "Wischen zum Navigieren"
                onClick(label = "OK") { currentOnSelect(); true }
                if (onSelectLong != null) onLongClick(label = "Optionen") { currentOnSelectLong?.invoke(); true }
                customActions = Direction.entries.map { d ->
                    CustomAccessibilityAction(d.label) { currentOnDirection(d); true }
                }
            }
            .drawBehind {
                val p = touchPos.value
                val g = glow.value
                if (g > 0f && p.isSpecified) {
                    val r = 150.dp.toPx()
                    drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.22f * g), Color.Transparent), center = p, radius = r), r, p)
                }
                // Steady glow along the edge that is auto-repeating.
                repeating?.let { d ->
                    val depth = 64.dp.toPx()
                    val c = tint.copy(alpha = 0.26f)
                    val brush = when (d) {
                        Direction.UP -> Brush.verticalGradient(listOf(c, Color.Transparent), 0f, depth)
                        Direction.DOWN -> Brush.verticalGradient(listOf(Color.Transparent, c), size.height - depth, size.height)
                        Direction.LEFT -> Brush.horizontalGradient(listOf(c, Color.Transparent), 0f, depth)
                        Direction.RIGHT -> Brush.horizontalGradient(listOf(Color.Transparent, c), size.width - depth, size.width)
                    }
                    drawRect(brush)
                }
            }
            .pointerInput(Unit) {
                val step = SwipeFeel.STEP_DP.dp.toPx()
                val repeatReach = step * SwipeFeel.REPEAT_REACH_STEPS
                val restRadius = SwipeFeel.REST_RADIUS_DP.dp.toPx()
                val slop = viewConfiguration.touchSlop

                fun fire(dir: Direction, auto: Boolean) {
                    if (auto) haptics.light() else haptics.tick()
                    flash(dir)
                    currentOnDirection(dir)
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    touchPos.value = down.position
                    setTouching(true)
                    var dragging = false
                    var acc = Offset.Zero
                    var horizontalAxis: Boolean? = null
                    var lastDir: Direction? = null
                    var anchor = down.position
                    val start = down.uptimeMillis
                    // Event times when events arrive, ticks while the finger rests (works on both
                    // the device clock and a test clock).
                    var now = start
                    var lastMove = start
                    var lastStep = start
                    var longFired = false
                    try {
                        while (true) {
                            val event = withTimeoutOrNull(PAD_TICK_MS) { awaitPointerEvent() }
                            if (event != null) {
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                now = maxOf(now, change.uptimeMillis)
                                if (!change.pressed) {
                                    // A cancel (system took the gesture) arrives as an already consumed release.
                                    val cancelled = change.isConsumed
                                    if (!cancelled && !dragging && !longFired) {
                                        haptics.click()
                                        currentOnSelect()
                                    }
                                    break
                                }
                                touchPos.value = change.position
                                if (longFired) {
                                    change.consume()
                                    continue
                                }
                                val travel = change.position - down.position
                                if (!dragging && travel.getDistance() > slop) {
                                    dragging = true
                                    // Count the travel beyond the slop, so the first step is not late.
                                    acc = travel - travel / travel.getDistance() * slop
                                } else if (dragging) {
                                    acc += change.positionChange()
                                }
                                if (dragging) {
                                    change.consume()
                                    if ((change.position - anchor).getDistance() > restRadius) {
                                        anchor = change.position
                                        lastMove = now
                                        repeating = null
                                    }
                                    val ax = abs(acc.x)
                                    val ay = abs(acc.y)
                                    val switchAt = step * SwipeFeel.AXIS_SWITCH_STEPS
                                    val horizontal = when (horizontalAxis) {
                                        null -> ax >= ay
                                        true -> !(ay >= switchAt && ay > ax * 2f)
                                        false -> ax >= switchAt && ax > ay * 2f
                                    }
                                    val along = if (horizontal) acc.x else acc.y
                                    if (abs(along) >= step) {
                                        val dir = if (horizontal) {
                                            if (along > 0) Direction.RIGHT else Direction.LEFT
                                        } else {
                                            if (along > 0) Direction.DOWN else Direction.UP
                                        }
                                        fire(dir, auto = false)
                                        horizontalAxis = horizontal
                                        acc = if (horizontal) Offset(along - sign(along) * step, 0f)
                                        else Offset(0f, along - sign(along) * step)
                                        lastDir = dir
                                        lastStep = now
                                    }
                                }
                            } else {
                                now += PAD_TICK_MS
                            }

                            val onLong = currentOnSelectLong
                            if (!dragging && !longFired && onLong != null && now - start >= LONG_PRESS_MS) {
                                longFired = true
                                haptics.heavy()
                                onLong()
                            }

                            // Swiped far and now resting: keep scrolling in that direction.
                            val dir = lastDir
                            if (dragging && dir != null && now - lastMove >= SwipeFeel.REST_MS) {
                                val travel = anchor - down.position
                                val reach = when (dir) {
                                    Direction.UP -> -travel.y
                                    Direction.DOWN -> travel.y
                                    Direction.LEFT -> -travel.x
                                    Direction.RIGHT -> travel.x
                                }
                                if (reach >= repeatReach) {
                                    val extra = ((reach - repeatReach) / (2f * step)).coerceIn(0f, 1f)
                                    val interval = SwipeFeel.REPEAT_SLOW_MS -
                                        ((SwipeFeel.REPEAT_SLOW_MS - SwipeFeel.REPEAT_FAST_MS) * extra).toLong()
                                    if (now - lastStep >= interval) {
                                        repeating = dir
                                        fire(dir, auto = true)
                                        lastStep = now
                                    }
                                }
                            }
                        }
                    } finally {
                        if (repeating != null) currentOnRepeatEnd()
                        repeating = null
                        setTouching(false)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Edge arrows: a dim base and a lit copy whose opacity follows each step.
        Direction.entries.forEach { dir ->
            val align = when (dir) {
                Direction.UP -> Alignment.TopCenter
                Direction.DOWN -> Alignment.BottomCenter
                Direction.LEFT -> Alignment.CenterStart
                Direction.RIGHT -> Alignment.CenterEnd
            }
            val lit = flashes.getValue(dir)
            Box(Modifier.align(align).padding(14.dp)) {
                Icon(dir.icon, null, tint = TextTertiary.copy(alpha = 0.55f), modifier = Modifier.size(28.dp))
                Icon(
                    dir.icon,
                    null,
                    tint = tint,
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer { alpha = if (repeating == dir) 1f else lit.value },
                )
            }
        }
        // Centre target: purely visual, the whole surface is tappable.
        Box(
            Modifier
                .size(76.dp)
                .clip(CircleShape)
                .border(1.dp, Hairline, CircleShape)
                .drawBehind { drawCircle(tint.copy(alpha = 0.10f * glow.value)) },
            contentAlignment = Alignment.Center,
        ) {
            Text("OK", color = TextTertiary, style = MaterialTheme.typography.labelLarge)
        }
        if (showHint) {
            Text(
                "Wischen · Tippen für OK · Halten für Optionen",
                color = TextTertiary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 24.dp, end = 24.dp, bottom = 52.dp)
                    .graphicsLayer { alpha = 1f - glow.value },
            )
        }
    }
}

internal fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)
