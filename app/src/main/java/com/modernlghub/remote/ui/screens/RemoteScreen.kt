package com.modernlghub.remote.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.modernlghub.remote.data.Connection
import com.modernlghub.remote.data.LauncherApp
import com.modernlghub.remote.data.TvState
import com.modernlghub.remote.ui.components.AppIcon
import com.modernlghub.remote.ui.components.AudioOutputSheet
import com.modernlghub.remote.ui.components.CursorPad
import com.modernlghub.remote.ui.components.DPadControl
import com.modernlghub.remote.ui.components.GesturePad
import com.modernlghub.remote.ui.components.KeyboardSheet
import com.modernlghub.remote.ui.components.MoreKeysSheet
import com.modernlghub.remote.ui.components.PictureModeSheet
import com.modernlghub.remote.ui.components.RoundButton
import com.modernlghub.remote.ui.components.StatusDot
import com.modernlghub.remote.ui.components.card
import com.modernlghub.remote.ui.components.pressable
import com.modernlghub.remote.ui.components.rememberHaptics
import com.modernlghub.remote.ui.theme.Accent
import com.modernlghub.remote.ui.theme.AccentDim
import com.modernlghub.remote.ui.theme.Hairline
import com.modernlghub.remote.ui.theme.StatusOff
import com.modernlghub.remote.ui.theme.StatusOn
import com.modernlghub.remote.ui.theme.StatusWarn
import com.modernlghub.remote.ui.theme.Surface1
import com.modernlghub.remote.ui.theme.Surface2
import com.modernlghub.remote.ui.theme.TextPrimary
import com.modernlghub.remote.ui.theme.TextSecondary
import com.modernlghub.remote.ui.theme.TextTertiary

private enum class Sheet { NONE, KEYBOARD, MORE, PICTURE, AUDIO }

@Composable
fun RemoteScreen(
    state: TvState,
    padMode: Int,
    hubAddress: String,
    onPadModeChange: (Int) -> Unit,
    onIntent: (RemoteIntent) -> Unit,
    onOpenSettings: () -> Unit,
    iconUrl: (String) -> String,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    var sheet by rememberSaveable { mutableStateOf(Sheet.NONE) }
    val ambient = ambientColor(state)
    val glow by animateColorAsState(ambient, tween(800), label = "ambient")

    Column(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(glow.copy(alpha = 0.13f), Color.Transparent),
                        center = Offset(size.width * 0.5f, 0f),
                        radius = size.width * 1.1f,
                    )
                )
            }
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Header(state, onPower = { haptics.heavy(); onIntent(RemoteIntent.Power) }, onOpenSettings = onOpenSettings)
        Spacer(Modifier.height(16.dp))

        val usable = state.isOnline && !state.status.isStandby
        if (usable) {
            StateChips(
                state = state,
                onSound = { haptics.confirm(); onIntent(RemoteIntent.ToggleSound) },
                onSoundMenu = { haptics.heavy(); sheet = Sheet.AUDIO },
                onScreen = { haptics.confirm(); onIntent(RemoteIntent.ToggleScreen) },
                onPc = { haptics.confirm(); onIntent(RemoteIntent.Pc) },
                onPicture = { haptics.click(); sheet = Sheet.PICTURE },
            )
            Spacer(Modifier.height(16.dp))
            PadModeSwitch(padMode) { haptics.tick(); onPadModeChange(it) }
            Spacer(Modifier.height(12.dp))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            AnimatedContent(
                targetState = if (usable) padMode else -1,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "pad",
            ) { mode ->
                val padModifier = Modifier.fillMaxSize()
                when (mode) {
                    -1 -> OfflineCard(state, hubAddress, onIntent, onOpenSettings, padModifier)
                    1 -> DPadControl(padModifier, onDirection = { onIntent(RemoteIntent.Key(it.key)) }, onSelect = { onIntent(RemoteIntent.Key("ENTER")) })
                    2 -> CursorPad(padModifier)
                    else -> GesturePad(padModifier, onDirection = { onIntent(RemoteIntent.Key(it.key)) }, onSelect = { onIntent(RemoteIntent.Key("ENTER")) })
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        NavRow(
            enabled = usable,
            onKey = { haptics.click(); onIntent(RemoteIntent.Key(it)) },
            onKeyboard = { haptics.click(); sheet = Sheet.KEYBOARD },
            onMore = { haptics.click(); sheet = Sheet.MORE },
        )
        Spacer(Modifier.height(12.dp))
        VolumeBar(
            state = state,
            enabled = usable,
            onDown = { haptics.tick(); onIntent(RemoteIntent.VolumeDown) },
            onUp = { haptics.tick(); onIntent(RemoteIntent.VolumeUp) },
            onMute = { haptics.click(); onIntent(RemoteIntent.Mute) },
        )
        Spacer(Modifier.height(16.dp))
        Favorites(state, iconUrl) { haptics.confirm(); onIntent(RemoteIntent.Launch(it.id)) }
        Spacer(Modifier.height(12.dp))
    }

    when (sheet) {
        Sheet.KEYBOARD -> KeyboardSheet(
            onTextChange = { onIntent(RemoteIntent.Text(it)) },
            onEnter = { onIntent(RemoteIntent.Enter) },
            onDismiss = { sheet = Sheet.NONE },
        )
        Sheet.MORE -> MoreKeysSheet(onKey = { onIntent(RemoteIntent.Key(it)) }, onDismiss = { sheet = Sheet.NONE })
        Sheet.PICTURE -> PictureModeSheet(onSelect = { onIntent(RemoteIntent.Picture(it)) }, onDismiss = { sheet = Sheet.NONE })
        Sheet.AUDIO -> AudioOutputSheet(state.status.audio, onSelect = { onIntent(RemoteIntent.Sound(it)) }, onDismiss = { sheet = Sheet.NONE })
        Sheet.NONE -> Unit
    }
}

/** Subtle top glow tinted with the running app's brand colour (Netflix red, Disney blue, …). */
private fun ambientColor(state: TvState): Color {
    if (!state.isOnline) return Color.Transparent
    if (state.status.screenOff) return StatusWarn
    if (state.status.currentAppId.contains("hdmi")) return Accent
    val brand = state.currentApp?.brandColor?.let { Color(it) } ?: return Accent
    val l = brand.luminance()
    return if (l < 0.04f || l > 0.85f) Accent else brand
}

@Composable
private fun Header(state: TvState, onPower: () -> Unit, onOpenSettings: () -> Unit) {
    val (dotColor, line) = statusLine(state)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(dotColor, pulsing = state.connection == Connection.CONNECTING || state.waking)
                Spacer(Modifier.width(10.dp))
                Text("LG OLED G4", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                line,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 18.dp),
            )
        }
        RoundButton(Icons.Rounded.Settings, "Einstellungen", size = 44.dp, iconSize = 22.dp, container = Color.Transparent, tint = TextSecondary, onClick = onOpenSettings)
        Spacer(Modifier.width(8.dp))
        val on = state.isOn
        RoundButton(
            Icons.Rounded.PowerSettingsNew,
            if (on) "Ausschalten" else "Einschalten",
            size = 48.dp,
            iconSize = 22.dp,
            container = if (on) Surface2 else AccentDim,
            tint = if (on) StatusOff else Accent,
            enabled = !state.waking,
            onClick = onPower,
        )
    }
}

