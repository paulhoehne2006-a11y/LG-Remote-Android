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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modernlghub.remote.data.WebOsPointerClient
import com.modernlghub.remote.ui.theme.CardBorder
import com.modernlghub.remote.ui.theme.SurfaceCard
import com.modernlghub.remote.ui.theme.SurfaceCardVariant
import com.modernlghub.remote.ui.theme.TextMuted

@Composable
fun MagicCursorPad(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pointerClient = remember { WebOsPointerClient(context) }
    val haptics = remember { Haptics(context) }

    DisposableEffect(Unit) {
        pointerClient.connect()
        onDispose {
            pointerClient.disconnect()
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(SurfaceCard)
            .border(1.dp, CardBorder, RoundedCornerShape(28.dp))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Main Touchpad Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                haptics.click()
                                pointerClient.click()
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            pointerClient.move(dragAmount.x * 1.5f, dragAmount.y * 1.5f)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Magic Pointer bewegen · Tippen für Klick",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            // Right Scroll Bar Strip
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .fillMaxHeight()
                    .background(SurfaceCardVariant.copy(alpha = 0.5f))
                    .border(1.dp, CardBorder.copy(alpha = 0.5f))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            pointerClient.scroll(dragAmount.y * 0.15f)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↕ Scroll",
                    color = TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
