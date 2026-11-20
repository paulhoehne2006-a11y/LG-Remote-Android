package com.modernlghub.remote.data

import com.modernlghub.remote.R

/**
 * One-tap actions shared by the widget, app shortcuts, Samsung routines and the notification.
 * Keys are stable (stored in prefs and shortcut intents) — do not rename them.
 */
enum class TvAction(val key: String, val label: String, val shortLabel: String, val iconRes: Int) {
    POWER("power", "Ein / Aus", "Power", R.drawable.ic_power),
    SCREEN("screen_off", "Display aus / an (Audio läuft weiter)", "Display", R.drawable.ic_oled_off),
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
            KEY_POWER_ON, KEY_POWER_OFF -> POWER
            else -> entries.firstOrNull { it.key == key }
        }

        /** Extra keys for routines: switch on / off explicitly instead of toggling. */
        const val KEY_POWER_ON = "power_on"
        const val KEY_POWER_OFF = "power_off"

        /**
         * true for "power_on", false for "power_off", null for every other key. A routine
         * "TV an" must not switch an already running TV off, which plain [POWER] (a toggle) would.
         */
        fun explicitPower(key: String?): Boolean? = when (key) {
            KEY_POWER_ON -> true
            KEY_POWER_OFF -> false
            else -> null
        }

        /**
         * Validates an action key that arrived from outside the app (exported shortcut activity,
         * Samsung routines, widget broadcasts). Only short lowercase keys of known actions pass;
         * anything else (wrong type, oversized, unknown) is null.
         */
        fun fromExternal(value: Any?): TvAction? {
            val key = value as? String ?: return null
            if (key.isEmpty() || key.length > MAX_KEY_LENGTH || !key.all { it in 'a'..'z' || it == '_' }) return null
            return fromKey(key)
        }

        private const val MAX_KEY_LENGTH = 32

        /** webOS id of the PC input (HDMI 1). */
        const val PC_INPUT_ID = "com.webos.app.hdmi1"

        val APP_IDS = mapOf(
            NETFLIX to "netflix",
            YOUTUBE to "youtube.leanback.v4",
            SPOTIFY to "spotify-beehive",
        )
    }
}
