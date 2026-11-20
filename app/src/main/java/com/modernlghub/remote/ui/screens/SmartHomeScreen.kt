package com.modernlghub.remote.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.modernlghub.remote.data.SmartDevice
import com.modernlghub.remote.ui.components.RoundButton
import com.modernlghub.remote.ui.components.ScreenHeader
import com.modernlghub.remote.ui.components.SectionLabel
import com.modernlghub.remote.ui.components.Skeleton
import com.modernlghub.remote.ui.components.StateAction
import com.modernlghub.remote.ui.components.StatePanel
import com.modernlghub.remote.ui.components.pressable
import com.modernlghub.remote.ui.components.rememberHaptics
import com.modernlghub.remote.ui.theme.Hairline
import com.modernlghub.remote.ui.theme.Motion
import com.modernlghub.remote.ui.theme.Shape
import com.modernlghub.remote.ui.theme.Spacing
import com.modernlghub.remote.ui.theme.StatusOff
import com.modernlghub.remote.ui.theme.StatusWarn
import com.modernlghub.remote.ui.theme.Surface1
import com.modernlghub.remote.ui.theme.Surface2
import com.modernlghub.remote.ui.theme.TextPrimary
import com.modernlghub.remote.ui.theme.TextSecondary
import com.modernlghub.remote.ui.theme.TextTertiary
import com.modernlghub.remote.ui.theme.motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** How often slider moves are sent to a lamp while dragging (WiZ handles ~4-5 updates/s fine). */
private const val LIGHT_UPDATE_INTERVAL_MS = 220L

/**
 * State of the Zuhause tab. Lives above the tab switcher, so the device list, pending toggles and
 * the last lamp update survive switching tabs (they used to be cancelled mid-flight).
 */
@Stable
class SmartHomeController(
    private val scope: CoroutineScope,
    private val load: suspend () -> List<SmartDevice>?,
    private val toggleRemote: suspend (String) -> Boolean?,
    private val setLight: suspend (String, LightChange) -> Boolean,
) {
    var devices by mutableStateOf<List<SmartDevice>?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    val busy = mutableStateMapOf<String, Boolean>()

    // Latest wanted lamp state; a slow sender only ever sees the newest value.
    private val outgoing = MutableStateFlow<Pair<String, LightChange>?>(null)
    private var reloadJob: Job? = null

    init {
        scope.launch {
            outgoing.filterNotNull().collect { (id, change) ->
                setLight(id, change)
                delay(LIGHT_UPDATE_INTERVAL_MS)
            }
        }
    }

    fun reload(delayMs: Long = 0) {
        reloadJob?.cancel()
        reloadJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            loading = true
            val result = load()
            failed = result == null
            if (result != null) devices = result
            loading = false
        }
    }

    fun toggle(device: SmartDevice) {
        val before = device.isOn
        update(device.id) { it.copy(isOn = !before) }
        busy[device.id] = true
        scope.launch {
            val now = toggleRemote(device.id)
            update(device.id) { it.copy(isOn = now ?: before) }
            busy.remove(device.id)
        }
    }

    fun change(id: String, change: LightChange) {
        update(id) {
            it.copy(
                isOn = change.on,
                brightness = change.brightness ?: it.brightness,
                color = change.color ?: change.kelvin?.let { k -> SmartDevice.toHexColor(SmartDevice.kelvinToRgb(k)) } ?: it.color,
                kelvin = if (change.color != null) null else change.kelvin ?: it.kelvin,
            )
        }
        outgoing.value = id to change
    }

    /** Read back what the lamp really shows once the last update went out. */
    fun sheetClosed() = reload(delayMs = 1200)

    private fun update(id: String, transform: (SmartDevice) -> SmartDevice) {
        devices = devices?.map { if (it.id == id) transform(it) else it }
    }
}

/**
 * WiZ lamps and Tapo plugs, controlled locally by the hub, grouped by room.
 * Tap a tile to switch it; long-press (or the tune button) on a lamp for brightness and colour.
 */
