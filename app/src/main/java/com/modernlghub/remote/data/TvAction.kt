package com.modernlghub.remote.data

import com.modernlghub.remote.R

/**
 * One-tap actions shared by the widget, app shortcuts, Samsung routines and the notification.
 * Keys are stable (stored in prefs and shortcut intents) — do not rename them.
 */
enum class TvAction(val key: String, val label: String, val shortLabel: String, val iconRes: Int) {
    POWER("power", "Ein / Aus", "Power", R.drawable.ic_power),
    SCREEN("screen_off", "Bildschirm aus / an (Audio läuft weiter)", "Display", R.drawable.ic_oled_off),
    PC("pc", "PC (HDMI 1)", "PC", R.drawable.ic_pc),
    REMOTE("overlay", "Mini-Fernbedienung öffnen", "Remote", R.drawable.ic_remote),
    AUDIO("audio_toggle", "Audio: Denon ⇄ TV", "Audio", R.drawable.ic_audio),
    MUTE("mute", "Stumm", "Stumm", R.drawable.ic_mute),
    PLAY_PAUSE("play_pause", "Wiedergabe / Pause", "Play", R.drawable.ic_play),
    HOME("home", "Home (Modern LG Hub)", "Home", R.drawable.ic_home),
    NETFLIX("netflix", "Netflix", "Netflix", R.drawable.ic_app),
    YOUTUBE("youtube", "YouTube", "YouTube", R.drawable.ic_app),
    SPOTIFY("spotify", "Spotify", "Spotify", R.drawable.ic_app);

    /** True if the action needs UI rather than a background request. */
    val opensUi: Boolean get() = this == REMOTE

    companion object {
        fun fromKey(key: String?): TvAction? = when (key) {
            null -> null
            "screen_toggle" -> SCREEN
            "power_on", "power_off" -> POWER
            else -> entries.firstOrNull { it.key == key }
        }

        val APP_IDS = mapOf(
            NETFLIX to "netflix",
            YOUTUBE to "youtube.leanback.v4",
            SPOTIFY to "spotify-beehive",
        )
    }
}
