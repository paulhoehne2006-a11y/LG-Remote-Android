package com.modernlghub.remote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.modernlghub.remote.data.LauncherApp
import com.modernlghub.remote.data.TvApiClient
import com.modernlghub.remote.data.TvPreferences
import com.modernlghub.remote.data.TvStatus
import com.modernlghub.remote.ui.components.AudioOutputDialog
import com.modernlghub.remote.ui.components.DPadControl
import com.modernlghub.remote.ui.components.GesturePad
import com.modernlghub.remote.ui.components.Haptics
import com.modernlghub.remote.ui.components.MagicCursorPad
import com.modernlghub.remote.ui.components.TextInputDialog
import com.modernlghub.remote.ui.theme.AccentBlue
import com.modernlghub.remote.ui.theme.AccentRed
import com.modernlghub.remote.ui.theme.CardBorder
import com.modernlghub.remote.ui.theme.DenonGreen
import com.modernlghub.remote.ui.theme.OledBlack
import com.modernlghub.remote.ui.theme.SurfaceCard
import com.modernlghub.remote.ui.theme.SurfaceCardVariant
import com.modernlghub.remote.ui.theme.TextMuted
import com.modernlghub.remote.ui.theme.TextWhite

@Composable
fun RemoteScreen(
    status: TvStatus,
    apps: List<LauncherApp>,
    apiClient: TvApiClient,
    onRefreshStatus: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { TvPreferences(context) }
    val haptics = remember { Haptics(context) }

    var selectedPadMode by remember { mutableIntStateOf(prefs.touchpadMode) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Top Hero Header
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // TV Status / App Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (status.isOnline) DenonGreen else AccentRed)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "LG OLED G4",
                            color = TextWhite,
                            fontSize = 17.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = if (status.isOnline) {
                                if (status.isScreenOff) "Display Aus · Audio aktiv" else status.currentAppTitle
                            } else "Standby (Tippen zum Wecken)",
                            color = TextMuted,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }

                // Header Action Buttons: OLED Aus & Power
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // OLED Screen-Off Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (status.isScreenOff) DenonGreen.copy(alpha = 0.2f) else SurfaceCard)
                            .border(1.dp, if (status.isScreenOff) DenonGreen else CardBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                haptics.click()
                                kotlinx.coroutines.GlobalScope.launch {
                                    apiClient.quickAction("screen_toggle")
                                    onRefreshStatus()
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (status.isScreenOff) "OLED An" else "OLED Aus",
                            color = if (status.isScreenOff) DenonGreen else TextWhite,
                            fontSize = 12.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                    }

                    // Power Button (WoL / Standby)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SurfaceCard)
                            .border(1.dp, CardBorder, CircleShape)
                            .clickable {
                                haptics.heavyClick()
                                kotlinx.coroutines.GlobalScope.launch {
                                    if (status.isOnline) {
                                        apiClient.quickAction("power_off")
                                    } else {
                                        apiClient.sendWakeOnLan()
                                    }
                                    onRefreshStatus()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Power",
                            tint = AccentRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1-Tap Audio Output Badge (Denon AVR vs TV Speaker)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceCard)
                    .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                    .clickable {
                        haptics.click()
                        showAudioDialog = true
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (status.audio.isDenonOptical) "🔊" else "🔈",
                            fontSize = 18.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Column {
                            Text(
                                text = status.audio.displayTitle,
                                color = TextWhite,
                                fontSize = 14.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                            Text(
                                text = if (status.audio.isDenonOptical) "Optisch · Tippen zum Umschalten" else "Vol: ${status.audio.volume}",
                                color = if (status.audio.isDenonOptical) DenonGreen else TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceCardVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = "Wechseln ⇄", color = TextWhite, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. Pad Mode Selector (Gesten / D-Pad / Cursor)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceCard)
                .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val modes = listOf("Gesten", "Steuerkreuz", "Magic Cursor")
            modes.forEachIndexed { index, title ->
                val isSelected = selectedPadMode == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) SurfaceCardVariant else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable {
                            haptics.click()
                            selectedPadMode = index
                            prefs.touchpadMode = index
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) TextWhite else TextMuted,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3. Main Touchpad / D-Pad Control Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (selectedPadMode) {
                0 -> GesturePad(
                    modifier = Modifier.fillMaxSize(),
                    onDirection = { dir ->
                        kotlinx.coroutines.GlobalScope.launch {
                            apiClient.sendRemoteKey(dir, fast = true)
                        }
                    },
                    onSelect = {
                        kotlinx.coroutines.GlobalScope.launch {
                            apiClient.sendRemoteKey("ENTER", fast = true)
                        }
                    },
                    onLongSelect = {
                        kotlinx.coroutines.GlobalScope.launch {
                            apiClient.sendRemoteKey("ENTER", fast = false)
                        }
                    }
                )
                1 -> DPadControl(
                    modifier = Modifier.fillMaxSize(),
                    onDirection = { dir ->
                        kotlinx.coroutines.GlobalScope.launch {
                            apiClient.sendRemoteKey(dir, fast = true)
                        }
                    },
                    onSelect = {
                        kotlinx.coroutines.GlobalScope.launch {
                            apiClient.sendRemoteKey("ENTER", fast = true)
                        }
                    }
                )
                2 -> MagicCursorPad(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4. Quick Remote Actions Row (Back, Home, Play/Pause, Mute, Keyboard, Vol-/Vol+)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceCard)
                .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RemoteActionButton(icon = Icons.Default.ArrowBack, label = "Zurück") {
                haptics.click()
                kotlinx.coroutines.GlobalScope.launch { apiClient.sendRemoteKey("BACK") }
            }

            RemoteActionButton(icon = Icons.Default.Home, label = "Home") {
                haptics.click()
                kotlinx.coroutines.GlobalScope.launch { apiClient.sendRemoteKey("HOME") }
            }

            RemoteActionButton(icon = Icons.Default.PlayArrow, label = "Play/Pause") {
                haptics.click()
                kotlinx.coroutines.GlobalScope.launch { apiClient.sendRemoteKey("PLAY") }
            }

            RemoteActionButton(icon = Icons.Default.VolumeMute, label = "Mute") {
                haptics.click()
                kotlinx.coroutines.GlobalScope.launch { apiClient.quickAction("mute") }
            }

            RemoteActionButton(icon = Icons.Default.Keyboard, label = "Tastatur") {
                haptics.click()
                showTextDialog = true
            }

            // Volume Steppers
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceCardVariant)
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clickable {
                            haptics.tick()
                            kotlinx.coroutines.GlobalScope.launch { apiClient.quickAction("vol_down") }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(text = "–", color = TextWhite, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .clickable {
                            haptics.tick()
                            kotlinx.coroutines.GlobalScope.launch { apiClient.quickAction("vol_up") }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(text = "+", color = TextWhite, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 5. Favoriten Dock (PC HDMI 1 + Top Apps from Modern LG Hub)
        Column {
            Text(
                text = "FAVORITEN (MODERN LG HUB)",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(apps.take(8)) { app ->
                    DockAppItem(
                        app = app,
                        iconUrl = apiClient.getAppIconUrl(app.id),
                        onClick = {
                            haptics.click()
                            kotlinx.coroutines.GlobalScope.launch {
                                apiClient.launchApp(app.id)
                                onRefreshStatus()
                            }
                        }
                    )
                }
            }
        }
    }

    // Audio Output Dialog
    if (showAudioDialog) {
        AudioOutputDialog(
            currentAudio = status.audio,
            onSelectOutput = { output ->
                kotlinx.coroutines.GlobalScope.launch {
                    apiClient.setAudioOutput(output)
                    onRefreshStatus()
                }
            },
            onDismiss = { showAudioDialog = false }
        )
    }

    // Text Input Dialog
    if (showTextDialog) {
        TextInputDialog(
            onSendText = { text ->
                kotlinx.coroutines.GlobalScope.launch { apiClient.sendText(text) }
            },
            onSendEnter = {
                kotlinx.coroutines.GlobalScope.launch { apiClient.sendEnter() }
            },
            onDismiss = { showTextDialog = false }
        )
    }
}

@Composable
private fun RemoteActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = TextWhite, modifier = Modifier.size(22.dp))
        Text(text = label, color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun DockAppItem(
    app: LauncherApp,
    iconUrl: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(66.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (app.id.contains("hdmi1")) {
                Text(text = "🖥️", fontSize = 24.sp)
            } else if (app.id.contains("custom.launcher")) {
                Text(text = "✨", fontSize = 24.sp)
            } else {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = app.title,
                    modifier = Modifier.size(38.dp)
                )
            }
        }
        Text(
            text = app.title,
            color = TextWhite,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
