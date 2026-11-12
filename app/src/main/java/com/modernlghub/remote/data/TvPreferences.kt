package com.modernlghub.remote.data

import android.content.Context
import android.content.SharedPreferences

class TvPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("modern_lg_remote_prefs", Context.MODE_PRIVATE)

    var tvIp: String
        get() = prefs.getString("tv_ip", "192.168.178.60") ?: "192.168.178.60"
        set(value) = prefs.edit().putString("tv_ip", value.trim()).apply()

    var tvMac: String
        get() = prefs.getString("tv_mac", "") ?: ""
        set(value) = prefs.edit().putString("tv_mac", value.trim()).apply()

    var touchpadMode: Int
        get() = prefs.getInt("touchpad_mode", 0) // 0: Gesture, 1: DPad, 2: Magic Cursor
        set(value) = prefs.edit().putInt("touchpad_mode", value).apply()

    var hapticFeedbackEnabled: Boolean
        get() = prefs.getBoolean("haptic_feedback", true)
        set(value) = prefs.edit().putBoolean("haptic_feedback", value).apply()

    var liveNotificationEnabled: Boolean
        get() = prefs.getBoolean("live_notification_enabled", true)
        set(value) = prefs.edit().putBoolean("live_notification_enabled", value).apply()

    // 2x2 Widget Slot Configuration
    var widgetSlot1: String
        get() = prefs.getString("widget_slot_1", "power") ?: "power"
        set(value) = prefs.edit().putString("widget_slot_1", value).apply()

    var widgetSlot2: String
        get() = prefs.getString("widget_slot_2", "screen_off") ?: "screen_off"
        set(value) = prefs.edit().putString("widget_slot_2", value).apply()

    var widgetSlot3: String
        get() = prefs.getString("widget_slot_3", "pc") ?: "pc"
        set(value) = prefs.edit().putString("widget_slot_3", value).apply()

    var widgetSlot4: String
        get() = prefs.getString("widget_slot_4", "overlay") ?: "overlay"
        set(value) = prefs.edit().putString("widget_slot_4", value).apply()

    companion object {
        const val ACTION_POWER = "power"
        const val ACTION_SCREEN_OFF = "screen_off"
        const val ACTION_PC = "pc"
        const val ACTION_OVERLAY = "overlay"
        const val ACTION_AUDIO = "audio_toggle"
        const val ACTION_MUTE = "mute"
        const val ACTION_HOME = "home"
        const val ACTION_NETFLIX = "netflix"
        const val ACTION_YOUTUBE = "youtube"
        const val ACTION_SPOTIFY = "spotify"

        val AVAILABLE_WIDGET_ACTIONS = listOf(
            ACTION_POWER to "Power (Ein via WoL / Aus)",
            ACTION_SCREEN_OFF to "OLED Display Aus / An",
            ACTION_PC to "PC (HDMI 1)",
            ACTION_OVERLAY to "Mini-Remote Overlay",
            ACTION_AUDIO to "Audio (Denon Optisch ↔ TV)",
            ACTION_MUTE to "Stummschalten (Mute)",
            ACTION_HOME to "Home (Modern LG Hub)",
            ACTION_NETFLIX to "Netflix",
            ACTION_YOUTUBE to "YouTube",
            ACTION_SPOTIFY to "Spotify"
        )
    }
}
