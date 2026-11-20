package com.modernlghub.remote.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Settings. Plain values live in [PREFS_FILE] (included in Android backup / device transfer);
 * credentials – the hub token and the TV's SSAP pairing key – live in [SECRETS_FILE], which the
 * backup rules exclude (res/xml/backup_rules.xml, data_extraction_rules.xml), so they never end
 * up in a cloud backup or on another phone.
 *
 * Cheap to construct: SharedPreferences instances are cached by the framework.
 */
class TvPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    private val secrets: SharedPreferences =
        context.applicationContext.getSharedPreferences(SECRETS_FILE, Context.MODE_PRIVATE)

    init {
        migrateSecrets()
    }

    /** Versions up to 2.3 kept the credentials in the main file; move them once. */
    private fun migrateSecrets() {
        if (!prefs.contains(KEY_HUB_TOKEN) && !prefs.contains(KEY_SSAP)) return
        val edit = secrets.edit()
        prefs.getString(KEY_HUB_TOKEN, null)?.let { if (!secrets.contains(KEY_HUB_TOKEN)) edit.putString(KEY_HUB_TOKEN, it) }
        prefs.getString(KEY_SSAP, null)?.let { if (!secrets.contains(KEY_SSAP)) edit.putString(KEY_SSAP, it) }
        // commit(): the old copy is only removed once the new one is on disk.
        if (edit.commit()) prefs.edit().remove(KEY_HUB_TOKEN).remove(KEY_SSAP).apply()
    }

    /** Host or host:port of the hub. Kept under the old key so existing installs keep their IP. */
    var tvIp: String
        get() = prefs.getString("tv_ip", DEFAULT_IP) ?: DEFAULT_IP
        set(value) = prefs.edit().putString("tv_ip", value.trim()).apply()

    val baseUrl: String get() = HubApi.normalizeBaseUrl(tvIp)

    /** Bare host, used for Wake-on-LAN unicast and the webOS pointer socket. */
    val tvHost: String get() = HubApi.hostOf(baseUrl)

    /** Shared secret if the hub runs with GEMINI_TOKEN; sent as X-Hub-Token. Empty = none. */
    var hubToken: String
        get() = secrets.getString(KEY_HUB_TOKEN, "") ?: ""
        set(value) = secrets.edit().putString(KEY_HUB_TOKEN, value.trim()).apply()

    /**
     * TV MAC for Wake-on-LAN, stored normalised as aa:bb:cc:dd:ee:ff when it is a valid MAC.
     * Setting it (the user typing in the settings) makes it a manual value that the hub's
     * report no longer overrides; see [learnMacFromHub].
     */
    var tvMac: String
        get() = prefs.getString(KEY_MAC, "") ?: ""
        set(value) = prefs.edit()
            .putString(KEY_MAC, normalizeMac(value) ?: value.trim())
            .putBoolean(KEY_MAC_FROM_HUB, false)
            .apply()

    /**
     * Takes the MAC the hub reports (`tv.network.primary_mac`) when none is set or the stored one
     * was learned from the hub before – so a TV moved from Wi-Fi to Ethernet gets its new MAC,
     * while a MAC typed in by hand is kept.
     */
    fun learnMacFromHub(reported: String) {
        val mac = normalizeMac(reported) ?: return
        val current = tvMac
        if (current == mac) return
        if (current.isBlank() || prefs.getBoolean(KEY_MAC_FROM_HUB, false)) {
            prefs.edit().putString(KEY_MAC, mac).putBoolean(KEY_MAC_FROM_HUB, true).apply()
        }
    }

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
        get() = secrets.getString(KEY_SSAP, "") ?: ""
        set(value) = secrets.edit().putString(KEY_SSAP, value).apply()

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
        const val DEFAULT_IP = ""   // no built-in address: first run asks for it or searches the LAN
        const val PREFS_FILE = "modern_lg_remote_prefs"
        const val SECRETS_FILE = "modern_lg_remote_secrets"
        private const val KEY_HUB_TOKEN = "hub_token"
        private const val KEY_SSAP = "ssap_client_key"
        private const val KEY_MAC = "tv_mac"
        private const val KEY_MAC_FROM_HUB = "tv_mac_from_hub"

        /** "A8-23-FE-01-02-03", "a823fe010203", … → "a8:23:fe:01:02:03"; null if not a MAC. */
        fun normalizeMac(input: String): String? {
            val hex = input.trim().filter { it.isLetterOrDigit() }.lowercase()
            if (hex.length != 12 || !hex.all { it in '0'..'9' || it in 'a'..'f' }) return null
            return hex.chunked(2).joinToString(":")
        }
    }
}
