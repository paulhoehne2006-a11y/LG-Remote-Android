package com.modernlghub.remote.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.modernlghub.remote.data.AssistantReply
import com.modernlghub.remote.data.AssistantTurn
import com.modernlghub.remote.ui.components.InlineNotice
import com.modernlghub.remote.ui.components.PrimaryButton
import com.modernlghub.remote.ui.components.RoundButton
import com.modernlghub.remote.ui.components.ScreenHeader
import com.modernlghub.remote.ui.components.SecondaryButton
import com.modernlghub.remote.ui.components.StatePanel
import com.modernlghub.remote.ui.components.card
import com.modernlghub.remote.ui.components.fieldColors
import com.modernlghub.remote.ui.components.pressable
import com.modernlghub.remote.ui.components.rememberHaptics
import com.modernlghub.remote.ui.theme.Accent
import com.modernlghub.remote.ui.theme.AccentDim
import com.modernlghub.remote.ui.theme.LocalReduceMotion
import com.modernlghub.remote.ui.theme.OnAccent
import com.modernlghub.remote.ui.theme.Shape
import com.modernlghub.remote.ui.theme.Spacing
import com.modernlghub.remote.ui.theme.StatusOff
import com.modernlghub.remote.ui.theme.StatusOn
import com.modernlghub.remote.ui.theme.Surface1
import com.modernlghub.remote.ui.theme.Surface2
import com.modernlghub.remote.ui.theme.TextPrimary
import com.modernlghub.remote.ui.theme.TextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    val actions: List<String> = emptyList(),
    val isError: Boolean = false,
    /** Follow-up questions proposed by Gemini for this answer. */
    val suggestions: List<String> = emptyList(),
)

/** Keeps the conversation across rotation and process death. */
val ChatSaver: Saver<SnapshotStateList<ChatMessage>, Any> = Saver(
    save = { list ->
        ArrayList(list.map { m -> arrayListOf(m.fromUser, m.text, ArrayList(m.actions), m.isError, ArrayList(m.suggestions)) })
    },
    restore = { saved ->
        @Suppress("UNCHECKED_CAST")
        val rows = saved as List<List<Any>>
        mutableStateListOf<ChatMessage>().apply {
            rows.forEach { r ->
                add(ChatMessage(r[0] as Boolean, r[1] as String, r[2] as List<String>, r[3] as Boolean, r[4] as List<String>))
            }
        }
    },
)

/**
 * The conversation with the hub's assistant. Lives above the tab switcher: a question asked just
 * before switching tabs still gets its answer (the request used to be cancelled with the tab).
 */
@Stable
class AssistantController(
    val messages: SnapshotStateList<ChatMessage>,
    private val scope: CoroutineScope,
    private val ask: suspend (prompt: String, history: List<AssistantTurn>) -> AssistantReply,
) {
    var thinking by mutableStateOf(false)
        private set

    fun send(prompt: String): Boolean {
        val p = prompt.trim()
        if (p.isEmpty() || thinking) return false
        // Earlier turns give Gemini context for follow-ups ("und jetzt etwas leiser").
        val history = messages.filterNot { it.isError }.map { AssistantTurn(it.fromUser, it.text) }
        messages += ChatMessage(true, p)
        thinking = true
        scope.launch {
            val reply = runCatching { ask(p, history) }
                .getOrElse { AssistantReply("Der Hub hat nicht geantwortet.", isError = true) }
            messages += ChatMessage(false, reply.text, reply.actions, reply.isError, reply.suggestions)
            thinking = false
        }
        return true
    }

    /** Sends the last question again after an error answer. */
    fun retry() {
        if (thinking) return
        val lastError = messages.lastOrNull()?.takeIf { it.isError && !it.fromUser } ?: return
        val question = messages.getOrNull(messages.lastIndex - 1)?.takeIf { it.fromUser } ?: return
        messages.remove(lastError)
        messages.remove(question)
        send(question.text)
    }

    fun clear() {
        if (!thinking) messages.clear()
    }
}

