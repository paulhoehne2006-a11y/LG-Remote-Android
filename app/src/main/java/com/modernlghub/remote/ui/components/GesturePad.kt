package com.modernlghub.remote.ui.components

import android.os.SystemClock
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.modernlghub.remote.ui.theme.Accent
import com.modernlghub.remote.ui.theme.Hairline
import com.modernlghub.remote.ui.theme.Surface1
import com.modernlghub.remote.ui.theme.TextTertiary
import kotlin.math.abs
import kotlin.math.sign

/** How long OK must be held to count as a long press (the hub's own remote uses 600 ms). */
const val LONG_PRESS_MS = 550L

enum class Direction(val key: String, val icon: ImageVector) {
    UP("UP", Icons.Rounded.KeyboardArrowUp),
    DOWN("DOWN", Icons.Rounded.KeyboardArrowDown),
    LEFT("LEFT", Icons.AutoMirrored.Rounded.KeyboardArrowLeft),
    RIGHT("RIGHT", Icons.AutoMirrored.Rounded.KeyboardArrowRight),
}

/**
 * Swipe surface in the style of a modern streaming remote:
 * - every ~36 dp of travel = one step (axis-locked, so diagonals never double-fire)
 * - swipe far and keep the finger resting = continuous scrolling
 * - tap anywhere = OK
 * - press and hold without moving = long OK (options menu on the hub)
 */
@Composable
fun GesturePad(
    modifier: Modifier = Modifier,
    onDirection: (Direction) -> Unit,
    onSelect: () -> Unit,
    onSelectLong: (() -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    val currentOnDirection by rememberUpdatedState(onDirection)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnSelectLong by rememberUpdatedState(onSelectLong)

    var touching by remember { mutableStateOf(false) }
    var touchPos by remember { mutableStateOf(Offset.Zero) }
    var flashDir by remember { mutableStateOf<Direction?>(null) }
    var flashCount by remember { mutableIntStateOf(0) }
    val flash = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }

    LaunchedEffect(flashCount) {
        if (flashCount == 0) return@LaunchedEffect
        flash.snapTo(1f)
        flash.animateTo(0f, tween(320))
    }
    LaunchedEffect(touching) { glow.animateTo(if (touching) 1f else 0f, tween(if (touching) 120 else 380)) }

    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Surface1)
            .border(1.dp, Hairline, shape)
            .semantics { contentDescription = "Wischfeld: Wischen zum Navigieren, Tippen für OK, Halten für Optionen" }
            .drawBehind {
                if (glow.value > 0f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Accent.copy(alpha = 0.18f * glow.value), Color.Transparent),
                            center = touchPos,
                            radius = 140.dp.toPx(),
                        ),
                        radius = 140.dp.toPx(),
                        center = touchPos,
                    )
                }
            }
            .pointerInput(Unit) {
                val step = 36.dp.toPx()
                val repeatReach = step * 2.5f
                val restRadius = 6.dp.toPx()
                val slop = viewConfiguration.touchSlop

                fun fire(dir: Direction) {
                    haptics.tick()
                    flashDir = dir
                    flashCount++
                    currentOnDirection(dir)
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    touching = true
                    touchPos = down.position
                    var dragging = false
                    var acc = Offset.Zero
                    var lastDir: Direction? = null
                    // "Resting" = finger stays within restRadius of this anchor (ignores jitter).
                    var anchor = down.position
                    var lastMove = SystemClock.uptimeMillis()
                    var lastRepeat = 0L
                    val start = SystemClock.uptimeMillis()
                    var longFired = false

                    while (true) {
                        val event = withTimeoutOrNull(40L) { awaitPointerEvent() }
                        val now = SystemClock.uptimeMillis()
                        if (event != null) {
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (!dragging && !longFired) {
                                    haptics.click()
                                    currentOnSelect()
                                }
                                break
                            }
                            touchPos = change.position
                            if (!dragging && (change.position - down.position).getDistance() > slop) dragging = true
                            if (dragging) {
                                val delta = change.positionChange()
                                change.consume()
                                if ((change.position - anchor).getDistance() > restRadius) {
                                    anchor = change.position
                                    lastMove = now
                                }
                                acc += delta
                                val ax = abs(acc.x)
                                val ay = abs(acc.y)
                                if (maxOf(ax, ay) >= step) {
                                    val horizontal = ax >= ay
                                    val dir = if (horizontal) {
                                        if (acc.x > 0) Direction.RIGHT else Direction.LEFT
                                    } else {
                                        if (acc.y > 0) Direction.DOWN else Direction.UP
                                    }
                                    fire(dir)
                                    acc = if (horizontal) Offset(acc.x - sign(acc.x) * step, 0f)
                                    else Offset(0f, acc.y - sign(acc.y) * step)
                                    lastDir = dir
                                    lastRepeat = now
                                }
                            }
                        }
                        val onLong = currentOnSelectLong
                        if (!dragging && !longFired && onLong != null && now - start > LONG_PRESS_MS) {
                            longFired = true
                            haptics.heavy()
                            onLong()
                        }
                        // Swiped far and now resting → keep scrolling in that direction.
                        val dir = lastDir
                        if (dragging && dir != null && now - lastMove > 300 && now - lastRepeat > 120) {
                            val travel = touchPos - down.position
                            val reach = when (dir) {
                                Direction.UP -> -travel.y
                                Direction.DOWN -> travel.y
                                Direction.LEFT -> -travel.x
                                Direction.RIGHT -> travel.x
                            }
                            if (reach > repeatReach) {
                                fire(dir)
                                lastRepeat = now
                            }
                        }
                    }
                    touching = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Edge arrows light up on each step.
        Direction.entries.forEach { dir ->
            val align = when (dir) {
                Direction.UP -> Alignment.TopCenter
                Direction.DOWN -> Alignment.BottomCenter
                Direction.LEFT -> Alignment.CenterStart
                Direction.RIGHT -> Alignment.CenterEnd
            }
            val lit = if (flashDir == dir) flash.value else 0f
            Icon(
                dir.icon,
                contentDescription = null,
                tint = lerpColor(TextTertiary.copy(alpha = 0.5f), Accent, lit),
                modifier = Modifier
                    .align(align)
                    .padding(14.dp)
                    .size(28.dp),
            )
        }
        // Centre target: purely visual, the whole surface is tappable.
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .border(1.dp, Hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("OK", color = TextTertiary, style = MaterialTheme.typography.labelLarge)
        }
        Text(
            "Wischen · Tippen OK · Halten Optionen",
            color = TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .alpha(1f - glow.value),
        )
    }
}

internal fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)
