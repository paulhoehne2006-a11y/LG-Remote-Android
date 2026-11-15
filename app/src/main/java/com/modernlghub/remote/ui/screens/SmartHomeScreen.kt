package com.modernlghub.remote.ui.screens

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.modernlghub.remote.data.SmartDevice
import com.modernlghub.remote.ui.components.EmptyHint
import com.modernlghub.remote.ui.components.RoundButton
import com.modernlghub.remote.ui.components.SectionLabel
import com.modernlghub.remote.ui.components.rememberHaptics
import com.modernlghub.remote.ui.theme.Hairline
import com.modernlghub.remote.ui.theme.StatusWarn
import com.modernlghub.remote.ui.theme.Surface1
import com.modernlghub.remote.ui.theme.Surface2
import com.modernlghub.remote.ui.theme.TextPrimary
import com.modernlghub.remote.ui.theme.TextSecondary
import com.modernlghub.remote.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** How often slider moves are sent to a lamp while dragging (WiZ handles ~4-5 updates/s fine). */
private const val LIGHT_UPDATE_INTERVAL_MS = 220L

/**
 * WiZ lamps and Tapo plugs, controlled locally by the hub (/api/smarthome/devices, toggle, set), grouped by room.
 * Tap a tile to switch it; long-press (or the tune button) on a lamp for brightness and colour.
 */
@Composable
fun SmartHomeScreen(
    load: suspend () -> List<SmartDevice>?,
    toggle: suspend (String) -> Boolean?,
    setLight: suspend (String, LightChange) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<SmartDevice>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    val busy = remember { mutableStateMapOf<String, Boolean>() }
    var sheetFor by rememberSaveable { mutableStateOf<String?>(null) }
    // Latest wanted lamp state; a slow sender only ever sees the newest value (StateFlow conflates).
    val outgoing = remember { MutableStateFlow<Pair<String, LightChange>?>(null) }

    suspend fun reload() {
        loading = true
        val result = load()
        failed = result == null
        if (result != null) devices = result
        loading = false
    }
    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(Unit) {
        outgoing.filterNotNull().collect { (id, change) ->
            setLight(id, change)
            delay(LIGHT_UPDATE_INTERVAL_MS)
        }
    }

    fun update(id: String, transform: (SmartDevice) -> SmartDevice) {
        devices = devices?.map { if (it.id == id) transform(it) else it }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Zuhause", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                    Text("Lichter & Steckdosen · lokal über den Hub", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                if (loading) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                } else {
                    RoundButton(Icons.Rounded.Refresh, "Aktualisieren", size = 44.dp, iconSize = 22.dp, container = Surface1, tint = TextSecondary) {
                        scope.launch { reload() }
                    }
                }
            }
        }
        val list = devices.orEmpty()
        if (!loading && list.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyHint(
                    if (failed) "Smart Home nicht erreichbar" else "Keine Geräte",
                    if (failed) "Der Hub hat nicht geantwortet." else "Auf dem Hub sind keine Lichter oder Steckdosen eingetragen (devices.json).",
                )
            }
        }
        val rooms = list.groupBy { it.room }
        val showRooms = rooms.keys.any { it.isNotBlank() }
        rooms.forEach { (room, inRoom) ->
            if (showRooms) {
                item(key = "room:$room", span = { GridItemSpan(maxLineSpan) }) {
                    SectionLabel(room.ifBlank { "Weitere" }, Modifier.padding(top = 10.dp))
                }
            }
            items(inRoom, key = { it.id }) { device ->
                DeviceTile(
                    device,
                    busy = busy[device.id] == true,
                    onClick = {
                        haptics.confirm()
                        val before = device.isOn
                        update(device.id) { it.copy(isOn = !before) }
                        busy[device.id] = true
                        scope.launch {
                            val now = toggle(device.id)
                            update(device.id) { it.copy(isOn = now ?: before) }
                            busy.remove(device.id)
                        }
                    },
                    onTune = if (device.adjustable) {
                        { haptics.tick(); sheetFor = device.id }
                    } else null,
                )
            }
        }
    }

    val sheetDevice = sheetFor?.let { id -> devices?.find { it.id == id } }
    if (sheetDevice != null) {
        LightSheet(
            device = sheetDevice,
            onChange = { change ->
                update(sheetDevice.id) {
                    it.copy(
                        isOn = change.on,
                        brightness = change.brightness ?: it.brightness,
                        color = change.color ?: change.kelvin?.let { k -> SmartDevice.toHexColor(SmartDevice.kelvinToRgb(k)) } ?: it.color,
                        kelvin = if (change.color != null) null else change.kelvin ?: it.kelvin,
                    )
                }
                outgoing.value = sheetDevice.id to change
            },
            onDismiss = {
                sheetFor = null
                // Read back what the lamp really shows once the last update went out.
                scope.launch { delay(1200); reload() }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceTile(device: SmartDevice, busy: Boolean, onClick: () -> Unit, onTune: (() -> Unit)?) {
    val on = device.isOn && device.online
    val tone = if (!device.isPlug) device.rgb?.let(::rgbColor) ?: StatusWarn else StatusWarn
    val bg by animateColorAsState(if (on) tone.copy(alpha = 0.14f) else Surface1, tween(250), label = "devBg")
    val border by animateColorAsState(if (on) tone.copy(alpha = 0.4f) else Hairline, tween(250), label = "devBorder")
    val shape = RoundedCornerShape(24.dp)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.25f)
            .scale(if (pressed) 0.96f else 1f)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .alpha(if (device.online) 1f else 0.45f)
            .combinedClickable(
                interactionSource = source,
                indication = null,
                enabled = device.online && !busy,
                role = Role.Switch,
                onLongClickLabel = if (onTune != null) "Helligkeit und Farbe" else null,
                onLongClick = onTune,
                onClick = onClick,
            )
            .padding(16.dp),
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
                val iconTint = if (on) (if (tone.luminance() > 0.35f) Color.Black else Color.White) else TextSecondary
                if (busy) {
                    CircularProgressIndicator(color = iconTint, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Icon(
                        if (device.isPlug) Icons.Rounded.Power else Icons.Rounded.Lightbulb,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (onTune != null) {
                RoundButton(
                    Icons.Rounded.Tune,
                    "Helligkeit und Farbe: ${device.name}",
                    size = 36.dp,
                    iconSize = 18.dp,
                    container = Surface2.copy(alpha = 0.8f),
                    tint = TextSecondary,
                    onClick = onTune,
                )
            }
        }
        Column {
            Text(device.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                when {
                    !device.online -> "Offline"
                    on && device.brightness != null && device.canDim -> "An · ${device.brightness} %"
                    on -> "An"
                    else -> "Aus"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (on) tone.takeIf { it.luminance() > 0.12f } ?: StatusWarn else TextTertiary,
            )
        }
    }
}
