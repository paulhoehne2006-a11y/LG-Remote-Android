package com.modernlghub.remote.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.modernlghub.remote.ui.theme.Accent
import com.modernlghub.remote.ui.theme.LocalReduceMotion
import com.modernlghub.remote.ui.theme.Motion
import com.modernlghub.remote.ui.theme.Shape as Shapes
import com.modernlghub.remote.ui.theme.Spacing
import com.modernlghub.remote.ui.theme.Surface1
import com.modernlghub.remote.ui.theme.Surface2
import com.modernlghub.remote.ui.theme.TextPrimary
import com.modernlghub.remote.ui.theme.TextSecondary

/** A button offered by a [StatePanel]. */
data class StateAction(val label: String, val icon: ImageVector? = null, val onClick: () -> Unit)

/**
 * The one way the app shows a non-happy state (off, unreachable, not set up, empty, error, offline):
 * a glowing icon, a short title, one or two sentences, one primary action and optional quiet ones.
 * [busy] replaces the icon with a spinner (connecting, waking).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatePanel(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = Accent,
    busy: Boolean = false,
    primary: StateAction? = null,
    secondary: List<StateAction> = emptyList(),
) {
    Column(
        modifier
            .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite }
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .drawBehind {
                    drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.22f), Color.Transparent)))
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(color = tint, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                } else if (icon != null) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
                }
            }
        }
        Spacer(Modifier.height(Spacing.m))
        Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(Spacing.s))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 340.dp),
            )
        }
        if (primary != null) {
            Spacer(Modifier.height(Spacing.xl))
            PrimaryButton(primary.label, icon = primary.icon, onClick = primary.onClick)
        }
        if (secondary.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.s))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                secondary.forEach { SecondaryButton(it.label, icon = it.icon, onClick = it.onClick) }
            }
        }
    }
}

/** Placeholder block while content loads: a calm breathing tone, still when motion is reduced. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: Shape = Shapes.Tile) {
    val alpha = if (!LocalReduceMotion.current) {
        rememberInfiniteTransition(label = "skeleton")
            .animateFloat(0.55f, 1f, infiniteRepeatable(tween(1100, easing = Motion.Standard), RepeatMode.Reverse), label = "skeletonAlpha")
    } else null
    Box(
        modifier
            .graphicsLayer { this.alpha = alpha?.value ?: 0.8f }
            .clip(shape)
            .background(Surface1)
    )
}

/** Small inline hint row: tonal background, secondary text. */
@Composable
fun InlineNotice(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, tint: Color = TextSecondary) {
    androidx.compose.foundation.layout.Row(
        modifier
            .clip(Shapes.Control)
            .background(Surface2)
            .padding(horizontal = Spacing.m, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(Spacing.s))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = tint)
    }
}
