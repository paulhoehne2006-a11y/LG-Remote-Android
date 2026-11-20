package com.modernlghub.remote.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.modernlghub.remote.ui.theme.Accent
import com.modernlghub.remote.ui.theme.AccentDim
import com.modernlghub.remote.ui.theme.AccentLine
import com.modernlghub.remote.ui.theme.DisabledAlpha
import com.modernlghub.remote.ui.theme.Hairline
import com.modernlghub.remote.ui.theme.LocalReduceMotion
import com.modernlghub.remote.ui.theme.MinTouch
import com.modernlghub.remote.ui.theme.Motion
import com.modernlghub.remote.ui.theme.Shape as Shapes
import com.modernlghub.remote.ui.theme.Spacing
import com.modernlghub.remote.ui.theme.Surface1
import com.modernlghub.remote.ui.theme.Surface2
import com.modernlghub.remote.ui.theme.Surface3
import com.modernlghub.remote.ui.theme.TextPrimary
import com.modernlghub.remote.ui.theme.TextSecondary
import com.modernlghub.remote.ui.theme.TextTertiary
import com.modernlghub.remote.ui.theme.motion

/** Standard card surface: tonal fill + hairline. Depth by tone, never by shadow. */
fun Modifier.card(shape: Shape = Shapes.Card, color: Color = Surface1, outline: Color = Hairline): Modifier =
    this.clip(shape).background(color).border(1.dp, outline, shape)

/**
 * Press feedback without ripples (ripples look cheap on true black): the element sinks slightly
 * with a soft spring. The scale is applied in the layer, so pressing never recomposes the content.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClickLabel: String? = null,
    pressedScale: Float = 0.95f,
    interactionSource: MutableInteractionSource? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

/**
 * Round icon button. [size] is the visible disc; the touch target is never smaller than 48 dp.
 * Pressing brightens the disc by one tone step.
 */
@Composable
fun RoundButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    iconSize: Dp = 24.dp,
    container: Color = Surface2,
    tint: Color = TextPrimary,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val fill by animateColorAsState(
        when {
            !pressed -> container
            container == Color.Transparent -> Surface2
            container == Surface2 -> Surface3
            else -> container
        },
        motion(Motion.Fast),
        label = "buttonFill",
    )
    Box(
        modifier = modifier
            .size(maxOf(size, MinTouch))
            .pressable(
                enabled = enabled,
                interactionSource = source,
                pressedScale = 0.92f,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer { alpha = if (enabled) 1f else DisabledAlpha }
                .clip(CircleShape)
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
        }
    }
}

/**
 * Pill-shaped toggle used for quick actions (sound output, picture off, PC, inputs).
 * [active] lights it in the accent (or [activeColor]); TalkBack hears [stateLabel].
 */
@Composable
fun ToggleChip(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    activeColor: Color = Accent,
    stateLabel: String? = null,
    enabled: Boolean = true,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    showIcon: Boolean = true,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(if (active) activeColor.copy(alpha = 0.16f) else Surface1, motion(Motion.Medium), label = "chipBg")
    val line by animateColorAsState(if (active) activeColor.copy(alpha = 0.38f) else Hairline, motion(Motion.Medium), label = "chipLine")
    val fg by animateColorAsState(if (active) activeColor else TextPrimary, motion(Motion.Medium), label = "chipFg")
    // At large font sizes the label needs the room more than the icon does.
    val withIcon = icon != null && showIcon && LocalDensity.current.fontScale < 1.25f
    Row(
        modifier
            .heightIn(min = MinTouch)
            .semantics { if (stateLabel != null) stateDescription = stateLabel }
            .pressable(enabled = enabled, onLongClickLabel = onLongClickLabel, onLongClick = onLongClick, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else DisabledAlpha }
            .clip(Shapes.Pill)
            .background(bg)
            .border(1.dp, line, Shapes.Pill)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (withIcon) {
            Icon(icon!!, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

/** Filled accent button for the one primary action of a state or form. */
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    PillButton(text, modifier, icon, enabled, filled = true, onClick = onClick)
}

/** Quiet outlined companion to [PrimaryButton]. */
@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    PillButton(text, modifier, icon, enabled, filled = false, onClick = onClick)
}

@Composable
private fun PillButton(text: String, modifier: Modifier, icon: ImageVector?, enabled: Boolean, filled: Boolean, onClick: () -> Unit) {
    val fg = if (filled) com.modernlghub.remote.ui.theme.OnAccent else TextPrimary
    Row(
        modifier
            .heightIn(min = MinTouch)
            .pressable(enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else DisabledAlpha }
            .clip(Shapes.Pill)
            .then(if (filled) Modifier.background(Accent) else Modifier.background(Surface1).border(1.dp, Hairline, Shapes.Pill))
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = TextTertiary,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .semantics { heading() }
            .padding(start = Spacing.xs, bottom = Spacing.s),
    )
}

/** Screen title with an optional one-line subtitle and trailing actions. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

@Composable
fun StatusDot(color: Color, pulsing: Boolean, modifier: Modifier = Modifier) {
    val pulse = pulsing && !LocalReduceMotion.current
    val alpha = if (pulse) {
        val t = rememberInfiniteTransition(label = "dot")
        t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(900, easing = Motion.Standard), RepeatMode.Reverse), label = "dotAlpha")
    } else null
    Box(
        modifier
            .size(8.dp)
            .graphicsLayer { this.alpha = alpha?.value ?: 1f }
            .clip(CircleShape)
            .background(color)
    )
}

/** Accent pill that marks something as current ("Läuft"). */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier, color: Color = Accent) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        modifier = modifier
            .clip(Shapes.Pill)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
