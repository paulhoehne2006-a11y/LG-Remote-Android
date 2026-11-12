package com.modernlghub.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modernlghub.remote.ui.theme.CardBorder
import com.modernlghub.remote.ui.theme.SurfaceCard
import com.modernlghub.remote.ui.theme.SurfaceCardVariant
import com.modernlghub.remote.ui.theme.TextWhite

@Composable
fun DPadControl(
    modifier: Modifier = Modifier,
    onDirection: (String) -> Unit,
    onSelect: () -> Unit
) {
    val context = LocalContext.current
    val haptics = remember { Haptics(context) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(SurfaceCard)
            .border(1.dp, CardBorder, RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // UP
            DirectionButton(
                icon = Icons.Default.KeyboardArrowUp,
                contentDescription = "Hoch"
            ) {
                haptics.tick()
                onDirection("up")
            }

            // LEFT - OK - RIGHT
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DirectionButton(
                    icon = Icons.Default.KeyboardArrowLeft,
                    contentDescription = "Links"
                ) {
                    haptics.tick()
                    onDirection("left")
                }

                // Center OK
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(SurfaceCardVariant)
                        .border(1.5.dp, CardBorder, CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, color = TextWhite)
                        ) {
                            haptics.click()
                            onSelect()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "OK",
                        color = TextWhite,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    )
                }

                DirectionButton(
                    icon = Icons.Default.KeyboardArrowRight,
                    contentDescription = "Rechts"
                ) {
                    haptics.tick()
                    onDirection("right")
                }
            }

            // DOWN
            DirectionButton(
                icon = Icons.Default.KeyboardArrowDown,
                contentDescription = "Runter"
            ) {
                haptics.tick()
                onDirection("down")
            }
        }
    }
}

@Composable
private fun DirectionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(62.dp)
            .clip(CircleShape)
            .background(SurfaceCardVariant)
            .border(1.dp, CardBorder, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = TextWhite)
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = TextWhite,
            modifier = Modifier.size(32.dp)
        )
    }
}
