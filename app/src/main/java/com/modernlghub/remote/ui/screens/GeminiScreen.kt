package com.modernlghub.remote.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modernlghub.remote.data.TvApiClient
import com.modernlghub.remote.ui.components.Haptics
import com.modernlghub.remote.ui.theme.CardBorder
import com.modernlghub.remote.ui.theme.GeminiPurple
import com.modernlghub.remote.ui.theme.OledBlack
import com.modernlghub.remote.ui.theme.SurfaceCard
import com.modernlghub.remote.ui.theme.SurfaceCardVariant
import com.modernlghub.remote.ui.theme.TextMuted
import com.modernlghub.remote.ui.theme.TextWhite
import kotlinx.coroutines.launch

@Composable
fun GeminiScreen(
    apiClient: TvApiClient
) {
    val context = LocalContext.current
    val haptics = remember { Haptics(context) }
    val scope = rememberCoroutineScope()
    var inputPrompt by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }

    fun sendPrompt(prompt: String) {
        if (prompt.isBlank()) return
        haptics.click()
        isThinking = true
        responseText = "Gemini denkt nach und steuert deinen TV..."
        scope.launch {
            val res = apiClient.sendGeminiCommand(prompt)
            responseText = res
            isThinking = false
        }
    }

    // Samsung Galaxy S26 Ultra Speech-To-Text Launcher
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                inputPrompt = spoken
                sendPrompt(spoken)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "✨", fontSize = 24.sp, modifier = Modifier.padding(end = 8.dp))
                Column {
                    Text(
                        text = "Gemini KI-Assistent",
                        color = TextWhite,
                        fontSize = 22.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(
                        text = "Sprachsteuerung & Smart TV",
                        color = GeminiPurple,
                        fontSize = 13.sp
                    )
                }
            }

            // Glowing Mic Quick Button in Header
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(GeminiPurple.copy(alpha = 0.2f))
                    .border(1.5.dp, GeminiPurple, CircleShape)
                    .clickable {
                        haptics.click()
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Sprich einen Befehl für deinen LG OLED...")
                        }
                        try {
                            speechLauncher.launch(intent)
                        } catch (_: Exception) {}
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Sprachbefehl",
                    tint = TextWhite,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Preset Quick Action Tiles
        Text(
            text = "SCHNELLAKTIONEN",
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val presets = listOf(
            "🎬 Kino-Abend" to "Schalte das TV-Licht und Wohnzimmerlicht für einen gemütlichen Filmabend ein und starte Netflix",
            "🌙 Alles Aus" to "Schalte alle SmartThings Lichter und den Fernseher aus",
            "🖥️ PC Gaming" to "Schalte auf den PC (HDMI 1) und aktiviere den Game Optimizer Modus",
            "🖤 OLED Display Aus" to "Schalte den Bildschirm aus, damit Musik über den Denon AVR-1803 weiterläuft",
            "🚀 Sci-Fi Empfehlungen" to "Empfiehl mir 3 packende Sci-Fi Filme für heute Abend",
            "🍳 15-Minuten Snack" to "Schlage einen schnellen, genialen Snack für den Fernsehabend vor"
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { (label, prompt) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceCard)
                                .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                                .clickable {
                                    inputPrompt = prompt
                                    sendPrompt(prompt)
                                }
                                .padding(12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(text = label, color = TextWhite, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Custom Prompt Entry with Mic and Send buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputPrompt,
                onValueChange = { inputPrompt = it },
                placeholder = { Text("Befehl sprechen oder tippen...", color = TextMuted) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedBorderColor = GeminiPurple,
                    unfocusedBorderColor = CardBorder
                ),
                maxLines = 3
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Speech Button next to input
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(GeminiPurple)
                    .clickable {
                        if (inputPrompt.isNotBlank()) {
                            sendPrompt(inputPrompt)
                        } else {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Sprich deinen Befehl...")
                            }
                            try {
                                speechLauncher.launch(intent)
                            } catch (_: Exception) {}
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (inputPrompt.isNotBlank()) Icons.Default.Send else Icons.Default.Mic,
                    contentDescription = "Senden / Sprechen",
                    tint = TextWhite,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (responseText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCardVariant)
                    .border(1.dp, GeminiPurple.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "Gemini Antwort:",
                        color = GeminiPurple,
                        fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Text(
                        text = responseText,
                        color = TextWhite,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
