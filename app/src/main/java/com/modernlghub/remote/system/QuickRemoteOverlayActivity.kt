package com.modernlghub.remote.system

import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modernlghub.remote.data.TvApiClient
import com.modernlghub.remote.data.TvStatus
import com.modernlghub.remote.ui.components.GesturePad
import com.modernlghub.remote.ui.components.Haptics
import com.modernlghub.remote.ui.theme.CardBorder
import com.modernlghub.remote.ui.theme.DenonGreen
import com.modernlghub.remote.ui.theme.ModernLgTheme
import com.modernlghub.remote.ui.theme.SurfaceCard
import com.modernlghub.remote.ui.theme.SurfaceCardVariant
import com.modernlghub.remote.ui.theme.TextMuted
import com.modernlghub.remote.ui.theme.TextWhite

class QuickRemoteOverlayActivity : ComponentActivity() {

    private lateinit var apiClient: TvApiClient
    private lateinit var haptics: Haptics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiClient = TvApiClient(this)
        haptics = Haptics(this)

        window.setGravity(Gravity.BOTTOM)
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        setContent {
            ModernLgTheme {
                OverlayContent(
                    apiClient = apiClient,
                    onClose = { finish() }
                )
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                haptics.tick()
                kotlinx.coroutines.GlobalScope.launch { apiClient.quickAction("vol_up") }
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                haptics.tick()
                kotlinx.coroutines.GlobalScope.launch { apiClient.quickAction("vol_down") }
                true
            }
            KeyEvent.KEYCODE_VOLUME_MUTE -> {
                haptics.click()
                kotlinx.coroutines.GlobalScope.launch { apiClient.quickAction("mute") }
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}

@Composable
private fun OverlayContent(
    apiClient: TvApiClient,
    onClose: () -> Unit
) {
    val haptics = remember { Haptics(androidx.compose.ui.platform.LocalContext.current) }
    var status by remember { mutableStateOf(TvStatus()) }

    LaunchedEffect(Unit) {
        status = apiClient.getStatus()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(SurfaceCard)
            .border(1.dp, CardBorder, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .padding(16.dp)
    ) {
        Column {
            // Drag handle / Header
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(CardBorder)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (status.isOnline) DenonGreen else CardBorder)
                    )
                    Text(
                        text = " LG OLED G4 · ${status.currentAppTitle}",
                        color = TextWhite,
                        fontSize = 15.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Quick OLED Off
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceCardVariant)
                            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                            .clickable {
                                haptics.click()
                                kotlinx.coroutines.GlobalScope.launch {
                                    apiClient.quickAction("screen_toggle")
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = "OLED Aus", color = TextWhite, fontSize = 11.sp)
                    }

                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Schließen", tint = TextMuted)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Gesture Pad
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
            ) {
                GesturePad(
                    modifier = Modifier.fillMaxWidth().height(230.dp),
                    onDirection = { dir ->
                        kotlinx.coroutines.GlobalScope.launch { apiClient.sendRemoteKey(dir) }
                    },
                    onSelect = {
                        kotlinx.coroutines.GlobalScope.launch { apiClient.sendRemoteKey("ENTER") }
                    },
                    onLongSelect = {
                        kotlinx.coroutines.GlobalScope.launch { apiClient.sendRemoteKey("ENTER", fast = false) }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Control Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    haptics.click()
                    kotlinx.coroutines.GlobalScope.launch { apiClient.sendRemoteKey("BACK") }
                }) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Zurück", tint = TextWhite)
                }

                IconButton(onClick = {
                    haptics.click()
                    kotlinx.coroutines.GlobalScope.launch { apiClient.sendRemoteKey("HOME") }
                }) {
                    Icon(imageVector = Icons.Default.Home, contentDescription = "Home", tint = TextWhite)
                }

                IconButton(onClick = {
                    haptics.click()
                    kotlinx.coroutines.GlobalScope.launch { apiClient.sendRemoteKey("PLAY") }
                }) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = TextWhite)
                }

                IconButton(onClick = {
                    haptics.click()
                    kotlinx.coroutines.GlobalScope.launch { apiClient.quickAction("mute") }
                }) {
                    Icon(imageVector = Icons.Default.VolumeMute, contentDescription = "Mute", tint = TextWhite)
                }

                // Audio Output Toggle (Denon vs TV)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceCardVariant)
                        .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                        .clickable {
                            haptics.click()
                            kotlinx.coroutines.GlobalScope.launch {
                                apiClient.toggleAudioOutput()
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(text = "🔊 Denon ⇄ TV", color = DenonGreen, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                }
            }
        }
    }
}
