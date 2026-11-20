package com.modernlghub.remote.ui

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.modernlghub.remote.data.TvRepository
import com.modernlghub.remote.system.TvPresenceService
import com.modernlghub.remote.ui.components.Haptics
import com.modernlghub.remote.ui.screens.AppsScreen
import com.modernlghub.remote.ui.screens.AssistantController
import com.modernlghub.remote.ui.screens.AssistantScreen
import com.modernlghub.remote.ui.screens.ChatSaver
import com.modernlghub.remote.ui.screens.RemoteIntent
import com.modernlghub.remote.ui.screens.RemoteScreen
import com.modernlghub.remote.ui.screens.RemoteSetup
import com.modernlghub.remote.ui.screens.SettingsScreen
import com.modernlghub.remote.ui.screens.SmartHomeController
import com.modernlghub.remote.ui.screens.SmartHomeScreen
import com.modernlghub.remote.ui.screens.handle
import com.modernlghub.remote.ui.theme.Accent
import com.modernlghub.remote.ui.theme.AccentDim
import com.modernlghub.remote.ui.theme.ModernLgTheme
import com.modernlghub.remote.ui.theme.LocalReduceMotion
import com.modernlghub.remote.ui.theme.Motion
import com.modernlghub.remote.ui.theme.motionSpec
import com.modernlghub.remote.ui.theme.Obsidian
import com.modernlghub.remote.ui.theme.Shape
import com.modernlghub.remote.ui.theme.Surface3
import com.modernlghub.remote.ui.theme.TextPrimary
import com.modernlghub.remote.ui.theme.TextSecondary
import com.modernlghub.remote.ui.theme.motion

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
        if (isVolumeKey(keyCode) && repo.prefs.hardwareVolumeEnabled && repo.state.value.isOnline) {
            if (event?.isCanceled != true && (event?.eventTime ?: 0L) - (event?.downTime ?: 0L) > 400L) repo.cancelPendingKeys()
            return true
        }
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

private data class Tab(val label: String, val icon: ImageVector)

/** Bottom navigation. The same names are used for the screen titles. */
private val TABS = listOf(
    Tab("Fernbedienung", Icons.Rounded.SettingsRemote),
    Tab("Apps", Icons.Rounded.Apps),
    Tab("Zuhause", Icons.Rounded.Lightbulb),
    Tab("Assistent", Icons.Rounded.AutoAwesome),
)

private const val SETTINGS = -1

@Composable
private fun MainContent(repo: TvRepository) {
    val state by repo.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var padMode by rememberSaveable { mutableIntStateOf(repo.prefs.touchpadMode) }
    var lightSheetFor by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    // Survive tab switches (the screens leave composition) and, for the chat, rotation.
    val scope = rememberCoroutineScope()
    val chat = rememberSaveable(saver = ChatSaver) { androidx.compose.runtime.mutableStateListOf() }
    val assistant = remember(chat) { AssistantController(chat, scope, repo::ask) }
    val smartHome = remember {
        SmartHomeController(scope, repo::smartDevices, repo::toggleDevice) { id, c ->
            repo.setDevice(id, c.on, c.brightness, c.color, c.kelvin)
        }
    }
    // Per-tab saveable state (search text, scroll position) is kept while another tab is shown.
    val tabStates = rememberSaveableStateHolder()
    val iconUrl = remember(repo) { { id: String -> repo.api.iconUrl(id) } }
    val onIntent = remember(repo) { { intent: RemoteIntent -> repo.handle(intent) } }

    LaunchedEffect(Unit) { repo.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(tab, showSettings) { if (tab == 2 && !showSettings) smartHome.reload() }
    BackHandler(enabled = !showSettings && tab != 0) { tab = 0 }

    Scaffold(
        containerColor = Obsidian,
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(data, shape = Shape.Control, containerColor = Surface3, contentColor = TextPrimary, actionColor = Accent)
            }
        },
        bottomBar = {
            if (!showSettings) {
                NavigationBar(containerColor = Obsidian, tonalElevation = 0.dp) {
                    TABS.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Accent,
                                selectedTextColor = TextPrimary,
                                indicatorColor = AccentDim,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        // consumeWindowInsets: the keyboard inset is measured from the screen bottom; without this the
        // assistant's input floated a whole navigation bar height above the keyboard.
        val content = Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
        val reduce = LocalReduceMotion.current
        AnimatedContent(
            targetState = if (showSettings) SETTINGS else tab,
            transitionSpec = { fadeIn(motionSpec(reduce, Motion.Medium)) togetherWith fadeOut(motionSpec(reduce, Motion.Fast)) },
            label = "screen",
        ) { screen ->
            tabStates.SaveableStateProvider(screen) {
                when (screen) {
                    SETTINGS -> SettingsScreen(repo, state, onBack = { showSettings = false }, modifier = content)
                    1 -> AppsScreen(
                        state, iconUrl,
                        onLaunch = { repo.launch(it); tab = 0 },
                        onRetry = repo::resetConnection,
                        modifier = content,
                    )
                    2 -> SmartHomeScreen(
                        devices = smartHome.devices,
                        loading = smartHome.loading,
                        failed = smartHome.failed,
                        busy = smartHome.busy,
                        sheetFor = lightSheetFor,
                        onReload = { smartHome.reload() },
                        onToggle = smartHome::toggle,
                        onTune = { lightSheetFor = it },
                        onLightChange = smartHome::change,
                        onSheetDismiss = { lightSheetFor = null; smartHome.sheetClosed() },
                        modifier = content,
                    )
                    3 -> AssistantScreen(
                        messages = assistant.messages,
                        thinking = assistant.thinking,
                        hasKey = state.status.hasGeminiKey,
                        online = state.isOnline,
                        onSend = assistant::send,
                        onRetry = assistant::retry,
                        onClear = assistant::clear,
                        saveKey = { key -> repo.saveGeminiKey(key).also { if (it == null) repo.refresh() } },
                        modifier = content.imePadding(),
                    )
                    else -> RemoteScreen(
                        state = state,
                        padMode = padMode,
                        setup = RemoteSetup(
                            hubAddress = repo.prefs.tvIp,
                            canWake = repo.prefs.tvMac.isNotBlank(),
                            needsSetup = repo.prefs.tvMac.isBlank(),
                        ),
                        onPadModeChange = { padMode = it; repo.prefs.touchpadMode = it },
                        onIntent = onIntent,
                        onOpenSettings = { showSettings = true },
                        iconUrl = iconUrl,
                        modifier = content,
                    )
                }
            }
        }
    }
}
