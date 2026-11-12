package com.modernlghub.remote.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.modernlghub.remote.data.LauncherApp
import com.modernlghub.remote.ui.theme.Hairline
import com.modernlghub.remote.ui.theme.Surface1
import com.modernlghub.remote.ui.theme.Surface2
import com.modernlghub.remote.ui.theme.Surface3
import com.modernlghub.remote.ui.theme.TextPrimary
import com.modernlghub.remote.ui.theme.TextSecondary
import com.modernlghub.remote.ui.theme.TextTertiary

/** Standard card surface: subtle fill + hairline border. */
fun Modifier.card(shape: Shape = RoundedCornerShape(24.dp), color: Color = Surface1): Modifier =
    this.clip(shape).background(color).border(1.dp, Hairline, shape)

/** Scale-on-press feedback without ripples (ripples look cheap on true black). */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return this
        .scale(if (pressed) 0.94f else 1f)
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
}

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
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .alpha(if (enabled) 1f else 0.4f)
            .pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = TextTertiary,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
fun StatusDot(color: Color, pulsing: Boolean, modifier: Modifier = Modifier) {
    val alpha = if (pulsing) {
        val t = rememberInfiniteTransition(label = "dot")
        t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "dotAlpha").value
    } else 1f
    Box(
        modifier
            .size(8.dp)
            .clip(CircleShape)
            .alpha(alpha)
            .background(color)
    )
}

/**
 * App icon from the hub (`/api/icon/<id>`). While loading or if the hub has no icon,
 * a monogram on the app's brand colour is shown instead of an empty box.
 */
@Composable
fun AppIcon(app: LauncherApp, iconUrl: String, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    var loaded by remember(iconUrl) { mutableStateOf(false) }
    val brand = app.brandColor?.let { Color(it) } ?: Surface3
    val shape = RoundedCornerShape(size * 0.26f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (loaded) Surface2 else brand)
            .border(1.dp, Hairline, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(
                text = app.title.take(1).uppercase(),
                color = if (brand.luminance() > 0.55f) Color.Black else Color.White,
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        AsyncImage(
            model = iconUrl,
            contentDescription = app.title,
            contentScale = ContentScale.Fit,
            onSuccess = { loaded = true },
            onError = { loaded = false },
            modifier = Modifier
                .size(size)
                .clip(shape),
        )
    }
}

@Composable
fun EmptyHint(title: String, body: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(body, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
