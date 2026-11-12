package com.modernlghub.remote.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modernlghub.remote.ui.theme.CardBorder
import com.modernlghub.remote.ui.theme.CyberCyan
import com.modernlghub.remote.ui.theme.SurfaceCard
import com.modernlghub.remote.ui.theme.SurfaceCardVariant
import com.modernlghub.remote.ui.theme.TextMuted
import com.modernlghub.remote.ui.theme.TextWhite
import kotlin.math.abs

@Composable
fun GesturePad(
    modifier: Modifier = Modifier,
    onDirection: (String) -> Unit,
    onSelect: () -> Unit,
    onLongSelect: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptics = remember { Haptics(context) }
    val stepThreshold = 55f // Snappy responsive step threshold

    var accumulatedX by remember { mutableFloatStateOf(0f) }
    var accumulatedY by remember { mutableFloatStateOf(0f) }

    var isTouching by remember { mutableStateOf(false) }
    var touchPos by remember { mutableStateOf(Offset.Zero) }
    var activeDirection by remember { mutableStateOf<String?>(null) }

    val upTint by animateColorAsState(if (activeDirection == "up") CyberCyan else CardBorder, tween(180))
    val downTint by animateColorAsState(if (activeDirection == "down") CyberCyan else CardBorder, tween(180))
    val leftTint by animateColorAsState(if (activeDirection == "left") CyberCyan else CardBorder, tween(180))
    val rightTint by animateColorAsState(if (activeDirection == "right") CyberCyan else CardBorder, tween(180))

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(SurfaceCard)
            .border(1.dp, CardBorder, RoundedCornerShape(32.dp))
            .drawBehind {
                if (isTouching) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                CyberCyan.copy(alpha = 0.22f),
                                Color.Transparent
                            ),
                            center = touchPos,
                            radius = 160f
                        )
                    )
                }
            }
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
                    onDragStart = { offset ->
                        isTouching = true
                        touchPos = offset
                        accumulatedX = 0f
                        accumulatedY = 0f
                    },
                    onDragEnd = {
                        isTouching = false
                        activeDirection = null
                    },
                    onDragCancel = {
                        isTouching = false
                        activeDirection = null
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        touchPos = change.position
                        accumulatedX += dragAmount.x
                        accumulatedY += dragAmount.y

                        if (abs(accumulatedX) >= stepThreshold) {
                            if (accumulatedX > 0) {
                                haptics.tick()
                                activeDirection = "right"
                                onDirection("right")
                            } else {
                                haptics.tick()
                                activeDirection = "left"
                                onDirection("left")
                            }
                            accumulatedX = 0f
                        }

                        if (abs(accumulatedY) >= stepThreshold) {
                            if (accumulatedY > 0) {
                                haptics.tick()
                                activeDirection = "down"
                                onDirection("down")
                            } else {
                                haptics.tick()
                                activeDirection = "up"
                                onDirection("up")
                            }
                            accumulatedY = 0f
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Direction Chevrons (ambient indicators)
        Text(
            text = "▲",
            color = upTint,
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp)
        )
        Text(
            text = "▼",
            color = downTint,
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
        )
        Text(
            text = "◀",
            color = leftTint,
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
        )
        Text(
            text = "▶",
            color = rightTint,
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
        )

        // Center OK Dial
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(SurfaceCardVariant)
                    .border(1.5.dp, if (isTouching) CyberCyan.copy(alpha = 0.6f) else CardBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "OK",
                    color = TextWhite,
                    fontSize = 18.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            }
            Text(
                text = "Wischen für Schritte · Tippen für OK",
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}
