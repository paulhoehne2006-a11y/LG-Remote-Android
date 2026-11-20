package com.modernlghub.remote.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

internal const val REPEAT_DELAY_MS = 400L
internal const val REPEAT_INTERVAL_MS = 140L

/**
 * Press = one step, keep holding = repeat (after [REPEAT_DELAY_MS], every [REPEAT_INTERVAL_MS]),
 * like the volume rocker of a physical remote. Repeating stops the moment the finger lifts,
 * the system takes the gesture, or the element leaves the screen.
 * [onStep] gets `true` for repeated steps (use a lighter haptic for those).
 * [onRepeatingChange] lets the caller show that it is repeating.
 */
fun Modifier.repeatingPress(
    enabled: Boolean,
    label: String? = null,
    onRepeatingChange: (Boolean) -> Unit = {},
    onStep: (repeat: Boolean) -> Unit,
): Modifier = composed {
    val step by rememberUpdatedState(onStep)
    val repeatingChange by rememberUpdatedState(onRepeatingChange)
    this
        .semantics {
            role = Role.Button
            if (enabled) onClick(label = label) { step(false); true }
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                step(false)
                var now = down.uptimeMillis
                var last = now
                val start = now
                try {
                    while (true) {
                        val ev = withTimeoutOrNull(PAD_TICK_MS) { awaitPointerEvent() }
                        if (ev != null) {
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            now = maxOf(now, ch.uptimeMillis)
                            ch.consume()
                        } else {
                            now += PAD_TICK_MS
                        }
                        if (now - start >= REPEAT_DELAY_MS && now - last >= REPEAT_INTERVAL_MS) {
                            repeatingChange(true)
                            step(true)
                            last = now
                        }
                    }
                } finally {
                    repeatingChange(false)
                }
            }
        }
}
