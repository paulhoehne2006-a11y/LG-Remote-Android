package com.modernlghub.remote.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.modernlghub.remote.ui.components.NumPadSheet
import com.modernlghub.remote.ui.components.TextInputDialog
import com.modernlghub.remote.ui.theme.AccentBlue
import com.modernlghub.remote.ui.theme.AccentRed
import com.modernlghub.remote.ui.theme.AmberGlow
import com.modernlghub.remote.ui.theme.CardBorder
import com.modernlghub.remote.ui.theme.CyberCyan
import com.modernlghub.remote.ui.theme.DenonGreen
import com.modernlghub.remote.ui.theme.GeminiPurple
import com.modernlghub.remote.ui.theme.OledBlack
import com.modernlghub.remote.ui.theme.SurfaceCard
import com.modernlghub.remote.ui.theme.SurfaceCardElevated
import com.modernlghub.remote.ui.theme.SurfaceCardVariant
import com.modernlghub.remote.ui.theme.TextMuted
import com.modernlghub.remote.ui.theme.TextWhite
import com.modernlghub.remote.ui.theme.ToslinkRed
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()

    var selectedPadMode by remember { mutableIntStateOf(prefs.touchpadMode) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showNumPad by remember { mutableStateOf(false) }

    // Dynamic Ambient Color based on current app or source
    val targetAmbientColor = when {
        status.currentApp.contains("hdmi1", ignoreCase = true) -> CyberCyan
        status.currentApp.contains("youtube", ignoreCase = true) -> Color(0xFFFF0000)
        status.currentApp.contains("netflix", ignoreCase = true) -> Color(0xFFE50914)
        status.currentApp.contains("amazon", ignoreCase = true) || status.currentApp.contains("disney", ignoreCase = true) -> AccentBlue
        status.audio.isDenonOptical -> ToslinkRed
        status.isOnline -> DenonGreen
        else -> CardBorder
    }
    val ambientGlow by animateColorAsState(targetAmbientColor, tween(600))

    // Pulse animation for Online status indicator
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .drawBehind {
                // Top subtle ambient glow reflecting active app
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(ambientGlow.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.5f, 0f),
                        radius = size.width * 0.9f
                    )
                )
            }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Top Hero Header (OLED Status + Standby / WoL + Screen-Off)
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Pulsing LED indicator
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (status.isOnline) DenonGreen.copy(alpha = pulseAlpha)
                                else AccentRed.copy(alpha = 0.5f)
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "LG OLED G4",
                                color = TextWhite,
                                fontSize = 16.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SurfaceCardVariant)
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (status.currentApp.contains("hdmi1", ignoreCase = true)) "4K · 144Hz HDR" else "4K HDR",
                                    color = if (status.currentApp.contains("hdmi1", ignoreCase = true)) CyberCyan else TextMuted,
                                    fontSize = 9.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = if (status.isOnline) {
                                if (status.isScreenOff) "Display ausgeschaltet (Audio aktiv)" else status.currentAppTitle
                            } else "Standby · Zum Wecken Power tippen",
                            color = if (status.isScreenOff) AmberGlow else TextMuted,
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
                                scope.launch {
                                    apiClient.quickAction("screen_toggle")
                                    onRefreshStatus()
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
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
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SurfaceCard)
                            .border(1.dp, if (!status.isOnline) AccentRed.copy(alpha = 0.6f) else CardBorder, CircleShape)
                            .clickable {
                                haptics.heavyClick()
                                scope.launch {
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
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Denon AVR-1803 Audiophile Status Bar & 1-Tap Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceCard)
                    .border(1.dp, if (status.audio.isDenonOptical) ToslinkRed.copy(alpha = 0.4f) else CardBorder, RoundedCornerShape(14.dp))
                    .clickable {
                        haptics.doubleClick()
                        scope.launch {
                            apiClient.toggleAudioOutput()
                            onRefreshStatus()
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (status.audio.isDenonOptical) ToslinkRed else DenonGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (status.audio.isDenonOptical) "🔴 DENON AVR-1803 (OPTISCH)" else "🔊 TV-LAUTSPRECHER",
                            color = if (status.audio.isDenonOptical) ToslinkRed else TextWhite,
                            fontSize = 12.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = if (status.audio.isDenonOptical) "Toslink Bitstream aktiv · Tippen für TV-Speaker" else "Internes Audio · Tippen für Denon",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceCardVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (status.audio.isDenonOptical) "⇄ TV" else "⇄ Denon",
                        color = TextWhite,
                        fontSize = 11.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Quick Input & Bildmodus Deck (PC, Game Opt, Filmmaker, Cinema)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                item {
                    QuickChip(
                        label = "🖥️ PC (HDMI 1)",
                        highlight = status.currentApp.contains("hdmi1", ignoreCase = true),
                        accentColor = CyberCyan
                    ) {
                        haptics.doubleClick()
                        scope.launch {
                            apiClient.quickAction("pc")
                            onRefreshStatus()
                        }
                    }
                }
                item {
                    QuickChip(label = "🎮 Game Opt", accentColor = GeminiPurple) {
                        haptics.click()
                        scope.launch { apiClient.setPictureMode("game") }
                    }
                }
                item {
                    QuickChip(label = "🎬 Filmmaker", accentColor = AmberGlow) {
                        haptics.click()
                        scope.launch { apiClient.setPictureMode("filmmaker") }
                    }
                }
                item {
                    QuickChip(label = "🍿 Cinema", accentColor = AccentBlue) {
                        haptics.click()
                        scope.launch { apiClient.setPictureMode("cinema") }
                    }
                }
                item {
                    QuickChip(label = "🌙 ISF Dunkel", accentColor = TextWhite) {
                        haptics.click()
                        scope.launch { apiClient.setPictureMode("isf_dark") }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 4. Touchpad Mode Selector (Gesten | D-Pad | Magic Cursor)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceCardVariant)
                .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ModeTab(title = "Gesten", selected = selectedPadMode == 0, modifier = Modifier.weight(1f)) {
                haptics.tick()
                selectedPadMode = 0
                prefs.touchpadMode = 0
            }
            ModeTab(title = "Klassik D-Pad", selected = selectedPadMode == 1, modifier = Modifier.weight(1f)) {
                haptics.tick()
                selectedPadMode = 1
                prefs.touchpadMode = 1
            }
            ModeTab(title = "Magic Cursor", selected = selectedPadMode == 2, modifier = Modifier.weight(1f)) {
                haptics.tick()
                selectedPadMode = 2
                prefs.touchpadMode = 2
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 5. Main Touchpad / D-Pad Control Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (selectedPadMode) {
                0 -> GesturePad(
                    modifier = Modifier.fillMaxSize(),
                    onDirection = { dir ->
                        scope.launch {
                            apiClient.sendRemoteKey(dir, fast = true)
                        }
                    },
                    onSelect = {
                        scope.launch {
                            apiClient.sendRemoteKey("ENTER", fast = true)
                        }
                    },
                    onLongSelect = {
                        scope.launch {
                            apiClient.sendRemoteKey("ENTER", fast = false)
                        }
                    }
                )
                1 -> DPadControl(
                    modifier = Modifier.fillMaxSize(),
                    onDirection = { dir ->
                        scope.launch {
                            apiClient.sendRemoteKey(dir, fast = true)
                        }
                    },
                    onSelect = {
                        scope.launch {
                            apiClient.sendRemoteKey("ENTER", fast = true)
                        }
                    }
                )
                2 -> MagicCursorPad(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 6. Quick Remote Actions Strip (Back, Home, Play, Mute, Text, 123, Vol-/Vol+)
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
                scope.launch { apiClient.sendRemoteKey("BACK") }
            }

            RemoteActionButton(icon = Icons.Default.Home, label = "Home") {
                haptics.click()
                scope.launch { apiClient.sendRemoteKey("HOME") }
            }

            RemoteActionButton(icon = Icons.Default.PlayArrow, label = "Play/Pause") {
                haptics.click()
                scope.launch { apiClient.sendRemoteKey("PLAY") }
            }

            RemoteActionButton(icon = Icons.Default.VolumeMute, label = "Mute") {
                haptics.click()
                scope.launch { apiClient.quickAction("mute") }
            }

            RemoteActionButton(icon = Icons.Default.Keyboard, label = "Tastatur") {
                haptics.click()
                showTextDialog = true
            }

            RemoteActionButton(icon = Icons.Default.Dialpad, label = "123") {
                haptics.click()
                showNumPad = true
            }

            // Volume Steppers (Tactile notch haptics)
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
                            haptics.tick(scale = 0.8f)
                            scope.launch { apiClient.quickAction("vol_down") }
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(text = "–", color = TextWhite, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .clickable {
                            haptics.tick(scale = 0.8f)
                            scope.launch { apiClient.quickAction("vol_up") }
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(text = "+", color = TextWhite, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 7. Favoriten Dock (PC HDMI 1 + Top Apps from Modern LG Hub)
        Column {
            Text(
                text = "FAVORITEN (MODERN LG HUB)",
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 5.dp)
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
                            haptics.doubleClick()
                            scope.launch {
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
                scope.launch {
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
                scope.launch { apiClient.sendText(text) }
            },
            onSendEnter = {
                scope.launch { apiClient.sendEnter() }
            },
            onDismiss = { showTextDialog = false }
        )
    }

    // 0-9 NumPad Sheet
    if (showNumPad) {
        NumPadSheet(
            onSendKey = { key ->
                scope.launch { apiClient.sendRemoteKey(key) }
            },
            onDismiss = { showNumPad = false }
        )
    }
}

@Composable
private fun QuickChip(
    label: String,
    highlight: Boolean = false,
    accentColor: Color = TextWhite,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlight) accentColor.copy(alpha = 0.2f) else SurfaceCard)
            .border(1.dp, if (highlight) accentColor else CardBorder, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (highlight) accentColor else TextWhite,
            fontSize = 11.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
        )
    }
}

@Composable
private fun ModeTab(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) SurfaceCardElevated else Color.Transparent)
            .border(1.dp, if (selected) CardBorder else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (selected) TextWhite else TextMuted,
            fontSize = 12.sp,
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
        )
    }
}

@Composable
private fun RemoteActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = TextWhite,
            modifier = Modifier.size(20.dp)
        )
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
            .width(58.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceCard)
                .border(1.dp, CardBorder, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = iconUrl,
                contentDescription = app.title,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = app.title,
            color = TextMuted,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
