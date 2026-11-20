package com.modernlghub.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.modernlghub.remote.data.LauncherApp
import com.modernlghub.remote.ui.theme.Hairline
import com.modernlghub.remote.ui.theme.Surface2

/** Squircle-ish app icon shape; [outset] widens it for a ring drawn around the icon. */
fun appIconShape(size: Dp, outset: Dp = 0.dp) = RoundedCornerShape(size * 0.26f + outset)

/** The app's brand colour, or a calm, stable tone derived from its name when the hub has none. */
fun LauncherApp.tint(): Color =
    brandColor?.let { Color(it) } ?: Color.hsv((title.lowercase().hashCode().mod(360)).toFloat(), 0.35f, 0.42f)

/**
 * App icon from the hub (`/api/icon/<id>`), cross-faded in. Until it arrives, or if the hub has no
 * icon, the app's initial sits on its brand colour, so a tile is never an empty box.
 * Decorative by default: the tile around it carries the name for TalkBack.
 */
@Composable
fun AppIcon(
    app: LauncherApp,
    iconUrl: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    contentDescription: String? = null,
) {
    var loaded by remember(iconUrl) { mutableStateOf(false) }
    val brand = app.tint()
    val shape = appIconShape(size)
    val context = LocalContext.current
    val request = remember(iconUrl) {
        ImageRequest.Builder(context).data(iconUrl.ifBlank { null }).crossfade(180).build()
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                if (loaded) Brush.linearGradient(listOf(Surface2, Surface2))
                else Brush.linearGradient(listOf(lighten(brand, 0.10f), brand))
            )
            .border(1.dp, Hairline, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(
                text = app.title.trim().take(1).uppercase(),
                color = if (brand.luminance() > 0.55f) Color.Black else Color.White,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            onSuccess = { loaded = true },
            onError = { loaded = false },
            modifier = Modifier
                .size(size)
                .clip(shape),
        )
    }
}

private fun lighten(c: Color, amount: Float) = Color(
    red = c.red + (1f - c.red) * amount,
    green = c.green + (1f - c.green) * amount,
    blue = c.blue + (1f - c.blue) * amount,
    alpha = c.alpha,
)
