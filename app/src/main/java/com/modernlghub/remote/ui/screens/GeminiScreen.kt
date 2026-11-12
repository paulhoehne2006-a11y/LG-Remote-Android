package com.modernlghub.remote.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@Composable
fun GeminiScreen(
    apiClient: TvApiClient
) {
    val context = LocalContext.current
    val haptics = remember { Haptics(context) }
    var inputPrompt by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }

    fun sendPrompt(prompt: String) {
        if (prompt.isBlank()) return
        haptics.click()
        isThinking = true
        responseText = "Gemini denkt nach..."
        kotlinx.coroutines.GlobalScope.launch {
            val res = apiClient.sendGeminiCommand(prompt)
            responseText = res
            isThinking = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
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
                    text = "TV-Steuerung & Empfehlungen",
                    color = GeminiPurple,
                    fontSize = 13.sp
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
            "🎬 Kino-Beleuchtung" to "Schalte das TV-Licht und Wohnzimmerlicht für einen gemütlichen Filmabend ein",
            "🌙 Lichter Aus" to "Schalte alle Lichter im Wohnzimmer und am TV aus",
            "🖥️ PC & Schreibtisch" to "Schalte auf den PC (HDMI 1) und schalte das Schreibtischlicht an",
            "🖤 OLED Display Aus" to "Schalte den Bildschirm aus, damit Musik über den Denon-Verstärker weiterläuft",
            "🚀 Sci-Fi Tipps" to "Empfiehl mir 2-3 herausragende Sci-Fi Filme für heute Abend",
            "🍳 Schnelles Rezept" to "Schlage ein schnelles, raffiniertes Abendessen vor, das in unter 20 Minuten fertig ist"
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

        // Custom Prompt Entry
        OutlinedTextField(
            value = inputPrompt,
            onValueChange = { inputPrompt = it },
            placeholder = { Text("Frage an Gemini oder Befehl eingeben...", color = TextMuted) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
                focusedBorderColor = GeminiPurple,
                unfocusedBorderColor = CardBorder
            ),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = { sendPrompt(inputPrompt) },
            enabled = !isThinking && inputPrompt.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = GeminiPurple),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(text = if (isThinking) "Antworte..." else "Befehl an TV senden ✨", color = TextWhite)
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
                        text = "Antwort:",
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
