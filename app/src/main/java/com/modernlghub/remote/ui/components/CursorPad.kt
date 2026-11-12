package com.modernlghub.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.modernlghub.remote.data.WebOsPointerClient
import com.modernlghub.remote.ui.theme.Accent
import com.modernlghub.remote.ui.theme.Hairline
import com.modernlghub.remote.ui.theme.Surface1
import com.modernlghub.remote.ui.theme.Surface2
import com.modernlghub.remote.ui.theme.TextSecondary
import com.modernlghub.remote.ui.theme.TextTertiary
import kotlin.math.abs

/** Touchpad that drives the webOS Magic Remote pointer. */
@Composable
fun CursorPad(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val client = remember { WebOsPointerClient(context) }
    val state by client.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()

    DisposableEffect(client) {
        client.connect()
        onDispose { client.disconnect() }
    }

    val shape = RoundedCornerShape(32.dp)
    Row(modifier.clip(shape).background(Surface1).border(1.dp, Hairline, shape)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(client) {
                    detectTapGestures(onTap = {
                        haptics.click()
                        client.click()
                    })
                }
                .pointerInput(client) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        // Mild acceleration: slow moves stay precise, flicks cross the screen.
                        val speed = drag.getDistance()
                        val gain = 1.1f + (speed / 18f).coerceAtMost(1.6f)
                        client.move(drag.x * gain, drag.y * gain)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                WebOsPointerClient.State.READY -> Hint(Icons.Rounded.Mouse, "Bewegen · Tippen = Klick")
                WebOsPointerClient.State.PAIRING -> Message("Am Fernseher die Verbindung erlauben")
                WebOsPointerClient.State.FAILED -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Message("Mauszeiger nicht verfügbar")
                    TextButton(onClick = { client.connect() }) { Text("Erneut verbinden", color = Accent) }
                }
                else -> CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
        }
        // Scroll lane.
        Box(
            Modifier
                .width(52.dp)
                .fillMaxHeight()
                .padding(6.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Surface2)
                .pointerInput(client) {
                    var acc = 0f
                    detectDragGestures(onDragStart = { acc = 0f }) { change, drag ->
                        change.consume()
                        acc += drag.y
                        if (abs(acc) >= 6f) {
                            client.scroll(acc / 3f)
                            acc = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.UnfoldMore, contentDescription = "Scrollen", tint = TextTertiary)
        }
    }
}

@Composable
private fun Hint(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(8.dp))
        Text(text, color = TextTertiary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        color = TextSecondary,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}
