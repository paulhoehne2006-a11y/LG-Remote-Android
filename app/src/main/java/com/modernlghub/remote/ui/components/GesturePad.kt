package com.modernlghub.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modernlghub.remote.ui.theme.CardBorder
import com.modernlghub.remote.ui.theme.SurfaceCard
import com.modernlghub.remote.ui.theme.TextMuted
import com.modernlghub.remote.ui.theme.TextWhite
import kotlin.math.abs

@Composable
fun GesturePad(
    modifier: Modifier = Modifier,
    onDirection: (String) -> Unit,
    onSelect: () -> Unit,
    onLongSelect: () -> Unit
) {
    val context = LocalContext.current
    val haptics = remember { Haptics(context) }
    val stepThreshold = 65f // pixels per D-Pad step

    var accumulatedX by remember { mutableFloatStateOf(0f) }
    var accumulatedY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(SurfaceCard)
            .border(1.dp, CardBorder, RoundedCornerShape(28.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        haptics.click()
                        onSelect()
                    },
                    onLongPress = {
                        haptics.heavyClick()
                        onLongSelect()
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        accumulatedX = 0f
                        accumulatedY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedX += dragAmount.x
                        accumulatedY += dragAmount.y

                        if (abs(accumulatedX) >= stepThreshold) {
                            if (accumulatedX > 0) {
                                haptics.tick()
                                onDirection("right")
                            } else {
                                haptics.tick()
                                onDirection("left")
                            }
                            accumulatedX = 0f
                        }

                        if (abs(accumulatedY) >= stepThreshold) {
                            if (accumulatedY > 0) {
                                haptics.tick()
                                onDirection("down")
                            } else {
                                haptics.tick()
                                onDirection("up")
                            }
                            accumulatedY = 0f
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(CardBorder.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "OK",
                    color = TextWhite,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp
                )
            }
            Text(
                text = "Wischen für Schritte · Tippen für OK",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
