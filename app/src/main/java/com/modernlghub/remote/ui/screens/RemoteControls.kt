package com.modernlghub.remote.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DesktopAccessDisabled
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.modernlghub.remote.data.AudioStatus
import com.modernlghub.remote.data.LauncherApp
import com.modernlghub.remote.data.TvState
import com.modernlghub.remote.ui.components.AppIcon
import com.modernlghub.remote.ui.components.appIconShape
import com.modernlghub.remote.ui.components.RoundButton
import com.modernlghub.remote.ui.components.ToggleChip
import com.modernlghub.remote.ui.components.card
import com.modernlghub.remote.ui.components.pressable
import com.modernlghub.remote.ui.components.repeatingPress
import com.modernlghub.remote.ui.theme.Hairline
import com.modernlghub.remote.ui.theme.MinTouch
import com.modernlghub.remote.ui.theme.Motion
import com.modernlghub.remote.ui.theme.Shape
import com.modernlghub.remote.ui.theme.Spacing
import com.modernlghub.remote.ui.theme.StatusWarn
import com.modernlghub.remote.ui.theme.Surface1
import com.modernlghub.remote.ui.theme.Surface2
import com.modernlghub.remote.ui.theme.TextPrimary
import com.modernlghub.remote.ui.theme.TextSecondary
import com.modernlghub.remote.ui.theme.TextTertiary
import com.modernlghub.remote.ui.theme.motion

/** HDMI input of the PC. */
internal const val PC_INPUT_ID = com.modernlghub.remote.data.TvAction.PC_INPUT_ID

/** Sound output, picture off, PC and picture mode: the four things done most besides navigating. */
@Composable
internal fun QuickChips(
    state: TvState,
    onSound: () -> Unit,
    onSoundMenu: () -> Unit,
    onScreen: () -> Unit,
    onPc: () -> Unit,
    onPicture: () -> Unit,
) {
    val s = state.status
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Equal pills when they fit; icons only when there is room for them; at large font sizes
        // the row scrolls instead of cutting labels off.
        val scroll = LocalDensity.current.fontScale >= 1.15f
        val icons = !scroll && (maxWidth - Spacing.s * 3) / 4 >= 100.dp
        Row(
            if (scroll) Modifier.horizontalScroll(rememberScrollState()) else Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            // Weights follow the label lengths, so no label is cut while PC has room to spare.
            fun w(weight: Float) = if (scroll) Modifier else Modifier.weight(weight)
            ToggleChip(
                s.audio.chipLabel, active = s.audio.isOptical, icon = Icons.Rounded.Speaker, modifier = w(1f), showIcon = icons,
                stateLabel = "Audioausgabe ${s.audio.outputLabel}",
                onLongClickLabel = "Audioausgabe wählen", onLongClick = onSoundMenu, onClick = onSound,
            )
            ToggleChip(
                "Bild aus", active = s.screenOff, icon = Icons.Rounded.DesktopAccessDisabled, modifier = w(1.1f), showIcon = icons,
                stateLabel = if (s.screenOff) "Bild ist aus" else "Bild ist an", onClick = onScreen,
            )
            ToggleChip(
                "PC", active = s.currentAppId == PC_INPUT_ID, icon = Icons.Rounded.Computer, modifier = w(0.7f), showIcon = icons,
                stateLabel = if (s.currentAppId == PC_INPUT_ID) "Aktiver Eingang" else null, onClick = onPc,
            )
            ToggleChip("Bildmodus", active = false, icon = Icons.Rounded.Tune, modifier = w(1.2f), showIcon = icons, onClick = onPicture)
        }
    }
}

private data class ModeOption(val mode: Int, val label: String, val icon: ImageVector)

private val MODES = listOf(
    ModeOption(PadMode.SWIPE, "Wischen", Icons.Rounded.Swipe),
    ModeOption(PadMode.DPAD, "Steuerkreuz", Icons.Rounded.Gamepad),
    ModeOption(PadMode.POINTER, "Mauszeiger", Icons.Rounded.Mouse),
)

