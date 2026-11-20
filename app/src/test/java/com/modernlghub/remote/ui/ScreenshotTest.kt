package com.modernlghub.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.modernlghub.remote.data.Connection
import com.modernlghub.remote.data.TvAction
import com.modernlghub.remote.data.WebOsPointerClient
import com.modernlghub.remote.system.OverlayActions
import com.modernlghub.remote.system.QuickRemoteOverlay
import com.modernlghub.remote.ui.components.CursorPadSurface
import com.modernlghub.remote.ui.screens.AppsScreen
import com.modernlghub.remote.ui.screens.AssistantScreen
import com.modernlghub.remote.ui.screens.ChatMessage
import com.modernlghub.remote.ui.screens.PadMode
import com.modernlghub.remote.ui.screens.RemoteScreen
import com.modernlghub.remote.ui.screens.RemoteSetup
import com.modernlghub.remote.ui.screens.SettingsContent
import com.modernlghub.remote.ui.screens.SmartHomeScreen
import com.modernlghub.remote.ui.theme.ModernLgTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders every screen and the important states from plain fake state.
 *
 * `./gradlew testDebugUnitTest` composes and lays out all of them (catches crashes in any state);
 * `./gradlew testDebugUnitTest --tests '*ScreenshotTest*' -Pscreenshots` also writes the PNGs to
 * docs/screenshots (phone frame 393×852 dp, scaled to ~461×1000 px like the earlier images).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots")
    private val options = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.391))

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { ModernLgTheme(reduceMotion = true) { content() } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(dir, "$name.png"), roborazziOptions = options)
    }

    /** Includes dialog windows (bottom sheets). */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun shootScreen(name: String, content: @Composable () -> Unit) {
        compose.setContent { ModernLgTheme(reduceMotion = true) { content() } }
        compose.mainClock.advanceTimeBy(2000)
        compose.waitForIdle()
        captureScreenRoboImage(File(dir, "$name.png"), roborazziOptions = options)
    }

    /** The app frame: the remote inside the real Scaffold padding would need the activity; a black box is enough. */
    @Composable
    private fun Frame(content: @Composable (Modifier) -> Unit) {
        Scaffold(containerColor = Color.Black) { padding -> content(Modifier.fillMaxSize().padding(padding)) }
    }

    @Composable
    private fun Remote(
        state: com.modernlghub.remote.data.TvState,
        mode: Int = PadMode.SWIPE,
        setup: RemoteSetup = RemoteSetup("192.168.178.60"),
        pointer: WebOsPointerClient.State = WebOsPointerClient.State.READY,
    ) = Frame { m ->
        RemoteScreen(
            state = state, padMode = mode, setup = setup,
            onPadModeChange = {}, onIntent = {}, onOpenSettings = {}, iconUrl = { "" }, modifier = m,
            cursorPad = { pm, tint -> CursorPadSurface(pointer, pm, tint, { _, _ -> }, {}, {}, {}) },
        )
    }

    @Test fun remoteNetflix() = shoot("remote_netflix") { Remote(Fixtures.tv()) }

    @Test fun remotePcDpad() = shoot("remote_pc_dpad") {
        Remote(Fixtures.tv(app = "com.webos.app.hdmi1", output = "tv_speaker", volume = 12), mode = PadMode.DPAD)
    }

    @Test fun remoteScreenOff() = shoot("remote_screen_off") {
        Remote(Fixtures.tv(app = "spotify-beehive", screenOff = true, volume = 31))
    }

    @Test fun remotePointer() = shoot("remote_pointer") {
        Remote(Fixtures.tv(app = "youtube.leanback.v4", muted = true), mode = PadMode.POINTER)
    }

    @Test fun remoteOffline() = shoot("remote_offline") { Remote(Fixtures.tv(connection = Connection.OFFLINE)) }

    @Test fun remoteFirstRun() = shoot("remote_first_run") {
        Remote(Fixtures.tv(connection = Connection.OFFLINE), setup = RemoteSetup("192.168.178.60", canWake = false, needsSetup = true))
    }

    @Test fun remoteUnauthorized() = shoot("remote_unauthorized") { Remote(Fixtures.tv(connection = Connection.UNAUTHORIZED)) }

    @Test fun remoteWaking() = shoot("remote_waking") { Remote(Fixtures.tv(connection = Connection.OFFLINE, waking = true)) }

    @Test
    @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    fun remoteLargeFont() {
        RuntimeEnvironment.setFontScale(1.3f)
        shoot("remote_font_130") { Remote(Fixtures.tv(output = "tv_speaker_and_external_optical")) }
    }

    @Test
    @Config(qualifiers = "w852dp-h393dp-land-xxhdpi")
    fun remoteLandscape() = shoot("remote_landscape") { Remote(Fixtures.tv()) }

    @Test fun apps() = shoot("apps") {
        Frame { m -> AppsScreen(Fixtures.tv(), { "" }, {}, m) }
    }

    @Test fun appsLoading() = shoot("apps_loading") {
        Frame { m -> AppsScreen(Fixtures.tv(connection = Connection.CONNECTING), { "" }, {}, m) }
    }

    @Test fun smartHome() = shoot("smarthome") {
        Frame { m ->
            SmartHomeScreen(Fixtures.devices, false, false, emptyMap(), null, {}, {}, {}, { _, _ -> }, {}, m)
        }
    }

    @Test fun smartHomeError() = shoot("smarthome_error") {
        Frame { m -> SmartHomeScreen(null, false, true, emptyMap(), null, {}, {}, {}, { _, _ -> }, {}, m) }
    }

    @Test fun smartHomeLight() = shootScreen("smarthome_light") {
        Frame { m ->
            SmartHomeScreen(Fixtures.devices, false, false, emptyMap(), "bar", {}, {}, {}, { _, _ -> }, {}, m)
        }
    }

    @Test fun assistant() = shoot("assistant") {
        Frame { m -> AssistantScreen(emptyList(), false, true, true, { true }, {}, {}, { null }, m) }
    }

    @Test fun assistantChat() = shoot("assistant_chat") {
        val chat = listOf(
            ChatMessage(true, "Filmabend: TV-Licht an, Netflix starten"),
            ChatMessage(
                false, "Die Fernseher Bar leuchtet warm auf 40 %, Netflix startet.",
                actions = listOf("set_light", "launch_app"),
                suggestions = listOf("Etwas dunkler", "Ton auf den Denon"),
            ),
            ChatMessage(true, "Und jetzt etwas leiser"),
        )
        Frame { m -> AssistantScreen(chat, true, true, true, { true }, {}, {}, { null }, m) }
    }

    @Test fun assistantOffline() = shoot("assistant_offline") {
        Frame { m -> AssistantScreen(emptyList(), false, false, false, { true }, {}, {}, { null }, m) }
    }

    @Test fun settings() = shoot("settings") {
        Frame { m ->
            SettingsContent(
                connection = Connection.ONLINE, connectedTo = "192.168.178.60:5000",
                address = "192.168.178.60", onAddress = {}, token = "", onToken = {},
                dirty = false, onApply = {}, searching = false, onSearch = {}, connectionFeedback = null,
                mac = "AC:5A:F0:12:34:56", onMac = {},
                haptics = true, onHaptics = {}, hwVolume = true, onHwVolume = {},
                live = true, onLive = {}, notificationsBlocked = true, onOpenNotificationSettings = {},
                slots = TvAction.entries.take(4), onSlot = { _, _ -> },
                onResetPairing = {}, pairingFeedback = null, version = "LG Remote 2.3.0", onBack = {},
                modifier = m,
            )
        }
    }

    @Test fun overlay() = shoot("overlay") {
        Box(Modifier.fillMaxSize().background(Color(0xFF1E2A38))) {
            QuickRemoteOverlay(Fixtures.tv(), OverlayActions(null, null), {}, {})
        }
    }

    @Test fun overlayOff() = shoot("overlay_off") {
        Box(Modifier.fillMaxSize().background(Color(0xFF1E2A38))) {
            QuickRemoteOverlay(Fixtures.tv(connection = Connection.OFFLINE), OverlayActions(null, null), {}, {})
        }
    }
}

const val PHONE = "w393dp-h852dp-xxhdpi"
