package com.modernlghub.remote.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.modernlghub.remote.data.TvPreferences
import com.modernlghub.remote.system.TvRemoteWidgetProvider
import com.modernlghub.remote.ui.components.Haptics
import com.modernlghub.remote.ui.theme.AccentBlue
import com.modernlghub.remote.ui.theme.CardBorder
import com.modernlghub.remote.ui.theme.DenonGreen
import com.modernlghub.remote.ui.theme.OledBlack
import com.modernlghub.remote.ui.theme.SurfaceCard
import com.modernlghub.remote.ui.theme.SurfaceCardVariant
import com.modernlghub.remote.ui.theme.TextMuted
import com.modernlghub.remote.ui.theme.TextWhite

@Composable
fun SettingsScreen(
    apiClient: TvApiClient
) {
    val context = LocalContext.current
    val prefs = remember { TvPreferences(context) }
    val haptics = remember { Haptics(context) }

    var tvIp by remember { mutableStateOf(prefs.tvIp) }
    var tvMac by remember { mutableStateOf(prefs.tvMac) }
    var hapticsEnabled by remember { mutableStateOf(prefs.hapticFeedbackEnabled) }
    var liveNotifEnabled by remember { mutableStateOf(prefs.liveNotificationEnabled) }

    var slot1 by remember { mutableStateOf(prefs.widgetSlot1) }
    var slot2 by remember { mutableStateOf(prefs.widgetSlot2) }
    var slot3 by remember { mutableStateOf(prefs.widgetSlot3) }
    var slot4 by remember { mutableStateOf(prefs.widgetSlot4) }

    var testStatusText by remember { mutableStateOf("") }

    fun triggerWidgetUpdate() {
        val intent = Intent(context, TvRemoteWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, TvRemoteWidgetProvider::class.java))
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        context.sendBroadcast(intent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Einstellungen",
            color = TextWhite,
            fontSize = 22.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        Text(
            text = "TV-Verbindung & One UI Integration",
            color = TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // TV Network Section
        SettingsCard(title = "LG TV VERBINDUNG") {
            OutlinedTextField(
                value = tvIp,
                onValueChange = {
                    tvIp = it
                    prefs.tvIp = it
                },
                label = { Text("TV IP-Adresse") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = CardBorder
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = tvMac,
                onValueChange = {
                    tvMac = it
                    prefs.tvMac = it
                },
                label = { Text("TV MAC-Adresse (für Wake-on-LAN)") },
                placeholder = { Text("Wird automatisch vom TV gelernt") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = CardBorder
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        haptics.click()
                        testStatusText = "Verbinde..."
                        kotlinx.coroutines.GlobalScope.launch {
                            val st = apiClient.getStatus()
                            testStatusText = if (st.isOnline) {
                                "Online! App: ${st.currentAppTitle}, MAC: ${st.primaryMac}"
                            } else {
                                "Keine Antwort (TV im Standby oder IP falsch)"
                            }
                            if (st.primaryMac.isNotEmpty()) {
                                tvMac = st.primaryMac
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceCardVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Verbindung testen", color = TextWhite)
                }

                Button(
                    onClick = {
                        haptics.heavyClick()
                        kotlinx.coroutines.GlobalScope.launch {
                            val ok = apiClient.sendWakeOnLan()
                            testStatusText = if (ok) "Wake-on-LAN Paket gesendet!" else "Fehler: Keine MAC hinterlegt"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DenonGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("WoL Wecken", color = OledBlack, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }

            if (testStatusText.isNotEmpty()) {
                Text(
                    text = testStatusText,
                    color = DenonGreen,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // System Integrations
        SettingsCard(title = "SYSTEM & HAPTIK") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "S26 Ultra Haptik-Feedback", color = TextWhite, fontSize = 15.sp)
                    Text(text = "Taktile Klicks bei D-Pad & Wischgesten", color = TextMuted, fontSize = 12.sp)
                }
                Switch(
                    checked = hapticsEnabled,
                    onCheckedChange = {
                        hapticsEnabled = it
                        prefs.hapticFeedbackEnabled = it
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = DenonGreen)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Live-Notification & Sperrbildschirm", color = TextWhite, fontSize = 15.sp)
                    Text(text = "Automatische Steuerung sobald TV an ist", color = TextMuted, fontSize = 12.sp)
                }
                Switch(
                    checked = liveNotifEnabled,
                    onCheckedChange = {
                        liveNotifEnabled = it
                        prefs.liveNotificationEnabled = it
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = DenonGreen)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2x2 Widget Customizer
        SettingsCard(title = "2x2 STARTBILDSCHIRM-WIDGET BELEGUNG") {
            Text(
                text = "Wähle frei, welche 4 Aktionen auf dem 2x2 Startbildschirm-Widget deines S26 Ultra liegen:",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            WidgetSlotPicker(label = "Button 1 (Oben Links)", selectedKey = slot1) {
                slot1 = it
                prefs.widgetSlot1 = it
                triggerWidgetUpdate()
            }

            Spacer(modifier = Modifier.height(8.dp))

            WidgetSlotPicker(label = "Button 2 (Oben Rechts)", selectedKey = slot2) {
                slot2 = it
                prefs.widgetSlot2 = it
                triggerWidgetUpdate()
            }

            Spacer(modifier = Modifier.height(8.dp))

            WidgetSlotPicker(label = "Button 3 (Unten Links)", selectedKey = slot3) {
                slot3 = it
                prefs.widgetSlot3 = it
                triggerWidgetUpdate()
            }

            Spacer(modifier = Modifier.height(8.dp))

            WidgetSlotPicker(label = "Button 4 (Unten Rechts)", selectedKey = slot4) {
                slot4 = it
                prefs.widgetSlot4 = it
                triggerWidgetUpdate()
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceCard)
                .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun WidgetSlotPicker(
    label: String,
    selectedKey: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayLabel = TvPreferences.AVAILABLE_WIDGET_ACTIONS.find { it.first == selectedKey }?.second ?: selectedKey

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextWhite, fontSize = 13.sp)

        Box {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceCardVariant)
                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = "$displayLabel ▾", color = DenonGreen, fontSize = 12.sp)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(SurfaceCard)
            ) {
                TvPreferences.AVAILABLE_WIDGET_ACTIONS.forEach { (key, name) ->
                    DropdownMenuItem(
                        text = { Text(text = name, color = TextWhite, fontSize = 13.sp) },
                        onClick = {
                            onSelect(key)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
