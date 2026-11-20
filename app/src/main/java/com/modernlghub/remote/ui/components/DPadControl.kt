package com.modernlghub.remote.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.modernlghub.remote.ui.theme.Accent
import com.modernlghub.remote.ui.theme.Hairline
import com.modernlghub.remote.ui.theme.Surface1
import com.modernlghub.remote.ui.theme.Surface2
import com.modernlghub.remote.ui.theme.TextPrimary
import com.modernlghub.remote.ui.theme.TextSecondary
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt

private sealed interface PadZone {
    data object Center : PadZone
    data class Arrow(val dir: Direction) : PadZone
}

/** Hold an arrow this long before it starts repeating, then repeat at [DPAD_REPEAT_MS]. */
internal const val DPAD_REPEAT_DELAY_MS = 420L
internal const val DPAD_REPEAT_MS = 110L

/**
 * Classic ring D-pad. An arrow fires on press and repeats while held, like a physical remote;
 * the held wedge glows brighter while it repeats. OK fires on release (only if released on OK),
 * holding OK = long OK. A press the system takes over (edge back gesture) is dropped.
 * TalkBack sees five buttons in reading order: up, left, OK, right, down.
 */
@Composable
fun DPadControl(
    modifier: Modifier = Modifier,
    tint: Color = Accent,
    onDirection: (Direction) -> Unit,
    onSelect: () -> Unit,
    onSelectLong: (() -> Unit)? = null,
    /** Called when a held, repeating press ends: drop keys still waiting to be sent. */
    onRepeatEnd: () -> Unit = {},
) {
    val haptics = rememberHaptics()
    val currentOnDirection by rememberUpdatedState(onDirection)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnSelectLong by rememberUpdatedState(onSelectLong)
    val currentOnRepeatEnd by rememberUpdatedState(onRepeatEnd)
    var pressed by remember { mutableStateOf<PadZone?>(null) }
    var repeating by remember { mutableStateOf(false) }

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                .systemGestureExclusion()
                .pointerInput(Unit) {
                    fun zoneAt(p: Offset): PadZone? {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val r = min(size.width, size.height) / 2f
                        val d = p - c
                        val dist = d.getDistance()
                        if (dist > r) return null
                        if (dist < r * 0.36f) return PadZone.Center
                        val deg = Math.toDegrees(atan2(d.y, d.x).toDouble())
                        return PadZone.Arrow(
                            when {
                                deg >= -45 && deg < 45 -> Direction.RIGHT
                                deg >= 45 && deg < 135 -> Direction.DOWN
                                deg >= -135 && deg < -45 -> Direction.UP
                                else -> Direction.LEFT
                            }
                        )
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val zone = zoneAt(down.position) ?: return@awaitEachGesture
                        down.consume()
                        pressed = zone
                        val start = down.uptimeMillis
                        var now = start
                        var lastRepeat = start
                        var longFired = false
                        if (zone is PadZone.Arrow) {
                            haptics.click()
                            currentOnDirection(zone.dir)
                        }
                        try {
                            while (true) {
                                val ev = withTimeoutOrNull(PAD_TICK_MS) { awaitPointerEvent() }
                                if (ev != null) {
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    now = maxOf(now, ch.uptimeMillis)
                                    if (!ch.pressed) {
                                        val cancelled = ch.isConsumed
                                        if (!cancelled && !longFired && zone == PadZone.Center && zoneAt(ch.position) == PadZone.Center) {
                                            haptics.click()
                                            currentOnSelect()
                                        }
                                        break
                                    }
                                    ch.consume()
                                } else {
                                    now += PAD_TICK_MS
                                }
                                // Holding OK = long press (the hub opens its options menu).
                                val onLong = currentOnSelectLong
                                if (zone == PadZone.Center && onLong != null && !longFired && now - start >= LONG_PRESS_MS) {
                                    longFired = true
                                    haptics.heavy()
                                    onLong()
                                }
                                if (zone is PadZone.Arrow && now - start >= DPAD_REPEAT_DELAY_MS && now - lastRepeat >= DPAD_REPEAT_MS) {
                                    repeating = true
                                    haptics.light()
                                    currentOnDirection(zone.dir)
                                    lastRepeat = now
                                }
                            }
                        } finally {
                            if (repeating) currentOnRepeatEnd()
                            pressed = null
                            repeating = false
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = min(size.width, size.height) / 2f
                val c = center
                drawCircle(Surface1, r, c)
                drawCircle(Hairline, r - 0.5.dp.toPx(), c, style = Stroke(1.dp.toPx()))
                (pressed as? PadZone.Arrow)?.let { p ->
                    val startAngle = when (p.dir) {
                        Direction.RIGHT -> -45f
                        Direction.DOWN -> 45f
                        Direction.LEFT -> 135f
                        Direction.UP -> 225f
                    }
                    drawArc(
                        tint.copy(alpha = if (repeating) 0.28f else 0.16f), startAngle, 90f, useCenter = true,
                        topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r),
                    )
                }
                val inner = r * 0.36f
                val centerOn = pressed == PadZone.Center
                drawCircle(if (centerOn) tint.copy(alpha = 0.18f) else Surface2, inner, c)
                drawCircle(if (centerOn) tint.copy(alpha = 0.5f) else Hairline, inner, c, style = Stroke(1.dp.toPx()))
            }
            DPadTargets(
                pressed = pressed,
                tint = tint,
                onDirection = { currentOnDirection(it) },
                onSelect = { currentOnSelect() },
                onSelectLong = onSelectLong?.let { { currentOnSelectLong?.invoke() } },
            )
        }
    }
}

/** Glyphs on the ring; each also is an accessibility button (touch input is handled by the ring). */
@Composable
private fun DPadTargets(
    pressed: PadZone?,
    tint: Color,
    onDirection: (Direction) -> Unit,
    onSelect: () -> Unit,
    onSelectLong: (() -> Unit)?,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val dist = min(constraints.maxWidth, constraints.maxHeight) / 2f * 0.68f
        val order = mapOf(Direction.UP to 0f, Direction.LEFT to 1f, Direction.RIGHT to 3f, Direction.DOWN to 4f)
        Direction.entries.forEach { dir ->
            val (dx, dy) = when (dir) {
                Direction.UP -> 0f to -dist
                Direction.DOWN -> 0f to dist
                Direction.LEFT -> -dist to 0f
                Direction.RIGHT -> dist to 0f
            }
            val lit = (pressed as? PadZone.Arrow)?.dir == dir
            Icon(
                dir.icon,
                contentDescription = null,
                tint = if (lit) tint else TextSecondary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
                    .size(48.dp)
                    .semantics {
                        contentDescription = dir.label
                        role = Role.Button
                        traversalIndex = order.getValue(dir)
                        onClick { onDirection(dir); true }
                    },
            )
        }
        Text(
            "OK",
            color = if (pressed == PadZone.Center) tint else TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.Center)
                .semantics {
                    role = Role.Button
                    traversalIndex = 2f
                    onClick(label = "OK") { onSelect(); true }
                    if (onSelectLong != null) onLongClick(label = "Optionen") { onSelectLong(); true }
                },
        )
    }
}
