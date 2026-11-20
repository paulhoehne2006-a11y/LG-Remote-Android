package com.modernlghub.remote.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.modernlghub.remote.data.Connection
import com.modernlghub.remote.data.LauncherApp
import com.modernlghub.remote.data.TvState
import com.modernlghub.remote.ui.components.AppIcon
import com.modernlghub.remote.ui.components.appIconShape
import com.modernlghub.remote.ui.components.ScreenHeader
import com.modernlghub.remote.ui.components.SectionLabel
import com.modernlghub.remote.ui.components.Skeleton
import com.modernlghub.remote.ui.components.StateAction
import com.modernlghub.remote.ui.components.StatePanel
import com.modernlghub.remote.ui.components.ToggleChip
import com.modernlghub.remote.ui.components.fieldColors
import com.modernlghub.remote.ui.components.pressable
import com.modernlghub.remote.ui.components.rememberHaptics
import com.modernlghub.remote.ui.components.tint
import com.modernlghub.remote.ui.theme.Shape
import com.modernlghub.remote.ui.theme.Spacing
import com.modernlghub.remote.ui.theme.TextPrimary
import com.modernlghub.remote.ui.theme.TextSecondary
import com.modernlghub.remote.ui.theme.TextTertiary

private val INPUTS = listOf(
    PC_INPUT_ID to "PC",
    "com.webos.app.hdmi2" to "HDMI 2",
    "com.webos.app.hdmi3" to "HDMI 3",
    "com.webos.app.hdmi4" to "HDMI 4",
)

/** Every app on the TV plus the HDMI inputs, searchable. Tapping launches and returns to the remote. */
@Composable
fun AppsScreen(
    state: TvState,
    iconUrl: (String) -> String,
    onLaunch: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    val haptics = rememberHaptics()
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    val apps = remember(state.apps, query) {
        state.apps
            .filter { it.visible && !it.isInput && it.type != "home" }
            .filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
            .sortedBy { it.title.lowercase() }
    }
    val launch: (String) -> Unit = {
        haptics.confirm()
        focus.clearFocus()
        onLaunch(it)
    }
    val usable = state.isOnline && !state.status.isStandby

    LazyVerticalGrid(
        columns = GridCells.Adaptive(76.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.Gutter, end = Spacing.Gutter, top = Spacing.s, bottom = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.l + 2.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header", contentType = "header") {
            Column {
                ScreenHeader("Apps", subtitle = currentLine(state))
                Spacer(Modifier.height(Spacing.l))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Apps durchsuchen") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Suche löschen", tint = TextSecondary) } }
                    } else null,
                    singleLine = true,
                    shape = Shape.Pill,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (query.isBlank()) {
                    Spacer(Modifier.height(Spacing.xl))
                    SectionLabel("Eingänge")
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        INPUTS.forEach { (id, label) ->
                            val active = state.status.currentAppId == id
                            ToggleChip(
                                label,
                                active = active,
                                icon = if (id == PC_INPUT_ID) Icons.Rounded.Computer else Icons.AutoMirrored.Rounded.Input,
                                stateLabel = if (active) "Aktiver Eingang" else null,
                                enabled = usable,
                                modifier = Modifier.widthIn(min = 84.dp),
                            ) { launch(id) }
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.xl))
                SectionLabel(if (query.isBlank()) "Alle Apps" else "Treffer")
            }
        }

        when {
            state.apps.isEmpty() && state.connection == Connection.CONNECTING -> {
                items(8, contentType = { "skeleton" }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Skeleton(Modifier.size(64.dp), Shape.Tile)
                        Spacer(Modifier.height(Spacing.s))
                        Skeleton(Modifier.size(width = 52.dp, height = 10.dp), Shape.Pill)
                    }
                }
            }
            state.apps.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                StatePanel(
                    title = "Noch keine Apps",
                    body = "Die Liste kommt vom Hub, sobald der Fernseher erreichbar ist.",
                    icon = Icons.Rounded.Apps,
                    tint = TextSecondary,
                    primary = StateAction("Erneut versuchen", Icons.Rounded.Refresh, onRetry),
                )
            }
            apps.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }, key = "nohits") {
                StatePanel(
                    title = "Nichts gefunden",
                    body = "Keine App heißt „${query.trim()}“.",
                    icon = Icons.Rounded.SearchOff,
                    tint = TextSecondary,
                )
            }
            else -> items(apps, key = { it.id }, contentType = { "app" }) { app ->
                AppTile(
                    app,
                    iconUrl(app.id),
                    running = app.id == state.status.currentAppId,
                    enabled = usable,
                ) { launch(app.id) }
            }
        }
    }
}

private fun currentLine(state: TvState): String? = when {
    !state.isOnline -> "Fernseher nicht erreichbar"
    state.status.isStandby -> "Fernseher im Standby"
    else -> state.appTitle().takeIf { it.isNotBlank() }?.let { "Läuft: $it" }
}

@Composable
private fun AppTile(app: LauncherApp, iconUrl: String, running: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val tint = app.tint()
    Column(
        Modifier
            .semantics(mergeDescendants = true) {
                contentDescription = app.title
                if (running) stateDescription = "Läuft"
            }
            .pressable(enabled = enabled, onClickLabel = "Öffnen", onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(
            app, iconUrl, size = 64.dp,
            modifier = if (running) Modifier.border(2.dp, tint, appIconShape(64.dp)) else Modifier,
        )
        Spacer(Modifier.height(Spacing.s))
        Text(
            app.title,
            style = MaterialTheme.typography.labelMedium,
            color = if (running) TextPrimary else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (running) {
            Text("Läuft", style = MaterialTheme.typography.labelSmall, color = TextTertiary, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
