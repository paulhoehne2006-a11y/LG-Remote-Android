package com.modernlghub.remote.system

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DesktopAccessDisabled
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.modernlghub.remote.data.Connection
import com.modernlghub.remote.data.TvRepository
import com.modernlghub.remote.data.TvState
import com.modernlghub.remote.ui.MainActivity
import com.modernlghub.remote.ui.components.Direction
import com.modernlghub.remote.ui.components.GesturePad
import com.modernlghub.remote.ui.components.Haptics
import com.modernlghub.remote.ui.components.RoundButton
import com.modernlghub.remote.ui.components.StateAction
import com.modernlghub.remote.ui.components.StatePanel
import com.modernlghub.remote.ui.components.StatusDot
import com.modernlghub.remote.ui.components.ToggleChip
import com.modernlghub.remote.ui.handleVolumeKey
import com.modernlghub.remote.ui.isVolumeKey
import com.modernlghub.remote.ui.screens.TV_NAME
import com.modernlghub.remote.ui.screens.padTint
import com.modernlghub.remote.ui.screens.statusLine
import com.modernlghub.remote.ui.theme.Accent
import com.modernlghub.remote.ui.theme.ModernLgTheme
import com.modernlghub.remote.ui.theme.Motion
import com.modernlghub.remote.ui.theme.Scrim
import com.modernlghub.remote.ui.theme.Shape
import com.modernlghub.remote.ui.theme.Spacing
import com.modernlghub.remote.ui.theme.StatusWarn
import com.modernlghub.remote.ui.theme.Surface1
import com.modernlghub.remote.ui.theme.Surface3
import com.modernlghub.remote.ui.theme.TextPrimary
import com.modernlghub.remote.ui.theme.TextSecondary
import com.modernlghub.remote.ui.theme.motion

/**
 * Floating mini remote, opened from the Quick Settings tile, widget, notification or a shortcut.
 * Sits over whatever app is running; tap outside, swipe back or the close button to dismiss.
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
                QuickRemoteOverlay(
                    state = state,
                    actions = remember { OverlayActions(repo, haptics) },
                    onFinished = { finish() },
                    onExpand = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                )
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        handleVolumeKey(keyCode, repo, haptics) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (isVolumeKey(keyCode) && repo.prefs.hardwareVolumeEnabled && repo.state.value.isOnline) {
            if (event?.isCanceled != true && (event?.eventTime ?: 0L) - (event?.downTime ?: 0L) > 400L) repo.cancelPendingKeys()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}

/** What the mini remote can do; one place for haptics so every button feels like the main app. */
class OverlayActions(private val repo: TvRepository?, private val haptics: Haptics?) {
    fun key(name: String) { haptics?.click(); repo?.key(name) }
    fun direction(d: Direction) { repo?.key(d.key) }
    fun ok() { repo?.key("ENTER") }
    fun stopRepeat() { repo?.cancelPendingKeys() }
    fun okLong() { repo?.key("LONG_ENTER") }
    fun volumeUp() { haptics?.tick(); repo?.volumeUp() }
    fun volumeDown() { haptics?.tick(); repo?.volumeDown() }
    fun mute() { haptics?.click(); repo?.toggleMute() }
    fun sound() { haptics?.confirm(); repo?.toggleSoundOutput() }
    fun screen() { haptics?.confirm(); repo?.toggleScreen() }
    fun wake() { haptics?.heavy(); repo?.wake() }
    fun retry() { repo?.resetConnection() }
}

/**
 * The mini remote as a bottom panel over a scrim. Slides in, and out again before the activity
 * finishes (it used to vanish abruptly). Stateless apart from its own visibility.
 */
@Composable
fun QuickRemoteOverlay(
    state: TvState,
    actions: OverlayActions,
    onFinished: () -> Unit,
    onExpand: () -> Unit,
) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val finished by rememberUpdatedState(onFinished)
    val close = { visible.targetState = false }
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (visible.isIdle && !visible.currentState && !visible.targetState) finished()
    }
    BackHandler(onBack = close)

    Box(
        Modifier
            .fillMaxSize()
            .background(Scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close)
            .statusBarsPadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = slideInVertically(motion<IntOffset>(Motion.Slow, Motion.Enter)) { it / 3 } + fadeIn(motion(Motion.Medium)),
            exit = slideOutVertically(motion<IntOffset>(Motion.Medium, Motion.Exit)) { it / 3 } + fadeOut(motion(Motion.Medium)),
        ) {
            QuickRemotePanel(state, actions, onClose = close, onExpand = onExpand)
        }
    }
}

