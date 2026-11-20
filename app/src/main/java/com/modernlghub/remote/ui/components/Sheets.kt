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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.modernlghub.remote.data.AudioStatus
import com.modernlghub.remote.data.PictureMode
import com.modernlghub.remote.data.SoundOutput
import com.modernlghub.remote.ui.theme.Accent
import com.modernlghub.remote.ui.theme.AccentDim
import com.modernlghub.remote.ui.theme.AccentLine
import com.modernlghub.remote.ui.theme.Hairline
import com.modernlghub.remote.ui.theme.OnAccent
import com.modernlghub.remote.ui.theme.Scrim
import com.modernlghub.remote.ui.theme.Shape
import com.modernlghub.remote.ui.theme.Spacing
import com.modernlghub.remote.ui.theme.Surface1
import com.modernlghub.remote.ui.theme.Surface2
import com.modernlghub.remote.ui.theme.Surface3
import com.modernlghub.remote.ui.theme.TextPrimary
import com.modernlghub.remote.ui.theme.TextSecondary
import com.modernlghub.remote.ui.theme.TextTertiary
import kotlinx.coroutines.delay

/** How long typing must pause before the text is mirrored to the TV. */
internal const val TEXT_SYNC_DEBOUNCE_MS = 220L

/**
 * The app's bottom sheet: one tone above the background, a slim handle, gutter padding.
 * The content scrolls, so nothing is cut off in landscape or at large font sizes; the sheet
 * itself already pads for the navigation bar and the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = Shape.Sheet,
        containerColor = Surface1,
        contentColor = TextPrimary,
        scrimColor = Scrim,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 14.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Surface3)
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.Gutter)
                .padding(bottom = Spacing.xl),
            content = content,
        )
    }
}

@Composable
fun SheetTitle(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary, modifier = Modifier.semantics { heading() })
    if (subtitle != null) {
        Spacer(Modifier.height(Spacing.xs))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
    Spacer(Modifier.height(Spacing.l + Spacing.xs))
}

/** Text field colours. [filled] fields (search, chat) sit on a tone; labelled form fields stay outlined only. */
@Composable
fun fieldColors(filled: Boolean = true): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = Hairline,
    disabledBorderColor = Hairline,
    focusedContainerColor = if (filled) Surface2 else Color.Transparent,
    unfocusedContainerColor = if (filled) Surface2 else Color.Transparent,
    disabledContainerColor = if (filled) Surface1 else Color.Transparent,
    cursorColor = Accent,
    focusedLabelColor = Accent,
    unfocusedLabelColor = TextSecondary,
    focusedPlaceholderColor = TextTertiary,
    unfocusedPlaceholderColor = TextTertiary,
    disabledPlaceholderColor = TextTertiary,
    focusedLeadingIconColor = TextSecondary,
    unfocusedLeadingIconColor = TextSecondary,
)

/**
 * Phone keyboard → TV text field. The text is mirrored (debounced) into the focused field on the TV;
 * Enter and closing the sheet first send whatever is still pending, so the TV never searches for
 * a stale word or drops the last letters.
 */
@Composable
fun KeyboardSheet(onTextChange: (String) -> Unit, onEnter: () -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    // What the TV field holds now (the hub replaces the whole field with each update).
    var sent by rememberSaveable { mutableStateOf("") }
    val send by rememberUpdatedState(onTextChange)
    val focus = remember { FocusRequester() }

    fun flush() {
        if (text != sent) {
            sent = text
            send(text)
        }
    }
    fun enter() {
        flush()
        onEnter()
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(text) {
        if (text == sent) return@LaunchedEffect
        delay(TEXT_SYNC_DEBOUNCE_MS)
        flush()
    }
    DisposableEffect(Unit) { onDispose { flush() } }

    RemoteSheet(onDismiss) {
        SheetTitle("Texteingabe", "Erscheint direkt im aktiven Textfeld auf dem Fernseher.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Suchen …") },
                singleLine = true,
                shape = Shape.Control,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { enter() }),
                trailingIcon = if (text.isNotEmpty()) {
                    {
                        IconButton(onClick = { text = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Text löschen", tint = TextSecondary)
                        }
                    }
                } else null,
                colors = fieldColors(),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus),
            )
            Spacer(Modifier.width(Spacing.m))
            RoundButton(
                Icons.AutoMirrored.Rounded.KeyboardReturn, "Eingabe senden",
                container = Accent, tint = OnAccent, onClick = ::enter,
            )
        }
    }
}

