package com.modernlghub.remote.ui

import com.modernlghub.remote.data.AudioStatus
import com.modernlghub.remote.data.Connection
import com.modernlghub.remote.data.LauncherApp
import com.modernlghub.remote.data.SmartDevice
import com.modernlghub.remote.data.TvState
import com.modernlghub.remote.data.TvStatus

/** Fake hub data for screenshots and UI tests (mirrors the owner's real setup). */
object Fixtures {
    val apps = listOf(
        LauncherApp("netflix", "Netflix", brandColor = 0xFFE50914),
        LauncherApp("youtube.leanback.v4", "YouTube", brandColor = 0xFFFF0033),
        LauncherApp("amazon", "Prime Video", brandColor = 0xFF0F79FF),
        LauncherApp("com.disney.disneyplus-prod", "Disney+", brandColor = 0xFF0B4A5E),
        LauncherApp("spotify-beehive", "Spotify", brandColor = 0xFF1DB954),
        LauncherApp("com.apple.appletv", "Apple TV", brandColor = 0xFF000000),
        LauncherApp("ard.mediathek", "ARD Mediathek", brandColor = 0xFF003480),
        LauncherApp("joyn", "Joyn", brandColor = 0xFF053B44),
        LauncherApp("magentatv", "MagentaTV", brandColor = 0xFFE20074),
        LauncherApp("com.moonlight-stream", "Moonlight"),
        LauncherApp("rtlplus", "RTL+", brandColor = 0xFF000000),
        LauncherApp("skyq", "Sky Q", brandColor = 0xFF1C2D72),
        LauncherApp("xbox", "Xbox", brandColor = 0xFF107C10),
        LauncherApp("com.webos.app.hdmi1", "PC", type = "input"),
    )

    fun tv(
        app: String = "netflix",
        output: String = "external_optical",
        volume: Int = 23,
        screenOff: Boolean = false,
        muted: Boolean = false,
        connection: Connection = Connection.ONLINE,
        power: String = "Active",
        waking: Boolean = false,
    ) = TvState(
        connection = connection,
        status = TvStatus(
            power = power,
            screenOff = screenOff,
            currentAppId = app,
            audio = AudioStatus(output, volume, muted),
            hasGeminiKey = true,
        ),
        apps = if (connection == Connection.ONLINE) apps else emptyList(),
        waking = waking,
    )

    val devices = listOf(
        SmartDevice("bar", "Fernseher Bar", "light", isOn = true, room = "Wohnzimmer", brightness = 55, color = "#ff2a10", capabilities = setOf("brightness", "color", "white")),
        SmartDevice("ceiling", "Deckenlampe", "light", isOn = true, room = "Wohnzimmer", brightness = 80, color = "#ffb46b", kelvin = 2700, capabilities = setOf("brightness", "white")),
        SmartDevice("bed", "Bett", "plug", isOn = true, room = "Schlafzimmer"),
        SmartDevice("fridge", "Kühlschrank", "plug", isOn = false, online = false, room = "Küche"),
        SmartDevice("desk", "Schreibtisch", "light", isOn = false, room = "Büro", brightness = 40, capabilities = setOf("brightness", "color", "white")),
    )
}
