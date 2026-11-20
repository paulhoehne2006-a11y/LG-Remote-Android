package com.modernlghub.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.modernlghub.remote.data.WebOsPointerClient
import com.modernlghub.remote.ui.theme.Accent
import com.modernlghub.remote.ui.theme.Hairline
import com.modernlghub.remote.ui.theme.Shape
import com.modernlghub.remote.ui.theme.StatusOff
import com.modernlghub.remote.ui.theme.Surface1
import com.modernlghub.remote.ui.theme.Surface2
import com.modernlghub.remote.ui.theme.TextTertiary
import kotlin.math.abs

/** Touchpad that drives the webOS Magic Remote pointer; owns the pointer socket while shown. */
@Composable
fun CursorPad(modifier: Modifier = Modifier, tint: Color = Accent) {
    val context = LocalContext.current
    val client = remember { WebOsPointerClient(context.applicationContext) }
    val state by client.state.collectAsStateWithLifecycle()
    DisposableEffect(client) {
        client.connect()
        onDispose { client.disconnect() }
    }
    CursorPadSurface(
        state = state,
        modifier = modifier,
        tint = tint,
        onMove = client::move,
        onClick = client::click,
        onScroll = client::scroll,
        onRetry = client::connect,
    )
}

/** The pointer surface itself, driven by plain state (also used for screenshots). */
@Composable
fun CursorPadSurface(
    state: WebOsPointerClient.State,
    modifier: Modifier = Modifier,
    tint: Color = Accent,
    onMove: (Float, Float) -> Unit,
    onClick: () -> Unit,
    onScroll: (Float) -> Unit,
    onRetry: () -> Unit,
) {
    val haptics = rememberHaptics()
    val move by rememberUpdatedState(onMove)
    val click by rememberUpdatedState(onClick)
    val scroll by rememberUpdatedState(onScroll)
    val ready = state == WebOsPointerClient.State.READY

    Row(modifier.clip(Shape.Pad).background(Surface1).border(1.dp, Hairline, Shape.Pad)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .systemGestureExclusion()
                .semantics {
                    contentDescription = "Mauszeiger-Feld"
                    if (ready) onClick(label = "Klicken") { click(); true }
                }
                .pointerInput(ready) {
                    if (!ready) return@pointerInput
                    detectTapGestures(onTap = {
                        haptics.click()
                        click()
                    })
                }
                .pointerInput(ready) {
                    if (!ready) return@pointerInput
                    detectDragGestures { change, drag ->
                        change.consume()
                        // Mild acceleration: slow moves stay precise, flicks cross the screen.
                        val speed = drag.getDistance()
                        val gain = 1.1f + (speed / 18f).coerceAtMost(1.6f)
                        move(drag.x * gain, drag.y * gain)
                    }
                },
        ) {
            when (state) {
                WebOsPointerClient.State.READY -> StatePanel(
                    title = "Mauszeiger bereit",
                    body = "Bewegen zum Zeigen, tippen zum Klicken. Rechts scrollen.",
                    icon = Icons.Rounded.Mouse,
                    tint = TextTertiary,
                    modifier = Modifier.fillMaxSize(),
                )
                WebOsPointerClient.State.PAIRING -> StatePanel(
                    title = "Am Fernseher erlauben",
                    body = "Der Fernseher fragt einmalig, ob diese App den Mauszeiger steuern darf.",
                    icon = Icons.Rounded.Tv,
                    tint = tint,
                    modifier = Modifier.fillMaxSize(),
                )
                WebOsPointerClient.State.FAILED -> StatePanel(
                    title = "Mauszeiger nicht verfügbar",
                    body = "Der Fernseher hat die Verbindung nicht angenommen.",
                    icon = Icons.Rounded.WifiOff,
                    tint = StatusOff,
                    primary = StateAction("Erneut verbinden", onClick = onRetry),
                    modifier = Modifier.fillMaxSize(),
                )
                else -> StatePanel(
                    title = "Verbinde …",
                    body = null,
                    busy = true,
                    tint = tint,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Scroll lane (excluded from the edge back gesture, which it sits right next to).
        Box(
            Modifier
                .width(56.dp)
                .fillMaxHeight()
                .padding(6.dp)
                .clip(Shape.Pill)
                .background(Surface2)
                .systemGestureExclusion()
                .pointerInput(ready) {
                    if (!ready) return@pointerInput
                    var acc = 0f
                    detectDragGestures(onDragStart = { acc = 0f }) { change, drag ->
                        change.consume()
                        acc += drag.y
                        if (abs(acc) >= 6f) {
                            scroll(acc / 3f)
                            acc = 0f
                        }
                    }
                },
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Icon(Icons.Rounded.UnfoldMore, contentDescription = "Scrollen", tint = TextTertiary)
        }
    }
}