private val COLOR_KEYS = listOf(
    Triple("RED", "Rot", Color(0xFFE5484D)),
    Triple("GREEN", "Grün", Color(0xFF46A758)),
    Triple("YELLOW", "Gelb", Color(0xFFF5D90A)),
    Triple("BLUE", "Blau", Color(0xFF3E63DD)),
)

/** Number pad, media transport, channel and colour keys: everything a remote has but rarely needs. */
@Composable
fun MoreKeysSheet(onKey: (String) -> Unit, onDismiss: () -> Unit) {
    RemoteSheet(onDismiss) { MoreKeysContent(onKey) }
}

@Composable
fun MoreKeysContent(onKey: (String) -> Unit) {
    val haptics = rememberHaptics()
    val press: (String) -> Unit = {
        haptics.click()
        onKey(it)
    }
    SheetTitle("Weitere Tasten")
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s + 2.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s + 2.dp)) {
                row.forEach { n -> KeyTile(n, Modifier.weight(1f)) { press(n) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s + 2.dp)) {
            KeyTile("Info", Modifier.weight(1f)) { press("INFO") }
            KeyTile("0", Modifier.weight(1f)) { press("0") }
            KeyTile("Exit", Modifier.weight(1f)) { press("EXIT") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s + 2.dp)) {
            KeyTile("Kanal −", Modifier.weight(1f), description = "Kanal runter") { press("CHANNELDOWN") }
            KeyTile("Menü", Modifier.weight(1f), description = "TV-Menü") { press("MENU") }
            KeyTile("Kanal +", Modifier.weight(1f), description = "Kanal hoch") { press("CHANNELUP") }
        }
    }
    Spacer(Modifier.height(Spacing.xl))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        RoundButton(Icons.Rounded.FastRewind, "Zurückspulen") { press("REWIND") }
        RoundButton(Icons.Rounded.PlayArrow, "Wiedergabe") { press("PLAY") }
        RoundButton(Icons.Rounded.Pause, "Pause") { press("PAUSE") }
        RoundButton(Icons.Rounded.Stop, "Stopp") { press("STOP") }
        RoundButton(Icons.Rounded.FastForward, "Vorspulen") { press("FASTFORWARD") }
    }
    Spacer(Modifier.height(Spacing.xl))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s + 2.dp)) {
        COLOR_KEYS.forEach { (key, name, color) ->
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .semantics { contentDescription = "Farbtaste $name" }
                    .pressable { press(key) }
                    .padding(vertical = 6.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.9f)),
            )
        }
    }
}

@Composable
private fun KeyTile(label: String, modifier: Modifier = Modifier, description: String? = null, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 56.dp)
            .semantics { if (description != null) contentDescription = description }
            .pressable(onClick = onClick)
            .clip(Shape.Control)
            .background(Surface2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = if (label.length == 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.m),
        )
    }
}

@Composable
fun PictureModeSheet(onSelect: (PictureMode) -> Unit, onDismiss: () -> Unit) {
    RemoteSheet(onDismiss) {
        SheetTitle("Bildmodus", "Der Fernseher meldet den aktiven Modus nicht zurück.")
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
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
        SheetTitle("Audioausgabe")
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
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
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { this.selected = selected }
            .pressable(role = Role.RadioButton, pressedScale = 0.98f, onClick = onClick)
            .clip(Shape.Control)
            .background(if (selected) AccentDim else Surface2)
            .border(1.dp, if (selected) AccentLine else Color.Transparent, Shape.Control)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (selected) Accent else TextPrimary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
    }
}
