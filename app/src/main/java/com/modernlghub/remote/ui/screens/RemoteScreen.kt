package com.modernlghub.remote.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.modernlghub.remote.data.TvState
import com.modernlghub.remote.ui.components.AudioOutputSheet
import com.modernlghub.remote.ui.components.CursorPad
import com.modernlghub.remote.ui.components.DPadControl
import com.modernlghub.remote.ui.components.GesturePad
import com.modernlghub.remote.ui.components.KeyboardSheet
import com.modernlghub.remote.ui.components.MoreKeysSheet
import com.modernlghub.remote.ui.components.PictureModeSheet
import com.modernlghub.remote.ui.components.rememberHaptics
import com.modernlghub.remote.ui.theme.LocalReduceMotion
import com.modernlghub.remote.ui.theme.Motion
import com.modernlghub.remote.ui.theme.motionSpec
import com.modernlghub.remote.ui.theme.Spacing
import com.modernlghub.remote.ui.theme.motion

private enum class Sheet { NONE, KEYBOARD, MORE, PICTURE, AUDIO }

/** Pad modes as stored in the preferences. */
object PadMode {
    const val SWIPE = 0
    const val DPAD = 1
    const val POINTER = 2
}

/** What the remote needs to know about the setup, beyond the live TV state. */
@Immutable
data class RemoteSetup(
    /** Host (and port) of the hub, shown in the offline and setup states. */
    val hubAddress: String,
    /** A MAC for Wake-on-LAN is known; without it "Einschalten" cannot work while the TV is off. */
    val canWake: Boolean = true,
    /** The hub has never answered (no MAC learned yet) and is offline: first run or wrong address. */
    val needsSetup: Boolean = false,
)

/**
 * The product: status, quick toggles, the pad, navigation keys, volume and favourite apps.
 * Stateless apart from which sheet is open; everything else comes in through [state] and leaves
 * through [onIntent].
 */