@Composable
fun QuickRemotePanel(state: TvState, actions: OverlayActions, onClose: () -> Unit, onExpand: () -> Unit) {
    val usable = state.isOnline && !state.status.isStandby
    val tint = padTint(state)
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        // In landscape or on a small screen the pad gives way; the panel never runs off screen.
        val padHeight = (maxHeight - 260.dp).coerceIn(150.dp, 280.dp)
        Column(
            Modifier
                .widthIn(max = Spacing.ContentMaxWidth)
                .fillMaxWidth()
                .clip(Shape.Sheet)
                .background(Surface1)
                // Swallow taps so they don't reach the scrim.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding()
                .padding(horizontal = Spacing.Gutter)
                .padding(top = Spacing.m, bottom = Spacing.l),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Surface3)
            )
            Spacer(Modifier.height(Spacing.m))
            OverlayHeader(state, onClose, onExpand)
            Spacer(Modifier.height(Spacing.l))

            if (usable) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    ToggleChip(
                        state.status.audio.chipLabel, active = state.status.audio.isOptical, icon = Icons.Rounded.Speaker,
                        stateLabel = "Audioausgabe ${state.status.audio.outputLabel}", modifier = Modifier.weight(1f),
                        onClick = actions::sound,
                    )
                    ToggleChip(
                        "Display aus", active = state.status.screenOff, icon = Icons.Rounded.DesktopAccessDisabled,
                        stateLabel = if (state.status.screenOff) "Display ist aus" else "Display ist an", modifier = Modifier.weight(1f),
                        onClick = actions::screen,
                    )
                }
                Spacer(Modifier.height(Spacing.m))
                GesturePad(
                    Modifier
                        .fillMaxWidth()
                        .height(padHeight),
                    tint = tint,
                    showHint = false,
                    onDirection = actions::direction,
                    onSelect = actions::ok,
                    onSelectLong = actions::okLong,
                    onRepeatEnd = actions::stopRepeat,
                )
                Spacer(Modifier.height(Spacing.m))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", size = 52.dp) { actions.key("BACK") }
                    RoundButton(Icons.Rounded.Home, "Home", size = 52.dp) { actions.key("HOME") }
                    RoundButton(Icons.Rounded.PlayArrow, "Wiedergabe / Pause", size = 52.dp) { actions.key("PLAY") }
                    RoundButton(Icons.Rounded.Remove, "Leiser", size = 52.dp, onClick = actions::volumeDown)
                    val muted = state.status.audio.muted
                    RoundButton(
                        if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        if (muted) "Ton an" else "Stummschalten",
                        size = 52.dp,
                        tint = if (muted) StatusWarn else TextPrimary,
                        onClick = actions::mute,
                    )
                    RoundButton(Icons.Rounded.Add, "Lauter", size = 52.dp, onClick = actions::volumeUp)
                }
            } else {
                val panel = Modifier.fillMaxWidth().heightIn(min = 200.dp)
                when {
                    state.waking || state.connection == Connection.CONNECTING -> StatePanel(
                        title = if (state.waking) "Fernseher startet …" else "Verbinde …",
                        body = null,
                        busy = true,
                        tint = if (state.waking) Accent else TextSecondary,
                        modifier = panel,
                    )
                    state.connection == Connection.UNAUTHORIZED -> StatePanel(
                        title = "Token erforderlich",
                        body = "Trag den Token des Hubs in der App ein.",
                        icon = Icons.Rounded.Lock,
                        tint = StatusWarn,
                        primary = StateAction("App öffnen", Icons.Rounded.OpenInFull, onExpand),
                        modifier = panel,
                    )
                    else -> StatePanel(
                        title = if (state.status.isStandby) "Fernseher im Standby" else "Fernseher ist aus",
                        body = null,
                        icon = Icons.Rounded.PowerSettingsNew,
                        primary = StateAction("Einschalten", Icons.Rounded.PowerSettingsNew, actions::wake),
                        secondary = if (state.status.isStandby) emptyList() else listOf(StateAction("Erneut versuchen", onClick = actions::retry)),
                        modifier = panel,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayHeader(state: TvState, onClose: () -> Unit, onExpand: () -> Unit) {
    val (color, line) = statusLine(state)
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(color, pulsing = state.waking || state.connection == Connection.CONNECTING)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            Text(TV_NAME, style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.semantics { heading() })
            Text(line, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RoundButton(Icons.Rounded.OpenInFull, "App öffnen", size = 40.dp, iconSize = 18.dp, container = Color.Transparent, tint = TextSecondary, onClick = onExpand)
        RoundButton(Icons.Rounded.Close, "Schließen", size = 40.dp, iconSize = 20.dp, container = Color.Transparent, tint = TextSecondary, onClick = onClose)
    }
}
