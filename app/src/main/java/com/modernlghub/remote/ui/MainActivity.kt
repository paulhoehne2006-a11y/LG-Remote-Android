package com.modernlghub.remote.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.modernlghub.remote.data.LauncherApp
import com.modernlghub.remote.data.TvApiClient
import com.modernlghub.remote.data.TvStatus
import com.modernlghub.remote.ui.components.Haptics
import com.modernlghub.remote.ui.screens.AppsScreen
import com.modernlghub.remote.ui.screens.GeminiScreen
import com.modernlghub.remote.ui.screens.RemoteScreen
import com.modernlghub.remote.ui.screens.SettingsScreen
import com.modernlghub.remote.ui.screens.SmartHomeScreen
import com.modernlghub.remote.ui.theme.CardBorder
import com.modernlghub.remote.ui.theme.DenonGreen
import com.modernlghub.remote.ui.theme.ModernLgTheme
import com.modernlghub.remote.ui.theme.OledBlack
import com.modernlghub.remote.ui.theme.SurfaceCard
import com.modernlghub.remote.ui.theme.TextMuted
import com.modernlghub.remote.ui.theme.TextWhite
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var apiClient: TvApiClient
    private lateinit var haptics: Haptics

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiClient = TvApiClient(this)
        haptics = Haptics(this)

        checkNotificationPermission()

        setContent {
            ModernLgTheme {
                MainContent(apiClient = apiClient)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                haptics.tick()
                kotlinx.coroutines.GlobalScope.launch {
                    apiClient.quickAction("vol_up")
                }
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                haptics.tick()
                kotlinx.coroutines.GlobalScope.launch {
                    apiClient.quickAction("vol_down")
                }
                true
            }
            KeyEvent.KEYCODE_VOLUME_MUTE -> {
                haptics.click()
                kotlinx.coroutines.GlobalScope.launch {
                    apiClient.quickAction("mute")
                }
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun MainContent(apiClient: TvApiClient) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var tvStatus by remember { mutableStateOf(TvStatus()) }
    var appsList by remember { mutableStateOf<List<LauncherApp>>(emptyList()) }

    suspend fun refreshData() {
        tvStatus = apiClient.getStatus()
        val apps = apiClient.getApps()
        if (apps.isNotEmpty()) {
            appsList = apps
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
        while (true) {
            delay(3500)
            tvStatus = apiClient.getStatus()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Box(
                modifier = Modifier
                    .background(OledBlack)
                    .border(width = 0.5.dp, color = CardBorder)
            ) {
                NavigationBar(
                    containerColor = OledBlack,
                    contentColor = TextWhite,
                    tonalElevation = 0.dp
                ) {
                    val items = listOf(
                        NavigationItem("Remote", Icons.Default.Tv),
                        NavigationItem("Apps", Icons.Default.Apps),
                        NavigationItem("Smart Home", Icons.Default.Lightbulb),
                        NavigationItem("Gemini", Icons.Default.Stars),
                        NavigationItem("Settings", Icons.Default.Settings)
                    )

                    items.forEachIndexed { index, item ->
                        val isSelected = selectedTab == index
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                                )
                            },
                            selected = isSelected,
                            onClick = { selectedTab = index },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TextWhite,
                                selectedTextColor = TextWhite,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = SurfaceCard
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(OledBlack)
        ) {
            when (selectedTab) {
                0 -> RemoteScreen(
                    status = tvStatus,
                    apps = appsList,
                    apiClient = apiClient,
                    onRefreshStatus = {
                        kotlinx.coroutines.GlobalScope.launch { refreshData() }
                    }
                )
                1 -> AppsScreen(
                    apps = appsList,
                    apiClient = apiClient,
                    onAppLaunched = {
                        selectedTab = 0
                        kotlinx.coroutines.GlobalScope.launch { refreshData() }
                    }
                )
                2 -> SmartHomeScreen(apiClient = apiClient)
                3 -> GeminiScreen(apiClient = apiClient)
                4 -> SettingsScreen(apiClient = apiClient)
            }
        }
    }
}

private data class NavigationItem(val label: String, val icon: ImageVector)