private val SUGGESTIONS = listOf(
    "Mach das Display aus, die Musik soll weiterlaufen",
    "PC an und Game Optimizer",
    "Filmabend: TV-Licht an, Netflix starten",
    "Alles aus",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(
    messages: List<ChatMessage>,
    thinking: Boolean,
    hasKey: Boolean,
    online: Boolean,
    onSend: (String) -> Boolean,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    /** Returns null on success, otherwise an error message. */
    saveKey: suspend (String) -> String?,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    var input by rememberSaveable { mutableStateOf("") }
    var voiceProblem by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    fun send(prompt: String) {
        if (onSend(prompt)) {
            haptics.click()
            input = ""
            voiceProblem = null
        }
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(::send)
        }
    }

    LaunchedEffect(messages.size, thinking) {
        val count = messages.size + if (thinking) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            "Gemini",
            subtitle = "Gemini steuert Fernseher, Ton und Licht",
            modifier = Modifier.padding(horizontal = Spacing.Gutter).padding(top = Spacing.s),
        ) {
            if (messages.isNotEmpty() && !thinking) {
                RoundButton(
                    Icons.Rounded.EditNote, "Neues Gespräch",
                    size = 44.dp, iconSize = 22.dp, container = Surface1, tint = TextSecondary,
                ) { haptics.click(); onClear() }
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                online && !hasKey -> KeyCard(saveKey, Modifier.verticalScroll(rememberScrollState()))
                !online && messages.isEmpty() -> StatePanel(
                    title = "Hub nicht erreichbar",
                    body = "Gemini läuft über den Hub am Fernseher. Sobald der erreichbar ist, geht es hier weiter.",
                    icon = Icons.Rounded.WifiOff,
                    tint = TextSecondary,
                    modifier = Modifier.fillMaxSize(),
                )
                messages.isEmpty() -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.Gutter),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(AccentDim), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Accent, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(Spacing.m))
                    Text("Was soll passieren?", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(Spacing.xs))
                    Text("Tippen oder sprechen, ganz normal formuliert.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Spacer(Modifier.height(Spacing.l))
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        SUGGESTIONS.forEach { s -> SuggestionChip(s, enabled = online) { send(s) } }
                    }
                }
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = Spacing.Gutter, vertical = Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(messages) { index, msg ->
                        val last = index == messages.lastIndex
                        Column {
                            if (msg.fromUser) UserBubble(msg.text) else AnswerBlock(msg)
                            if (last && !thinking && !msg.fromUser) {
                                if (msg.isError) {
                                    Spacer(Modifier.height(Spacing.s))
                                    SecondaryButton("Erneut versuchen", icon = Icons.Rounded.Refresh, enabled = online, onClick = onRetry)
                                } else if (msg.suggestions.isNotEmpty()) {
                                    Spacer(Modifier.height(Spacing.m))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                        msg.suggestions.forEach { s -> SuggestionChip(s, enabled = online) { send(s) } }
                                    }
                                }
                            }
                        }
                    }
                    if (thinking) item(key = "thinking") { ThinkingIndicator() }
                }
            }
        }

        voiceProblem?.let {
            InlineNotice(it, Modifier.padding(horizontal = Spacing.Gutter).fillMaxWidth(), icon = Icons.Rounded.Mic)
        }
        Row(
            Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(if (online) "Befehl eingeben" else "Hub nicht erreichbar") },
                shape = Shape.Pill,
                maxLines = 4,
                enabled = online,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(input) }),
                colors = fieldColors(),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.s + 2.dp))
            if (input.isNotBlank()) {
                RoundButton(Icons.AutoMirrored.Rounded.Send, "Senden", container = Accent, tint = OnAccent, enabled = online && !thinking) { send(input) }
            } else {
                RoundButton(Icons.Rounded.Mic, "Sprechen", container = AccentDim, tint = Accent, enabled = online && !thinking) {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Was soll der Fernseher tun?")
                    // No speech service (or it is disabled): say so instead of doing nothing.
                    runCatching { speech.launch(intent) }.onFailure {
                        voiceProblem = "Spracheingabe ist auf diesem Gerät nicht verfügbar. Prüfe, ob Google-Spracheingabe aktiv ist."
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionChip(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = TextPrimary,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .pressable(enabled = enabled, pressedScale = 0.97f, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .card(Shape.Tile)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    )
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            modifier = Modifier
                .padding(start = 48.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                .background(Surface2)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/** Answers sit on the background, without a bubble; what the hub did is listed underneath. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnswerBlock(msg: ChatMessage) {
    Row(Modifier.fillMaxWidth().padding(end = 24.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        Icon(
            if (msg.isError) Icons.Rounded.ErrorOutline else Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = if (msg.isError) StatusOff else Accent,
            modifier = Modifier.padding(top = 2.dp).size(18.dp),
        )
        Spacer(Modifier.width(Spacing.m))
        Column {
            Text(msg.text, style = MaterialTheme.typography.bodyLarge, color = if (msg.isError) StatusOff else TextPrimary)
            if (msg.actions.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.s))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    msg.actions.distinct().forEach { ActionTag(it) }
                }
            }
        }
    }
}

@Composable
private fun ActionTag(tool: String) {
    Row(
        Modifier
            .clip(Shape.Pill)
            .background(Surface1)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = StatusOn, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(tool.replace('_', ' '), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
}

@Composable
private fun ThinkingIndicator() {
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "thinking")
    Row(
        Modifier.semantics { contentDescription = "Gemini denkt nach" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.m))
        repeat(3) { i ->
            val a = if (reduce) null else t.animateFloat(
                0.25f, 1f,
                infiniteRepeatable(tween(600, delayMillis = i * 150), RepeatMode.Reverse),
                label = "dot$i",
            )
            Box(
                Modifier
                    .padding(end = 5.dp)
                    .size(7.dp)
                    .graphicsLayer { alpha = a?.value ?: 0.6f }
                    .clip(CircleShape)
                    .background(TextSecondary)
            )
        }
    }
}

@Composable
private fun KeyCard(saveKey: suspend (String) -> String?, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var key by rememberSaveable { mutableStateOf("") }
    var saving by rememberSaveable { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    Column(modifier.padding(Spacing.Gutter).card().padding(Spacing.Gutter)) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(AccentDim), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Key, contentDescription = null, tint = Accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(Spacing.m))
        Text("Gemini-API-Schlüssel fehlt", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.xs))
        Text("Der Schlüssel wird nur auf dem Hub am Fernseher gespeichert.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(Spacing.l))
        OutlinedTextField(
            value = key,
            onValueChange = { key = it; result = null },
            placeholder = { Text("AIza …") },
            singleLine = true,
            shape = Shape.Control,
            visualTransformation = PasswordVisualTransformation(),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.m))
        PrimaryButton("Auf dem Hub speichern", enabled = key.trim().length > 10 && !saving) {
            saving = true
            scope.launch {
                result = saveKey(key)
                saving = false
            }
        }
        result?.let {
            Spacer(Modifier.height(Spacing.s))
            Text(it, style = MaterialTheme.typography.bodySmall, color = StatusOff, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}