private fun statusLine(state: TvState): Pair<Color, String> {
    val s = state.status
    return when {
        state.waking -> StatusWarn to "Wird eingeschaltet …"
        state.connection == Connection.CONNECTING -> TextTertiary to "Verbinde …"
        state.connection == Connection.UNAUTHORIZED -> StatusWarn to "Hub verlangt einen Token"
        state.connection == Connection.OFFLINE -> StatusOff to "Aus oder nicht erreichbar"
        s.isStandby -> StatusWarn to "Standby"
        s.screenOff -> StatusWarn to "Bildschirm aus · ${s.audio.outputLabel}"
        else -> {
            val app = state.appTitle().ifBlank { "Eingeschaltet" }
            StatusOn to "$app · ${s.audio.outputLabel}"
        }
    }
}

@Composable
private fun StateChips(
    state: TvState,
    onSound: () -> Unit,
    onSoundMenu: () -> Unit,
    onScreen: () -> Unit,
    onPc: () -> Unit,
    onPicture: () -> Unit,
) {
    val s = state.status
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val m = Modifier.weight(1f)
        Chip(Icons.Rounded.Speaker, s.audio.chipLabel, active = s.audio.isOptical, modifier = m, onClick = onSound, onLongClick = onSoundMenu)
        Chip(Icons.Rounded.DesktopAccessDisabled, if (s.screenOff) "Bild an" else "Bild aus", active = s.screenOff, modifier = m, onClick = onScreen)
        Chip(Icons.Rounded.Computer, "PC", active = s.currentAppId.endsWith("hdmi1"), modifier = m, onClick = onPc)
        Chip(Icons.Rounded.Tune, "Modus", active = false, modifier = m, onClick = onPicture)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Chip(
    icon: ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val bg by animateColorAsState(if (active) AccentDim else Surface1, tween(200), label = "chip")
    val fg by animateColorAsState(if (active) Accent else TextPrimary, tween(200), label = "chipFg")
    Row(
        modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, if (active) Accent.copy(alpha = 0.35f) else Hairline, CircleShape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = fg, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun PadModeSwitch(mode: Int, onChange: (Int) -> Unit) {
    val modes = listOf(
        Triple(Icons.Rounded.Swipe, "Wischen", 0),
        Triple(Icons.Rounded.Gamepad, "Steuerkreuz", 1),
        Triple(Icons.Rounded.Mouse, "Mauszeiger", 2),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(Surface1)
                .border(1.dp, Hairline, CircleShape)
                .padding(3.dp),
        ) {
            modes.forEach { (icon, label, value) ->
                val selected = mode == value
                Row(
                    Modifier
                        .height(32.dp)
                        .clip(CircleShape)
                        .background(if (selected) Surface2 else Color.Transparent)
                        .pressable(role = Role.Tab) { onChange(value) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = null, tint = if (selected) TextPrimary else TextTertiary, modifier = Modifier.size(16.dp))
                    if (selected) {
                        Spacer(Modifier.width(6.dp))
                        Text(label, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun NavRow(enabled: Boolean, onKey: (String) -> Unit, onKeyboard: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", enabled = enabled) { onKey("BACK") }
        RoundButton(Icons.Rounded.Home, "Home", enabled = enabled) { onKey("HOME") }
        RoundButton(Icons.Rounded.PlayArrow, "Wiedergabe/Pause", enabled = enabled) { onKey("PLAY") }
        RoundButton(Icons.Rounded.Keyboard, "Tastatur", enabled = enabled, onClick = onKeyboard)
        RoundButton(Icons.Rounded.MoreHoriz, "Weitere Tasten", enabled = enabled, onClick = onMore)
    }
}

@Composable
private fun VolumeBar(state: TvState, enabled: Boolean, onDown: () -> Unit, onUp: () -> Unit, onMute: () -> Unit) {
    val audio = state.status.audio
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .card(CircleShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButton(Icons.Rounded.Remove, "Leiser", container = Color.Transparent, enabled = enabled, onClick = onDown)
        Row(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .pressable(enabled = enabled, onClick = onMute),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (audio.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = if (audio.muted) "Ton an" else "Stumm",
                tint = if (audio.muted) StatusWarn else TextSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    !state.isOnline -> "Lautstärke"
                    audio.muted -> "Stumm"
                    else -> audio.volume.toString()
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (audio.muted) StatusWarn else TextPrimary,
            )
        }
        RoundButton(Icons.Rounded.Add, "Lauter", container = Color.Transparent, enabled = enabled, onClick = onUp)
    }
}

@Composable
private fun Favorites(state: TvState, iconUrl: (String) -> String, onLaunch: (LauncherApp) -> Unit) {
    val favorites = remember(state.apps) {
        state.apps.filter { it.visible && !it.isInput && it.type != "home" }.take(10)
    }
    if (favorites.isEmpty()) {
        Spacer(Modifier.height(52.dp))
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
        items(favorites, key = { it.id }) { app ->
            val running = app.id == state.status.currentAppId
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(if (running) 2.dp else 0.dp, if (running) Accent else Color.Transparent, RoundedCornerShape(16.dp))
                    .padding(if (running) 3.dp else 0.dp)
                    .pressable { onLaunch(app) },
            ) {
                AppIcon(app, iconUrl(app.id), size = if (running) 46.dp else 52.dp)
            }
        }
    }
}

@Composable
private fun OfflineCard(
    state: TvState,
    hubAddress: String,
    onIntent: (RemoteIntent) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.card(RoundedCornerShape(32.dp)).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            state.connection == Connection.CONNECTING || state.waking -> {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    if (state.waking) "Fernseher startet …" else "Suche Fernseher …",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            state.connection == Connection.UNAUTHORIZED -> {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = StatusWarn, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(16.dp))
                Text("Token erforderlich", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Der Hub unter $hubAddress ist mit GEMINI_TOKEN abgesichert. Trag denselben Token in den Einstellungen ein.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
                ) { Text("Einstellungen") }
            }
            else -> {
                Icon(
                    if (state.status.isStandby) Icons.Rounded.Tv else Icons.Rounded.WifiOff,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    if (state.status.isStandby) "Fernseher im Standby" else "Fernseher ist aus",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Oder der Hub unter $hubAddress ist nicht erreichbar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { onIntent(RemoteIntent.Power) },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Einschalten")
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onIntent(RemoteIntent.Retry) }) { Text("Erneut suchen", color = TextPrimary) }
                    OutlinedButton(onClick = onOpenSettings) { Text("Einstellungen", color = TextPrimary) }
                }
            }
        }
    }
}