/** Segmented switch between the three pad modes; the selected one shows its name. */
@Composable
internal fun PadModeSwitch(mode: Int, compact: Boolean, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Row(
            Modifier
                .clip(Shape.Pill)
                .background(Surface1)
                .border(1.dp, Hairline, Shape.Pill)
                .padding(3.dp),
        ) {
            MODES.forEach { option ->
                val selected = mode == option.mode
                val bg by animateColorAsState(if (selected) Surface2 else Color.Transparent, motion(Motion.Medium), label = "segment")
                Row(
                    Modifier
                        .heightIn(min = if (compact) 40.dp else 42.dp)
                        .widthIn(min = 52.dp)
                        .semantics {
                            role = Role.Tab
                            this.selected = selected
                            contentDescription = option.label
                        }
                        .pressable(role = Role.Tab) { if (!selected) onChange(option.mode) }
                        .clip(Shape.Pill)
                        .background(bg)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(option.icon, contentDescription = null, tint = if (selected) TextPrimary else TextTertiary, modifier = Modifier.size(18.dp))
                    if (selected) {
                        Spacer(Modifier.width(Spacing.s))
                        Text(option.label, style = MaterialTheme.typography.labelLarge, color = TextPrimary, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
internal fun NavRow(onKey: (String) -> Unit, onKeyboard: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") { onKey("BACK") }
        RoundButton(Icons.Rounded.Home, "Home") { onKey("HOME") }
        RoundButton(Icons.Rounded.PlayArrow, "Wiedergabe / Pause") { onKey("PLAY") }
        RoundButton(Icons.Rounded.Keyboard, "Texteingabe", onClick = onKeyboard)
        RoundButton(Icons.Rounded.MoreHoriz, "Weitere Tasten", onClick = onMore)
    }
}

/**
 * Volume capsule: − and + repeat while held, the middle toggles mute. A faint fill behind shows
 * the level, so the number is readable at a glance and the bar tells "loud" from "quiet".
 */
@Composable
internal fun VolumeBar(
    audio: AudioStatus,
    tint: Color,
    onDown: (repeat: Boolean) -> Unit,
    onUp: (repeat: Boolean) -> Unit,
    onMute: () -> Unit,
) {
    val level by animateFloatAsState(
        if (audio.muted) 0f else (audio.volume / 100f).coerceIn(0f, 1f),
        motion(Motion.Medium),
        label = "volume",
    )
    val fill = if (audio.muted) StatusWarn else tint
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .card(Shape.Pill)
            .drawBehind {
                // Level as a hairline along the bottom of the capsule.
                if (level > 0f) {
                    val h = 2.dp.toPx()
                    val inset = size.height / 2f
                    val track = size.width - 2 * inset
                    drawRoundRect(Hairline, Offset(inset, size.height - h - 7.dp.toPx()), Size(track, h), CornerRadius(h))
                    drawRoundRect(fill.copy(alpha = 0.8f), Offset(inset, size.height - h - 7.dp.toPx()), Size(track * level, h), CornerRadius(h))
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VolumeStepButton(Icons.Rounded.Remove, "Leiser", onDown)
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .semantics {
                    stateDescription = if (audio.muted) "Stumm" else "Lautstärke ${audio.volume}"
                }
                .pressable(role = Role.Switch, onClickLabel = if (audio.muted) "Ton an" else "Stummschalten", onClick = onMute),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (audio.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = null,
                tint = if (audio.muted) StatusWarn else TextSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(Spacing.s))
            Text(
                if (audio.muted) "Stumm" else audio.volume.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = if (audio.muted) StatusWarn else TextPrimary,
            )
        }
        VolumeStepButton(Icons.Rounded.Add, "Lauter", onUp)
    }
}

@Composable
private fun VolumeStepButton(icon: ImageVector, label: String, onStep: (Boolean) -> Unit) {
    var repeating by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (repeating) Surface2 else Color.Transparent, motion(Motion.Fast), label = "volBg")
    Box(
        Modifier
            .size(60.dp)
            .semantics { contentDescription = label }
            .repeatingPress(enabled = true, label = label, onRepeatingChange = { repeating = it }, onStep = onStep)
            .padding(6.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(24.dp))
    }
}

/** First ten launchable apps from the hub, in the hub's order; the running one is ringed. */
@Composable
internal fun Favorites(state: TvState, iconUrl: (String) -> String, tint: Color, onLaunch: (LauncherApp) -> Unit) {
    val favorites = remember(state.apps) {
        state.apps.filter { it.visible && !it.isInput && it.type != "home" }.take(10)
    }
    if (favorites.isEmpty()) {
        Spacer(Modifier.height(MinTouch + 12.dp))
        return
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(favorites, key = { it.id }) { app ->
            val running = app.id == state.status.currentAppId
            val ring = appIconShape(52.dp, outset = 4.dp)
            Box(
                Modifier
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${app.title} öffnen"
                        if (running) stateDescription = "Läuft"
                    }
                    .pressable { onLaunch(app) }
                    .border(2.dp, if (running) tint else Color.Transparent, ring)
                    .padding(4.dp),
            ) {
                AppIcon(app, iconUrl(app.id), size = 52.dp)
            }
        }
    }
}
