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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.modernlghub.remote.data.SmartDevice
import com.modernlghub.remote.data.TvApiClient
import com.modernlghub.remote.ui.components.Haptics
import com.modernlghub.remote.ui.theme.CardBorder
import com.modernlghub.remote.ui.theme.DenonGreen
import com.modernlghub.remote.ui.theme.OledBlack
import com.modernlghub.remote.ui.theme.SurfaceCard
import com.modernlghub.remote.ui.theme.SurfaceCardVariant
import com.modernlghub.remote.ui.theme.TextMuted
import com.modernlghub.remote.ui.theme.TextWhite

@Composable
fun SmartHomeScreen(
    apiClient: TvApiClient
) {
    val context = LocalContext.current
    val haptics = remember { Haptics(context) }
    var devices by remember { mutableStateOf<List<SmartDevice>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    suspend fun loadDevices() {
        isLoading = true
        devices = apiClient.getSmartDevices()
        isLoading = false
    }

    LaunchedEffect(Unit) {
        loadDevices()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Smart Home",
                    color = TextWhite,
                    fontSize = 22.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = "SmartThings Steuerung",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceCard)
                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    .clickable {
                        haptics.click()
                        kotlinx.coroutines.GlobalScope.launch { loadDevices() }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(text = "Aktualisieren 🔄", color = TextWhite, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading && devices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Lade SmartThings Geräte...", color = TextMuted, fontSize = 14.sp)
            }
        } else if (devices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Keine Geräte gefunden oder SmartThings Token nicht aktiv.", color = TextMuted, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(devices) { dev ->
                    SmartDeviceItem(
                        device = dev,
                        onToggle = {
                            haptics.click()
                            kotlinx.coroutines.GlobalScope.launch {
                                apiClient.toggleSmartDevice(dev.id)
                                loadDevices()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartDeviceItem(
    device: SmartDevice,
    onToggle: () -> Unit
) {
    val isOn = device.state.equals("on", ignoreCase = true)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .border(
                width = 1.dp,
                color = if (isOn) DenonGreen.copy(alpha = 0.6f) else CardBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onToggle() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (device.type == "plug") "🔌" else "💡",
                    fontSize = 24.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column {
                    Text(
                        text = device.name,
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    Text(
                        text = if (isOn) "Eingeschaltet" else "Ausgeschaltet",
                        color = if (isOn) DenonGreen else TextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isOn) DenonGreen else SurfaceCardVariant)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isOn) "AN" else "AUS",
                    color = if (isOn) OledBlack else TextMuted,
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }
    }
}
