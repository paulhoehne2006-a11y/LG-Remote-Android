package com.modernlghub.remote.data

import android.content.Context
import android.content.SharedPreferences

class TvPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("modern_lg_remote_prefs", Context.MODE_PRIVATE)

    /** Host or host:port of the hub. Kept under the old key so existing installs keep their IP. */
    var tvIp: String
        get() = prefs.getString("tv_ip", DEFAULT_IP) ?: DEFAULT_IP
        set(value) = prefs.edit().putString("tv_ip", value.trim()).apply()

    val baseUrl: String get() = HubApi.normalizeBaseUrl(tvIp)

    /** Bare host, used for Wake-on-LAN unicast and the webOS pointer socket. */
    val tvHost: String get() = baseUrl.substringAfter("://").substringBefore(":")

    /** Shared secret if the hub runs with GEMINI_TOKEN; sent as X-Hub-Token. Empty = none. */
    var hubToken: String
        get() = prefs.getString("hub_token", "") ?: ""
        set(value) = prefs.edit().putString("hub_token", value.trim()).apply()

    var tvMac: String
        get() = prefs.getString("tv_mac", "") ?: ""
        set(value) = prefs.edit().putString("tv_mac", value.trim()).apply()

    /** 0 = Wischen, 1 = Steuerkreuz, 2 = Mauszeiger. */
    var touchpadMode: Int
        get() = prefs.getInt("touchpad_mode", 0).coerceIn(0, 2)
        set(value) = prefs.edit().putInt("touchpad_mode", value).apply()

    var hapticFeedbackEnabled: Boolean
        get() = prefs.getBoolean("haptic_feedback", true)
        set(value) = prefs.edit().putBoolean("haptic_feedback", value).apply()

    var hardwareVolumeEnabled: Boolean
        get() = prefs.getBoolean("hardware_volume", true)
        set(value) = prefs.edit().putBoolean("hardware_volume", value).apply()

    /** Ongoing media notification + lock screen controls. Off by default: it is a foreground service. */
    var liveNotificationEnabled: Boolean
        get() = prefs.getBoolean("live_notification_v2", false)
        set(value) = prefs.edit().putBoolean("live_notification_v2", value).apply()

    /** webOS SSAP client key, so the TV only asks for pairing once (Mauszeiger). */
    var ssapClientKey: String
        get() = prefs.getString("ssap_client_key", "") ?: ""
        set(value) = prefs.edit().putString("ssap_client_key", value).apply()

    var widgetSlot1: String
        get() = prefs.getString("widget_slot_1", TvAction.POWER.key) ?: TvAction.POWER.key
        set(value) = prefs.edit().putString("widget_slot_1", value).apply()

    var widgetSlot2: String
        get() = prefs.getString("widget_slot_2", TvAction.SCREEN.key) ?: TvAction.SCREEN.key
        set(value) = prefs.edit().putString("widget_slot_2", value).apply()

    var widgetSlot3: String
        get() = prefs.getString("widget_slot_3", TvAction.PC.key) ?: TvAction.PC.key
        set(value) = prefs.edit().putString("widget_slot_3", value).apply()

    var widgetSlot4: String
        get() = prefs.getString("widget_slot_4", TvAction.REMOTE.key) ?: TvAction.REMOTE.key
        set(value) = prefs.edit().putString("widget_slot_4", value).apply()

    fun widgetSlots(): List<TvAction> =
        listOf(widgetSlot1, widgetSlot2, widgetSlot3, widgetSlot4).map { TvAction.fromKey(it) ?: TvAction.REMOTE }

    fun setWidgetSlot(index: Int, action: TvAction) {
        when (index) {
            0 -> widgetSlot1 = action.key
            1 -> widgetSlot2 = action.key
            2 -> widgetSlot3 = action.key
            else -> widgetSlot4 = action.key
        }
    }

    companion object {
        const val DEFAULT_IP = "192.168.178.60"
    }
}
