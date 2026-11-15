package com.modernlghub.remote.ui

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.modernlghub.remote.data.TvRepository
import com.modernlghub.remote.system.TvPresenceService
import com.modernlghub.remote.ui.components.Haptics
import com.modernlghub.remote.ui.screens.AppsScreen
import com.modernlghub.remote.ui.screens.AssistantScreen
import com.modernlghub.remote.ui.screens.ChatMessage
import com.modernlghub.remote.ui.screens.RemoteScreen
import com.modernlghub.remote.ui.screens.SettingsScreen
import com.modernlghub.remote.ui.screens.SmartHomeScreen
import com.modernlghub.remote.ui.screens.handle
import com.modernlghub.remote.ui.theme.Accent
import com.modernlghub.remote.ui.theme.AccentDim
import com.modernlghub.remote.ui.theme.ModernLgTheme
import com.modernlghub.remote.ui.theme.Obsidian
import com.modernlghub.remote.ui.theme.TextPrimary
import com.modernlghub.remote.ui.theme.TextTertiary

class MainActivity : ComponentActivity() {

    private val repo by lazy { TvRepository.get(this) }
    private val haptics by lazy { Haptics(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Obsidian.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Obsidian.toArgb()),
        )
        super.onCreate(savedInstanceState)
        if (repo.prefs.liveNotificationEnabled) TvPresenceService.start(this)
        setContent { ModernLgTheme { MainContent(repo) } }
    }

    /** Galaxy volume rocker → TV volume while the app is in front. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (handleVolumeKey(keyCode, repo, haptics)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (isVolumeKey(keyCode) && repo.prefs.hardwareVolumeEnabled && repo.state.value.isOnline) return true
        return super.onKeyUp(keyCode, event)
    }
}

internal fun isVolumeKey(code: Int) =
    code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_MUTE

/** Shared by the main app and the mini remote. Falls through to the phone volume when the TV is off. */
internal fun handleVolumeKey(code: Int, repo: TvRepository, haptics: Haptics): Boolean {
    if (!isVolumeKey(code) || !repo.prefs.hardwareVolumeEnabled || !repo.state.value.isOnline) return false
    when (code) {
        KeyEvent.KEYCODE_VOLUME_UP -> repo.volumeUp()
        KeyEvent.KEYCODE_VOLUME_DOWN -> repo.volumeDown()
        else -> repo.toggleMute()
    }
    haptics.tick()
    return true
}

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val TABS = listOf(
    Tab("Fernbedienung", Icons.Rounded.SettingsRemote),
    Tab("Apps", Icons.Rounded.Apps),
    Tab("Zuhause", Icons.Rounded.Lightbulb),
    Tab("Assistent", Icons.Rounded.AutoAwesome),
)

@Composable
private fun MainContent(repo: TvRepository) {
    val state by repo.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var padMode by remember { mutableIntStateOf(repo.prefs.touchpadMode) }
    val chat = remember { mutableStateListOf<ChatMessage>() }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { repo.messages.collect { snackbar.showSnackbar(it) } }
    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = !showSettings && tab != 0) { tab = 0 }

    Scaffold(
        containerColor = Obsidian,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!showSettings) {
                NavigationBar(containerColor = Obsidian, tonalElevation = 0.dp) {
                    TABS.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Accent,
                                selectedTextColor = TextPrimary,
                                indicatorColor = AccentDim,
                                unselectedIconColor = TextTertiary,
                                unselectedTextColor = TextTertiary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        val content = Modifier
            .fillMaxSize()
            .padding(padding)
        AnimatedContent(
            targetState = if (showSettings) -1 else tab,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
            label = "screen",
        ) { screen ->
            when (screen) {
                -1 -> SettingsScreen(repo, state, onBack = { showSettings = false }, modifier = content)
                1 -> AppsScreen(state, repo.api::iconUrl, onLaunch = { repo.launch(it); tab = 0 }, modifier = content)
                2 -> SmartHomeScreen(
                    load = repo::smartDevices,
                    toggle = repo::toggleDevice,
                    setLight = { id, c -> repo.setDevice(id, c.on, c.brightness, c.color, c.kelvin) },
                    modifier = content,
                )
                3 -> AssistantScreen(
                    messages = chat,
                    hasKey = state.status.hasGeminiKey,
                    online = state.isOnline,
                    ask = repo::ask,
                    saveKey = { key -> repo.saveGeminiKey(key).also { if (it == null) repo.refresh() } },
                    modifier = content,
                )
                else -> RemoteScreen(
                    state = state,
                    padMode = padMode,
                    hubAddress = repo.prefs.tvIp,
                    onPadModeChange = { padMode = it; repo.prefs.touchpadMode = it },
                    onIntent = repo::handle,
                    onOpenSettings = { showSettings = true },
                    iconUrl = repo.api::iconUrl,
                    modifier = content,
                )
            }
        }
    }
}
