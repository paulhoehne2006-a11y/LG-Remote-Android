package com.modernlghub.remote.ui.components

import android.os.SystemClock
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.modernlghub.remote.ui.theme.Accent
import com.modernlghub.remote.ui.theme.AccentDim
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

/** Classic ring D-pad. Holding an arrow repeats it, like a physical remote. */
@Composable
fun DPadControl(
    modifier: Modifier = Modifier,
    onDirection: (Direction) -> Unit,
    onSelect: () -> Unit,
) {
    val haptics = rememberHaptics()
    val currentOnDirection by rememberUpdatedState(onDirection)
    val currentOnSelect by rememberUpdatedState(onSelect)
    var pressed by remember { mutableStateOf<PadZone?>(null) }

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                .semantics { contentDescription = "Steuerkreuz" }
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
                        val start = SystemClock.uptimeMillis()
                        var lastRepeat = start
                        if (zone is PadZone.Arrow) {
                            haptics.click()
                            currentOnDirection(zone.dir)
                        }
                        while (true) {
                            val ev = withTimeoutOrNull(40L) { awaitPointerEvent() }
                            if (ev != null) {
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) {
                                    if (zone == PadZone.Center && zoneAt(ch.position) == PadZone.Center) {
                                        haptics.click()
                                        currentOnSelect()
                                    }
                                    break
                                }
                                ch.consume()
                            }
                            val now = SystemClock.uptimeMillis()
                            if (zone is PadZone.Arrow && now - start > 420 && now - lastRepeat > 110) {
                                haptics.tick()
                                currentOnDirection(zone.dir)
                                lastRepeat = now
                            }
                        }
                        pressed = null
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = min(size.width, size.height) / 2f
                val c = center
                drawCircle(Surface1, r, c)
                drawCircle(Hairline, r - 0.5.dp.toPx(), c, style = Stroke(1.dp.toPx()))
                // Highlight the pressed wedge.
                (pressed as? PadZone.Arrow)?.let { p ->
                    val startAngle = when (p.dir) {
                        Direction.RIGHT -> -45f
                        Direction.DOWN -> 45f
                        Direction.LEFT -> 135f
                        Direction.UP -> 225f
                    }
                    drawArc(
                        AccentDim, startAngle, 90f, useCenter = true,
                        topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r),
                    )
                }
                val inner = r * 0.36f
                drawCircle(if (pressed == PadZone.Center) AccentDim else Surface2, inner, c)
                drawCircle(if (pressed == PadZone.Center) Accent.copy(alpha = 0.5f) else Hairline, inner, c, style = Stroke(1.dp.toPx()))
            }
            Text("OK", color = if (pressed == PadZone.Center) Accent else TextPrimary, style = MaterialTheme.typography.titleMedium)
            ArrowGlyphs(pressed)
        }
    }
}

@Composable
private fun ArrowGlyphs(pressed: PadZone?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val dist = min(constraints.maxWidth, constraints.maxHeight) / 2f * 0.68f
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
                tint = if (lit) Accent else TextSecondary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
                    .size(32.dp),
            )
        }
    }
}
