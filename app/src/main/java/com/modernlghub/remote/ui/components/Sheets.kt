package com.modernlghub.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.modernlghub.remote.data.AudioStatus
import com.modernlghub.remote.data.PictureMode
import com.modernlghub.remote.data.SoundOutput
import com.modernlghub.remote.ui.theme.Accent
import com.modernlghub.remote.ui.theme.AccentDim
import com.modernlghub.remote.ui.theme.Hairline
import com.modernlghub.remote.ui.theme.Surface1
import com.modernlghub.remote.ui.theme.Surface2
import com.modernlghub.remote.ui.theme.TextPrimary
import com.modernlghub.remote.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface1,
        contentColor = TextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.6f),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            content = content,
        )
    }
}

@Composable
private fun SheetTitle(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
    if (subtitle != null) {
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
    Spacer(Modifier.height(20.dp))
}

/** Phone keyboard → TV text field. Text is mirrored live (debounced) into the focused field. */
@Composable
fun KeyboardSheet(onTextChange: (String) -> Unit, onEnter: () -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    var touched by remember { mutableStateOf(false) }
    LaunchedEffect(text) {
        if (!touched) return@LaunchedEffect
        delay(220)
        onTextChange(text)
    }
    RemoteSheet(onDismiss) {
        SheetTitle("Texteingabe", "Erscheint direkt im aktiven Suchfeld auf dem TV")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.imePadding()) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    touched = true
                    text = it
                },
                placeholder = { Text("Suchen …") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onEnter() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Hairline,
                    cursorColor = Accent,
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus),
            )
            Spacer(Modifier.width(12.dp))
            RoundButton(
                Icons.AutoMirrored.Rounded.KeyboardReturn, "Enter",
                container = Accent, tint = Color.Black, onClick = onEnter,
            )
        }
    }
}

/** Number pad, media transport, channel and colour keys — everything a remote has but rarely needs. */
@Composable
fun MoreKeysSheet(onKey: (String) -> Unit, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    val press: (String) -> Unit = {
        haptics.click()
        onKey(it)
    }
    RemoteSheet(onDismiss) {
        SheetTitle("Weitere Tasten")
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { n -> KeyTile(n, Modifier.weight(1f)) { press(n) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyTile("Info", Modifier.weight(1f)) { press("INFO") }
                KeyTile("0", Modifier.weight(1f)) { press("0") }
                KeyTile("Exit", Modifier.weight(1f)) { press("EXIT") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyTile("CH −", Modifier.weight(1f)) { press("CHANNELDOWN") }
                KeyTile("Einstellungen", Modifier.weight(1f)) { press("MENU") }
                KeyTile("CH +", Modifier.weight(1f)) { press("CHANNELUP") }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RoundButton(Icons.Rounded.FastRewind, "Zurückspulen") { press("REWIND") }
            RoundButton(Icons.Rounded.PlayArrow, "Wiedergabe") { press("PLAY") }
            RoundButton(Icons.Rounded.Pause, "Pause") { press("PAUSE") }
            RoundButton(Icons.Rounded.Stop, "Stopp") { press("STOP") }
            RoundButton(Icons.Rounded.FastForward, "Vorspulen") { press("FASTFORWARD") }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                "RED" to Color(0xFFE5484D),
                "GREEN" to Color(0xFF46A758),
                "YELLOW" to Color(0xFFF5D90A),
                "BLUE" to Color(0xFF3E63DD),
            ).forEach { (key, color) ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.9f))
                        .pressable { press(key) },
                )
            }
        }
    }
}

@Composable
private fun KeyTile(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface2)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = if (label.length == 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun PictureModeSheet(onSelect: (PictureMode) -> Unit, onDismiss: () -> Unit) {
    RemoteSheet(onDismiss) {
        SheetTitle("Bildmodus")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PictureMode.entries.forEach { mode ->
                OptionRow(title = mode.label, subtitle = mode.hint, selected = false) {
                    onSelect(mode)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
fun AudioOutputSheet(current: AudioStatus, onSelect: (SoundOutput) -> Unit, onDismiss: () -> Unit) {
    RemoteSheet(onDismiss) {
        SheetTitle("Tonausgabe")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SoundOutput.entries.forEach { out ->
                OptionRow(title = out.label, subtitle = null, selected = current.soundOutput == out.key) {
                    onSelect(out)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
fun OptionRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) AccentDim else Surface2)
            .border(1.dp, if (selected) Accent.copy(alpha = 0.4f) else Color.Transparent, shape)
            .pressable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (selected) Accent else TextPrimary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        if (selected) Icon(Icons.Rounded.Check, contentDescription = "Aktiv", tint = Accent, modifier = Modifier.size(20.dp))
    }
}
