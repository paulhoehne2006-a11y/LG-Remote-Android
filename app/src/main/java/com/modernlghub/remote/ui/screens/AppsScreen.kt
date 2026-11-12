package com.modernlghub.remote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.modernlghub.remote.data.LauncherApp
import com.modernlghub.remote.data.TvState
import com.modernlghub.remote.ui.components.AppIcon
import com.modernlghub.remote.ui.components.EmptyHint
import com.modernlghub.remote.ui.components.SectionLabel
import com.modernlghub.remote.ui.components.pressable
import com.modernlghub.remote.ui.components.rememberHaptics
import com.modernlghub.remote.ui.theme.Accent
import com.modernlghub.remote.ui.theme.AccentDim
import com.modernlghub.remote.ui.theme.Hairline
import com.modernlghub.remote.ui.theme.Surface1
import com.modernlghub.remote.ui.theme.TextPrimary
import com.modernlghub.remote.ui.theme.TextSecondary

private val INPUTS = listOf(
    "com.webos.app.hdmi1" to "PC",
    "com.webos.app.hdmi2" to "HDMI 2",
    "com.webos.app.hdmi3" to "HDMI 3",
    "com.webos.app.hdmi4" to "HDMI 4",
)

@Composable
fun AppsScreen(
    state: TvState,
    iconUrl: (String) -> String,
    onLaunch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    var query by rememberSaveable { mutableStateOf("") }
    val apps = remember(state.apps, query) {
        state.apps
            .filter { it.visible && !it.isInput && it.type != "home" }
            .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
            .sortedBy { it.title.lowercase() }
    }
    val launch: (String) -> Unit = {
        haptics.confirm()
        onLaunch(it)
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(76.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text("Apps", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Suchen") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Hairline,
                        unfocusedContainerColor = Surface1,
                        focusedContainerColor = Surface1,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (query.isBlank()) {
                    Spacer(Modifier.height(24.dp))
                    SectionLabel("Eingänge")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        INPUTS.forEach { (id, label) ->
                            InputChip(label, active = state.status.currentAppId == id, isPc = id.endsWith("hdmi1"), modifier = Modifier.weight(1f)) { launch(id) }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                SectionLabel(if (query.isBlank()) "Alle Apps" else "Treffer")
            }
        }

        if (apps.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyHint(
                    if (state.apps.isEmpty()) "Noch keine Apps" else "Nichts gefunden",
                    if (state.apps.isEmpty()) "Die Liste kommt vom Hub, sobald der Fernseher erreichbar ist." else "Versuch einen anderen Suchbegriff.",
                )
            }
        }

        items(apps, key = { it.id }) { app ->
            AppTile(app, iconUrl(app.id), running = app.id == state.status.currentAppId) { launch(app.id) }
        }
    }
}

@Composable
private fun InputChip(label: String, active: Boolean, isPc: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(if (active) AccentDim else Surface1)
            .border(1.dp, if (active) Accent.copy(alpha = 0.35f) else Hairline, CircleShape)
            .pressable(onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (isPc) Icons.Rounded.Computer else Icons.AutoMirrored.Rounded.Input,
            contentDescription = null,
            tint = if (active) Accent else TextSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) Accent else TextPrimary, maxLines = 1)
    }
}

@Composable
private fun AppTile(app: LauncherApp, iconUrl: String, running: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.pressable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(
            app, iconUrl, size = 64.dp,
            modifier = if (running) Modifier.border(2.dp, Accent, RoundedCornerShape(17.dp)) else Modifier,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            app.title,
            style = MaterialTheme.typography.labelMedium,
            color = if (running) Accent else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
