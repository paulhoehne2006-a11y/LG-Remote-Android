package com.modernlghub.remote.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.modernlghub.remote.data.Connection
import com.modernlghub.remote.data.TvState
import com.modernlghub.remote.ui.components.RoundButton
import com.modernlghub.remote.ui.components.StateAction
import com.modernlghub.remote.ui.components.StatePanel
import com.modernlghub.remote.ui.components.StatusDot
import com.modernlghub.remote.ui.components.card
import com.modernlghub.remote.ui.theme.Accent
import com.modernlghub.remote.ui.theme.AccentDim
import com.modernlghub.remote.ui.theme.Shape
import com.modernlghub.remote.ui.theme.Spacing
import com.modernlghub.remote.ui.theme.StatusOff
import com.modernlghub.remote.ui.theme.StatusOn
import com.modernlghub.remote.ui.theme.StatusWarn
import com.modernlghub.remote.ui.theme.Surface2
import com.modernlghub.remote.ui.theme.TextPrimary
import com.modernlghub.remote.ui.theme.TextSecondary
import com.modernlghub.remote.ui.theme.TextTertiary

/** Name of the one TV this app drives. */
val TV_NAME: String get() = com.modernlghub.remote.data.TvState.TV_NAME

@Composable
internal fun RemoteHeader(state: TvState, onPower: () -> Unit, onOpenSettings: () -> Unit) {
    val (dotColor, line) = statusLine(state)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 56.dp)) {
        Column(
            Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(dotColor, pulsing = state.connection == Connection.CONNECTING || state.waking)
                Spacer(Modifier.width(10.dp))
                Text(
                    TV_NAME,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Text(
                line,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 18.dp, top = 2.dp),
            )
        }
        RoundButton(
            Icons.Rounded.Settings, "Einstellungen",
            size = 44.dp, iconSize = 22.dp, container = Color.Transparent, tint = TextSecondary,
            onClick = onOpenSettings,
        )
        Spacer(Modifier.width(Spacing.xs))
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

internal fun statusLine(state: TvState): Pair<Color, String> {
    val s = state.status
    return when {
        state.waking -> StatusWarn to "Wird eingeschaltet …"
        state.connection == Connection.CONNECTING -> TextTertiary to "Verbinde …"
        state.connection == Connection.UNAUTHORIZED -> StatusWarn to "Hub verlangt einen Token"
        state.connection == Connection.OFFLINE -> StatusOff to "Aus oder nicht erreichbar"
        s.isStandby -> StatusWarn to "Standby"
        s.screenOff -> StatusWarn to "Bild aus · ${s.audio.outputLabel}"
        else -> {
            val app = state.appTitle().ifBlank { "Eingeschaltet" }
            StatusOn to "$app · ${s.audio.outputLabel}"
        }
    }
}

/** The running app's brand colour if it reads well on black, else the accent. */
private fun brandTint(state: TvState): Color? {
    if (state.status.currentAppId.contains("hdmi")) return null
    val brand = state.currentApp?.brandColor?.let { Color(it) } ?: return null
    val l = brand.luminance()
    return if (l < 0.04f || l > 0.85f) null else brand
}

/** Background glow: the running app's colour; amber while the picture is off; none when offline. */
internal fun ambientColor(state: TvState): Color = when {
    !state.isOnline -> Color.Transparent
    state.status.screenOff -> StatusWarn
    else -> brandTint(state) ?: Accent
}

/** Touch feedback colour on the pad and controls: content colour, never the warning amber. */
internal fun padTint(state: TvState): Color = brandTint(state) ?: Accent

/**
 * Everything the pad area shows when the TV can't be controlled: connecting, waking, needs a
 * token, never set up, off/unreachable, standby. Each state has one obvious next step.
 */
@Composable
internal fun RemoteStatePanel(
    state: TvState,
    setup: RemoteSetup,
    onIntent: (RemoteIntent) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = StateAction("Einstellungen", onClick = onOpenSettings)
    val retry = StateAction("Erneut versuchen", Icons.Rounded.Refresh) { onIntent(RemoteIntent.Retry) }
    val wake = StateAction("Einschalten", Icons.Rounded.PowerSettingsNew) { onIntent(RemoteIntent.Power) }
    val panel = modifier.card(Shape.Pad)
    when {
        state.waking -> StatePanel(
            title = "Fernseher startet …",
            body = "Das dauert ein paar Sekunden.",
            busy = true,
            modifier = panel,
        )
        state.connection == Connection.CONNECTING -> StatePanel(
            title = "Verbinde …",
            body = "Suche den Hub unter ${setup.hubAddress}.",
            busy = true,
            tint = TextSecondary,
            modifier = panel,
        )
        state.connection == Connection.UNAUTHORIZED -> StatePanel(
            title = "Token erforderlich",
            body = "Der Hub unter ${setup.hubAddress} ist mit einem Token geschützt. Trag denselben Token in den Einstellungen ein.",
            icon = Icons.Rounded.Lock,
            tint = StatusWarn,
            primary = settings,
            modifier = panel,
        )
        state.connection == Connection.OFFLINE && setup.needsSetup -> StatePanel(
            title = "Hub einrichten",
            body = "Unter ${setup.hubAddress} antwortet kein Hub. Schalte den Fernseher ein und such den Hub in den Einstellungen im WLAN.",
            icon = Icons.Rounded.SettingsInputAntenna,
            primary = settings,
            secondary = listOf(retry),
            modifier = panel,
        )
        state.connection == Connection.OFFLINE -> StatePanel(
            title = "Fernseher ist aus",
            body = if (setup.canWake) "Oder der Hub unter ${setup.hubAddress} ist gerade nicht erreichbar."
            else "Zum Einschalten fehlt die MAC-Adresse. Sie wird übernommen, sobald der Fernseher einmal erreichbar war.",
            icon = Icons.Rounded.PowerSettingsNew,
            tint = if (setup.canWake) Accent else TextSecondary,
            primary = if (setup.canWake) wake else settings,
            secondary = if (setup.canWake) listOf(retry, settings) else listOf(retry),
            modifier = panel,
        )
        else -> StatePanel(
            title = "Fernseher im Standby",
            body = "Der Hub ist erreichbar, das Bild ist aus.",
            icon = Icons.Rounded.Tv,
            primary = wake,
            modifier = panel,
        )
    }
}
