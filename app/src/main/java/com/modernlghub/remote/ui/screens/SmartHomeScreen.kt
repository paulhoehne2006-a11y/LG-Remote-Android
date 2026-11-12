package com.modernlghub.remote.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.modernlghub.remote.data.SmartDevice
import com.modernlghub.remote.ui.components.EmptyHint
import com.modernlghub.remote.ui.components.RoundButton
import com.modernlghub.remote.ui.components.pressable
import com.modernlghub.remote.ui.components.rememberHaptics
import com.modernlghub.remote.ui.theme.Hairline
import com.modernlghub.remote.ui.theme.StatusWarn
import com.modernlghub.remote.ui.theme.Surface1
import com.modernlghub.remote.ui.theme.Surface2
import com.modernlghub.remote.ui.theme.TextPrimary
import com.modernlghub.remote.ui.theme.TextSecondary
import com.modernlghub.remote.ui.theme.TextTertiary
import kotlinx.coroutines.launch

/** SmartThings lights and plugs, proxied by the hub (/api/smartthings/devices and /toggle). */
@Composable
fun SmartHomeScreen(
    load: suspend () -> List<SmartDevice>?,
    toggle: suspend (String) -> Boolean?,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<SmartDevice>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    val busy = remember { mutableStateMapOf<String, Boolean>() }

    suspend fun reload() {
        loading = true
        val result = load()
        failed = result == null
        if (result != null) devices = result
        loading = false
    }
    LaunchedEffect(Unit) { reload() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Zuhause", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                    Text("SmartThings über den Hub", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
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
                    if (failed) "SmartThings nicht erreichbar" else "Keine Geräte",
                    if (failed) "Der Hub hat nicht geantwortet oder kein SmartThings-Token." else "Der Hub hat keine bekannten Lichter oder Steckdosen gefunden.",
                )
            }
        }
        items(list, key = { it.id }) { device ->
            DeviceTile(device, busy = busy[device.id] == true) {
                haptics.confirm()
                val before = device.isOn
                devices = devices?.map { if (it.id == device.id) it.copy(isOn = !before) else it }
                busy[device.id] = true
                scope.launch {
                    val now = toggle(device.id)
                    devices = devices?.map { if (it.id == device.id) it.copy(isOn = now ?: before) else it }
                    busy.remove(device.id)
                }
            }
        }
    }
}

@Composable
private fun DeviceTile(device: SmartDevice, busy: Boolean, onClick: () -> Unit) {
    val on = device.isOn
    val bg by animateColorAsState(if (on) StatusWarn.copy(alpha = 0.14f) else Surface1, tween(250), label = "devBg")
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.25f)
            .clip(shape)
            .background(bg)
            .border(1.dp, if (on) StatusWarn.copy(alpha = 0.35f) else Hairline, shape)
            .pressable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (on) StatusWarn else Surface2),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(color = if (on) Color.Black else TextPrimary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                Icon(
                    if (device.type == "plug") Icons.Rounded.Power else Icons.Rounded.Lightbulb,
                    contentDescription = null,
                    tint = if (on) Color.Black else TextSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column {
            Text(device.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(if (on) "An" else "Aus", style = MaterialTheme.typography.bodySmall, color = if (on) StatusWarn else TextTertiary)
        }
    }
}
