package com.modernlghub.remote.system

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DesktopAccessDisabled
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.modernlghub.remote.data.Connection
import com.modernlghub.remote.data.TvRepository
import com.modernlghub.remote.data.TvState
import com.modernlghub.remote.ui.MainActivity
import com.modernlghub.remote.ui.components.GesturePad
import com.modernlghub.remote.ui.components.Haptics
import com.modernlghub.remote.ui.components.RoundButton
import com.modernlghub.remote.ui.components.StatusDot
import com.modernlghub.remote.ui.handleVolumeKey
import com.modernlghub.remote.ui.isVolumeKey
import com.modernlghub.remote.ui.theme.Accent
import com.modernlghub.remote.ui.theme.AccentDim
import com.modernlghub.remote.ui.theme.Hairline
import com.modernlghub.remote.ui.theme.ModernLgTheme
import com.modernlghub.remote.ui.theme.StatusOff
import com.modernlghub.remote.ui.theme.StatusOn
import com.modernlghub.remote.ui.theme.StatusWarn
import com.modernlghub.remote.ui.theme.Surface1
import com.modernlghub.remote.ui.theme.TextPrimary
import com.modernlghub.remote.ui.theme.TextSecondary
import com.modernlghub.remote.ui.theme.TextTertiary

/**
 * Floating mini remote, opened from the Quick Settings tile, widget, notification or a shortcut.
 * Sits over whatever app is running; tap outside to dismiss.
 */
class QuickRemoteOverlayActivity : ComponentActivity() {

    private val repo by lazy { TvRepository.get(this) }
    private val haptics by lazy { Haptics(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Surface1.toArgb()),
        )
        super.onCreate(savedInstanceState)
        setContent {
            ModernLgTheme {
                val state by repo.state.collectAsStateWithLifecycle()
                OverlayContent(
                    state = state,
                    repo = repo,
                    haptics = haptics,
                    onClose = { finish() },
                    onExpand = {
                        startActivity(android.content.Intent(this, MainActivity::class.java))
                        finish()
                    },
                )
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        handleVolumeKey(keyCode, repo, haptics) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (isVolumeKey(keyCode) && repo.prefs.hardwareVolumeEnabled && repo.state.value.isOnline) return true
        return super.onKeyUp(keyCode, event)
    }
}

@Composable
private fun OverlayContent(
    state: TvState,
    repo: TvRepository,
    haptics: Haptics,
    onClose: () -> Unit,
    onExpand: () -> Unit,
) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = slideInVertically(tween(260)) { it / 3 } + fadeIn(tween(200)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(Surface1)
                    // Swallow taps so they don't reach the scrim.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp, bottom = 16.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Hairline)
                )
                Spacer(Modifier.height(14.dp))
                OverlayHeader(state, onClose, onExpand)
                Spacer(Modifier.height(16.dp))

                if (state.isOnline && !state.status.isStandby) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniChip(Icons.Rounded.Speaker, state.status.audio.outputLabel, state.status.audio.isOptical, Modifier.weight(1f)) {
                            haptics.confirm(); repo.toggleSoundOutput()
                        }
                        MiniChip(
                            Icons.Rounded.DesktopAccessDisabled,
                            if (state.status.screenOff) "Bild ist aus" else "Bild aus",
                            state.status.screenOff,
                            Modifier.weight(1f),
                        ) { haptics.confirm(); repo.toggleScreen() }
                    }
                    Spacer(Modifier.height(12.dp))
                    GesturePad(
                        Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        onDirection = { repo.key(it.key) },
                        onSelect = { repo.key("ENTER") },
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", size = 52.dp) { haptics.click(); repo.key("BACK") }
                        RoundButton(Icons.Rounded.Home, "Home", size = 52.dp) { haptics.click(); repo.key("HOME") }
                        RoundButton(Icons.Rounded.PlayArrow, "Wiedergabe", size = 52.dp) { haptics.click(); repo.key("PLAY") }
                        RoundButton(Icons.Rounded.Remove, "Leiser", size = 52.dp) { haptics.tick(); repo.volumeDown() }
                        RoundButton(
                            if (state.status.audio.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                            "Stumm",
                            size = 52.dp,
                            tint = if (state.status.audio.muted) StatusWarn else TextPrimary,
                        ) { haptics.click(); repo.toggleMute() }
                        RoundButton(Icons.Rounded.Add, "Lauter", size = 52.dp) { haptics.tick(); repo.volumeUp() }
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoundButton(
                            Icons.Rounded.PowerSettingsNew, "Einschalten",
                            size = 72.dp, iconSize = 30.dp, container = AccentDim, tint = Accent,
                            enabled = !state.waking && state.connection != Connection.CONNECTING,
                        ) { haptics.heavy(); repo.wake() }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayHeader(state: TvState, onClose: () -> Unit, onExpand: () -> Unit) {
    val (color, line) = when {
        state.waking -> StatusWarn to "Wird eingeschaltet …"
        state.connection == Connection.CONNECTING -> TextTertiary to "Verbinde …"
        state.connection == Connection.UNAUTHORIZED -> StatusWarn to "Hub verlangt Token · App öffnen"
        !state.isOnline -> StatusOff to "Aus · tippen zum Einschalten"
        state.status.isStandby -> StatusWarn to "Standby"
        state.status.screenOff -> StatusWarn to "Bildschirm aus"
        else -> StatusOn to state.appTitle().ifBlank { "Eingeschaltet" }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(color, pulsing = state.waking || state.connection == Connection.CONNECTING)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("LG OLED G4", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(line, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RoundButton(Icons.Rounded.OpenInFull, "App öffnen", size = 40.dp, iconSize = 18.dp, container = Color.Transparent, tint = TextSecondary, onClick = onExpand)
        RoundButton(Icons.Rounded.Close, "Schließen", size = 40.dp, iconSize = 20.dp, container = Color.Transparent, tint = TextSecondary, onClick = onClose)
    }
}

@Composable
private fun MiniChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(if (active) AccentDim else com.modernlghub.remote.ui.theme.Surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = if (active) Accent else TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) Accent else TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
