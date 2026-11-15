package com.modernlghub.remote.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.modernlghub.remote.data.AssistantReply
import com.modernlghub.remote.data.AssistantTurn
import com.modernlghub.remote.ui.components.RoundButton
import com.modernlghub.remote.ui.components.card
import com.modernlghub.remote.ui.components.pressable
import com.modernlghub.remote.ui.components.rememberHaptics
import com.modernlghub.remote.ui.theme.Accent
import com.modernlghub.remote.ui.theme.AccentDim
import com.modernlghub.remote.ui.theme.Hairline
import com.modernlghub.remote.ui.theme.StatusOff
import com.modernlghub.remote.ui.theme.Surface1
import com.modernlghub.remote.ui.theme.Surface2
import com.modernlghub.remote.ui.theme.TextPrimary
import com.modernlghub.remote.ui.theme.TextSecondary
import com.modernlghub.remote.ui.theme.TextTertiary
import kotlinx.coroutines.launch

data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    val actions: List<String> = emptyList(),
    val isError: Boolean = false,
    /** Follow-up questions proposed by Gemini for this answer. */
    val suggestions: List<String> = emptyList(),
)

private val SUGGESTIONS = listOf(
    "Mach den Bildschirm aus, Musik soll weiterlaufen",
    "PC an und Game Optimizer",
    "Filmabend: TV-Licht an, Netflix starten",
    "Alles aus",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(
    messages: SnapshotStateList<ChatMessage>,
    hasKey: Boolean,
    online: Boolean,
    ask: suspend (prompt: String, history: List<AssistantTurn>) -> AssistantReply,
    /** Returns null on success, otherwise an error message. */
    saveKey: suspend (String) -> String?,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun send(prompt: String) {
        val p = prompt.trim()
        if (p.isEmpty() || thinking) return
        haptics.click()
        // Earlier turns give Gemini context for follow-ups ("und jetzt etwas leiser").
        val history = messages.filterNot { it.isError }.map { AssistantTurn(it.fromUser, it.text) }
        messages += ChatMessage(true, p)
        input = ""
        thinking = true
        scope.launch {
            val reply = ask(p, history)
            messages += ChatMessage(false, reply.text, reply.actions, reply.isError, reply.suggestions)
            thinking = false
        }
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(::send)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier.fillMaxSize().imePadding()) {
        Row(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Assistent", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                Text("Gemini steuert TV, Audio und Licht", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            if (messages.isNotEmpty() && !thinking) {
                RoundButton(
                    Icons.AutoMirrored.Rounded.Chat, "Neues Gespräch",
                    size = 44.dp, iconSize = 20.dp, container = Surface1, tint = TextSecondary,
                ) { messages.clear() }
            }
        }
        if (thinking) {
            LinearProgressIndicator(color = Accent, trackColor = Color.Transparent, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(2.dp))
        } else {
            Spacer(Modifier.height(14.dp))
        }

        Box(Modifier.weight(1f)) {
            when {
                online && !hasKey -> KeyCard(saveKey)
                messages.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Accent, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Was soll passieren?", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SUGGESTIONS.forEach { s -> SuggestionChip(s) { send(s) } }
                    }
                }
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(messages) { index, msg ->
                        Bubble(msg)
                        // Follow-up chips only under the latest answer.
                        if (index == messages.lastIndex && !msg.fromUser && msg.suggestions.isNotEmpty() && !thinking) {
                            Spacer(Modifier.height(10.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                msg.suggestions.forEach { s -> SuggestionChip(s) { send(s) } }
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Befehl eingeben") },
                shape = RoundedCornerShape(28.dp),
                maxLines = 4,
                enabled = online,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(input) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Hairline,
                    unfocusedContainerColor = Surface1,
                    focusedContainerColor = Surface1,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(10.dp))
            if (input.isNotBlank()) {
                RoundButton(Icons.AutoMirrored.Rounded.Send, "Senden", container = Accent, tint = Color.Black, enabled = !thinking) { send(input) }
            } else {
                RoundButton(Icons.Rounded.Mic, "Sprechen", container = AccentDim, tint = Accent, enabled = online && !thinking) {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Was soll der Fernseher tun?")
                    runCatching { speech.launch(intent) }
                }
            }
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = TextPrimary,
        modifier = Modifier
            .card(RoundedCornerShape(18.dp))
            .pressable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun Bubble(msg: ChatMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp, topEnd = 20.dp,
                        bottomStart = if (msg.fromUser) 20.dp else 6.dp,
                        bottomEnd = if (msg.fromUser) 6.dp else 20.dp,
                    )
                )
                .background(if (msg.fromUser) AccentDim else Surface2)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                msg.text,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    msg.isError -> StatusOff
                    msg.fromUser -> Accent
                    else -> TextPrimary
                },
            )
            if (msg.actions.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    msg.actions.distinct().joinToString(" · ") { "✓ " + it.replace('_', ' ') },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun KeyCard(saveKey: suspend (String) -> String?) {
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(20.dp).card().padding(20.dp)) {
        Icon(Icons.Rounded.Key, contentDescription = null, tint = Accent)
        Spacer(Modifier.height(12.dp))
        Text("Gemini-API-Schlüssel fehlt", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text("Der Schlüssel wird nur auf dem Hub am Fernseher gespeichert.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            placeholder = { Text("AIza…") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Hairline),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { scope.launch { result = saveKey(key) ?: "Gespeichert" } },
            enabled = key.length > 10,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
        ) { Text("Auf dem Hub speichern") }
        result?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}