@Composable
fun SmartHomeScreen(
    devices: List<SmartDevice>?,
    loading: Boolean,
    failed: Boolean,
    busy: Map<String, Boolean>,
    sheetFor: String?,
    onReload: () -> Unit,
    onToggle: (SmartDevice) -> Unit,
    onTune: (String) -> Unit,
    onLightChange: (String, LightChange) -> Unit,
    onSheetDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val list = devices.orEmpty()
    val rooms = remember(list) { list.groupBy { it.room } }
    val showRooms = rooms.keys.any { it.isNotBlank() }
    val onCount = list.count { it.isOn && it.online }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(156.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.Gutter, end = Spacing.Gutter, top = Spacing.s, bottom = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            ScreenHeader(
                "Zuhause",
                subtitle = when {
                    devices == null -> "Lichter und Steckdosen"
                    list.isEmpty() -> "Lichter und Steckdosen"
                    onCount == 0 -> "Alles aus"
                    onCount == 1 -> "1 Gerät an"
                    else -> "$onCount Geräte an"
                },
                modifier = Modifier.padding(bottom = Spacing.xs),
            ) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (loading) {
                        CircularProgressIndicator(color = TextSecondary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    } else {
                        RoundButton(Icons.Rounded.Refresh, "Aktualisieren", size = 44.dp, iconSize = 22.dp, container = Surface1, tint = TextSecondary, onClick = onReload)
                    }
                }
            }
        }
        when {
            devices == null && !failed -> items(4, contentType = { "skeleton" }) {
                Skeleton(Modifier.fillMaxWidth().aspectRatio(1.25f), Shape.Card)
            }
            list.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                if (failed) {
                    StatePanel(
                        title = "Hub nicht erreichbar",
                        body = "Lichter und Steckdosen werden über den Hub am Fernseher geschaltet.",
                        icon = Icons.Rounded.CloudOff,
                        tint = StatusOff,
                        primary = StateAction("Erneut versuchen", Icons.Rounded.Refresh, onReload),
                    )
                } else {
                    StatePanel(
                        title = "Keine Geräte",
                        body = "Auf dem Hub sind keine Lichter oder Steckdosen eingetragen (devices.json).",
                        icon = Icons.Rounded.Lightbulb,
                        tint = TextSecondary,
                    )
                }
            }
            else -> rooms.forEach { (room, inRoom) ->
                if (showRooms) {
                    item(key = "room:$room", span = { GridItemSpan(maxLineSpan) }) {
                        SectionLabel(room.ifBlank { "Weitere" }, Modifier.padding(top = Spacing.s))
                    }
                }
                items(inRoom, key = { it.id }) { device ->
                    DeviceTile(
                        device,
                        busy = busy[device.id] == true,
                        onClick = { haptics.confirm(); onToggle(device) },
                        onTune = if (device.adjustable) {
                            { haptics.tick(); onTune(device.id) }
                        } else null,
                    )
                }
            }
        }
    }

    val sheetDevice = sheetFor?.let { id -> devices?.find { it.id == id } }
    if (sheetDevice != null) {
        LightSheet(
            device = sheetDevice,
            onChange = { onLightChange(sheetDevice.id, it) },
            onDismiss = onSheetDismiss,
        )
    }
}

/** Colour a device glows in when on: the lamp's light colour, amber for plugs and lamps without colour. */
internal fun deviceTone(device: SmartDevice): Color =
    if (!device.isPlug) device.rgb?.let(::rgbColor) ?: StatusWarn else StatusWarn

@Composable
private fun DeviceTile(device: SmartDevice, busy: Boolean, onClick: () -> Unit, onTune: (() -> Unit)?) {
    val on = device.isOn && device.online
    val tone = deviceTone(device)
    val bg by animateColorAsState(if (on) tone.copy(alpha = 0.13f) else Surface1, motion(Motion.Medium), label = "devBg")
    val line by animateColorAsState(if (on) tone.copy(alpha = 0.38f) else Hairline, motion(Motion.Medium), label = "devLine")
    val source = remember { MutableInteractionSource() }
    val status = when {
        !device.online -> "Nicht erreichbar"
        on && device.brightness != null && device.canDim -> "An · ${device.brightness} %"
        on -> "An"
        else -> "Aus"
    }
    val level = if (on && device.canDim) (device.brightness ?: 100) / 100f else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.25f)
            .semantics(mergeDescendants = true) {
                contentDescription = device.name
                stateDescription = status
            }
            .pressable(
                enabled = device.online && !busy,
                role = Role.Switch,
                pressedScale = 0.96f,
                interactionSource = source,
                onClickLabel = if (on) "Ausschalten" else "Einschalten",
                onLongClickLabel = if (onTune != null) "Helligkeit und Farbe" else null,
                onLongClick = onTune,
                onClick = onClick,
            )
            .clip(Shape.Card)
            .background(bg)
            .border(1.dp, line, Shape.Card)
            .drawBehind {
                // Brightness as a slim bar along the bottom edge.
                if (level > 0f) {
                    val h = 3.dp.toPx()
                    val inset = 16.dp.toPx()
                    drawRoundRect(tone.copy(alpha = 0.18f), Offset(inset, size.height - inset), Size(size.width - 2 * inset, h), CornerRadius(h))
                    drawRoundRect(tone, Offset(inset, size.height - inset), Size((size.width - 2 * inset) * level, h), CornerRadius(h))
                }
            }
            .padding(Spacing.l),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (on) tone else Surface2),
                contentAlignment = Alignment.Center,
            ) {
                val iconTint = when {
                    !device.online -> TextTertiary
                    on -> if (tone.luminance() > 0.35f) Color.Black else Color.White
                    else -> TextSecondary
                }
                if (busy) {
                    CircularProgressIndicator(color = iconTint, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Icon(
                        when {
                            !device.online -> Icons.Rounded.CloudOff
                            device.isPlug -> Icons.Rounded.Power
                            else -> Icons.Rounded.Lightbulb
                        },
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (onTune != null) {
                // 36 dp disc, 48 dp touch target (RoundButton pads it); pulled into the tile corner.
                RoundButton(
                    Icons.Rounded.Tune,
                    "Helligkeit und Farbe: ${device.name}",
                    size = 36.dp,
                    iconSize = 18.dp,
                    container = Surface2,
                    tint = TextSecondary,
                    modifier = Modifier.offset(x = 6.dp, y = (-4).dp),
                    onClick = onTune,
                )
            }
        }
        Column(Modifier.padding(bottom = if (level > 0f) Spacing.s else 0.dp)) {
            Text(device.name, style = MaterialTheme.typography.titleSmall, color = if (device.online) TextPrimary else TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    !device.online -> TextTertiary
                    on -> tone.takeIf { it.luminance() > 0.12f } ?: StatusWarn
                    else -> TextTertiary
                },
                maxLines = 1,
            )
        }
    }
}