@Composable
fun RemoteScreen(
    state: TvState,
    padMode: Int,
    setup: RemoteSetup,
    onPadModeChange: (Int) -> Unit,
    onIntent: (RemoteIntent) -> Unit,
    onOpenSettings: () -> Unit,
    iconUrl: (String) -> String,
    modifier: Modifier = Modifier,
    cursorPad: @Composable (Modifier, Color) -> Unit = { m, tint -> CursorPad(m, tint) },
) {
    val haptics = rememberHaptics()
    var sheet by rememberSaveable { mutableStateOf(Sheet.NONE) }
    val glow by animateColorAsState(ambientColor(state), motion(Motion.Ambient), label = "ambient")
    val padTint = padTint(state)
    val usable = state.isOnline && !state.status.isStandby

    val header: @Composable () -> Unit = {
        RemoteHeader(
            state = state,
            onPower = { haptics.heavy(); onIntent(RemoteIntent.Power) },
            onOpenSettings = onOpenSettings,
        )
    }
    val chips: @Composable () -> Unit = {
        QuickChips(
            state = state,
            onSound = { haptics.confirm(); onIntent(RemoteIntent.ToggleSound) },
            onSoundMenu = { haptics.heavy(); sheet = Sheet.AUDIO },
            onScreen = { haptics.confirm(); onIntent(RemoteIntent.ToggleScreen) },
            onPc = { haptics.confirm(); onIntent(RemoteIntent.Pc) },
            onPicture = { haptics.click(); sheet = Sheet.PICTURE },
        )
    }
    val pad: @Composable (Modifier) -> Unit = { m ->
        val reduce = LocalReduceMotion.current
        AnimatedContent(
            targetState = if (usable) padMode else -1,
            transitionSpec = { fadeIn(motionSpec(reduce, Motion.Medium)) togetherWith fadeOut(motionSpec(reduce, Motion.Fast)) },
            label = "pad",
            modifier = m,
        ) { mode ->
            val fill = Modifier.fillMaxSize()
            when (mode) {
                -1 -> RemoteStatePanel(state, setup, onIntent, onOpenSettings, fill)
                PadMode.DPAD -> DPadControl(
                    fill,
                    tint = padTint,
                    onDirection = { onIntent(RemoteIntent.Key(it.key)) },
                    onSelect = { onIntent(RemoteIntent.Key("ENTER")) },
                    onSelectLong = { onIntent(RemoteIntent.Key("LONG_ENTER")) },
                    onRepeatEnd = { onIntent(RemoteIntent.StopRepeat) },
                )
                PadMode.POINTER -> cursorPad(fill, padTint)
                else -> GesturePad(
                    fill,
                    tint = padTint,
                    onDirection = { onIntent(RemoteIntent.Key(it.key)) },
                    onSelect = { onIntent(RemoteIntent.Key("ENTER")) },
                    onSelectLong = { onIntent(RemoteIntent.Key("LONG_ENTER")) },
                    onRepeatEnd = { onIntent(RemoteIntent.StopRepeat) },
                )
            }
        }
    }
    val modeSwitch: @Composable (Boolean) -> Unit = { compact ->
        PadModeSwitch(padMode, compact) { haptics.tick(); onPadModeChange(it) }
    }
    val navRow: @Composable () -> Unit = {
        NavRow(
            onKey = { haptics.click(); onIntent(RemoteIntent.Key(it)) },
            onKeyboard = { haptics.click(); sheet = Sheet.KEYBOARD },
            onMore = { haptics.click(); sheet = Sheet.MORE },
        )
    }
    val volume: @Composable () -> Unit = {
        VolumeBar(
            audio = state.status.audio,
            tint = padTint,
            onDown = { repeat -> if (repeat) haptics.light() else haptics.tick(); onIntent(RemoteIntent.VolumeDown) },
            onUp = { repeat -> if (repeat) haptics.light() else haptics.tick(); onIntent(RemoteIntent.VolumeUp) },
            onMute = { haptics.click(); onIntent(RemoteIntent.Mute) },
        )
    }
    val favorites: @Composable () -> Unit = {
        Favorites(state, iconUrl, padTint) { haptics.confirm(); onIntent(RemoteIntent.Launch(it.id)) }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .drawBehind {
                // Soft glow from the running app's colour, like the TV launcher's focus glow.
                drawRect(
                    Brush.radialGradient(
                        listOf(glow.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.5f, -size.width * 0.15f),
                        radius = size.width * 1.15f,
                    )
                )
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        val landscape = maxWidth > maxHeight && maxHeight < 560.dp
        val compact = maxHeight < 660.dp
        val tight = maxHeight < 580.dp
        if (landscape) {
            // Two panes: controls on the left (scrolls if needed), the pad on the right.
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.Gutter),
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = Spacing.s),
                ) {
                    header()
                    if (usable) {
                        Spacer(Modifier.height(Spacing.m))
                        chips()
                        Spacer(Modifier.height(Spacing.l))
                        navRow()
                        Spacer(Modifier.height(Spacing.m))
                        volume()
                        Spacer(Modifier.height(Spacing.l))
                        favorites()
                    }
                }
                Spacer(Modifier.width(Spacing.xl))
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(vertical = Spacing.s),
                ) {
                    if (usable) {
                        modeSwitch(true)
                        Spacer(Modifier.height(Spacing.s))
                    }
                    pad(Modifier.weight(1f).fillMaxWidth())
                }
            }
        } else {
            Column(
                Modifier
                    .widthIn(max = Spacing.ContentMaxWidth)
                    .fillMaxSize()
                    .padding(horizontal = Spacing.Gutter),
            ) {
                Spacer(Modifier.height(Spacing.s))
                header()
                Spacer(Modifier.height(if (compact) Spacing.s else Spacing.m))
                if (usable) {
                    chips()
                    Spacer(Modifier.height(if (compact) Spacing.s else Spacing.m))
                    modeSwitch(compact)
                    Spacer(Modifier.height(if (compact) Spacing.s else Spacing.m))
                }
                pad(Modifier.weight(1f).fillMaxWidth())
                if (usable) {
                    Spacer(Modifier.height(if (compact) Spacing.m else Spacing.l))
                    navRow()
                    Spacer(Modifier.height(Spacing.m))
                    volume()
                    if (!tight) {
                        Spacer(Modifier.height(if (compact) Spacing.m else Spacing.l))
                        favorites()
                    }
                }
                Spacer(Modifier.height(Spacing.m))
            }
        }
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
    // The sheets only make sense with the TV on: close them when it goes away.
    LaunchedEffect(usable) { if (!usable) sheet = Sheet.NONE }
}
