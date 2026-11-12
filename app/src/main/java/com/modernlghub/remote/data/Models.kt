package com.modernlghub.remote.data

import org.json.JSONArray
import org.json.JSONObject

/** Where the hub connection currently stands. */
enum class Connection { CONNECTING, ONLINE, OFFLINE }

data class AudioStatus(
    val soundOutput: String = "",
    val volume: Int = 0,
    val muted: Boolean = false,
) {
    val isOptical: Boolean get() = soundOutput.contains("optical", ignoreCase = true)
    val isTvSpeaker: Boolean get() = soundOutput.contains("speaker", ignoreCase = true) && !isOptical
    val isBluetooth: Boolean get() = soundOutput.startsWith("bt", ignoreCase = true)

    /** Very short label for chips. */
    val chipLabel: String
        get() = when {
            soundOutput == "tv_speaker_and_external_optical" -> "TV+Denon"
            isOptical -> "Denon"
            isTvSpeaker -> "TV"
            isBluetooth -> "Bluetooth"
            else -> "Audio"
        }

    /** Short, human label for the active output. */
    val outputLabel: String
        get() = when {
            soundOutput.isBlank() -> "Audio"
            soundOutput == "tv_speaker_and_external_optical" -> "TV + Denon"
            isOptical -> "Denon AVR"
            isTvSpeaker -> "TV-Lautsprecher"
            isBluetooth -> "Bluetooth"
            else -> soundOutput
        }
}

data class TvStatus(
    val power: String = "",
    val screenOff: Boolean = false,
    val currentAppId: String = "",
    val audio: AudioStatus = AudioStatus(),
    val primaryMac: String = "",
    val hasGeminiKey: Boolean = false,
) {
    /** Hub answered, but the panel itself is in (active) standby. */
    val isStandby: Boolean
        get() {
            val p = power.lowercase()
            return !screenOff && (p.contains("standby") || p.contains("suspend") || p == "power off")
        }

    companion object {
        fun parse(body: String): TvStatus {
            val root = JSONObject(body)
            val tv = root.optJSONObject("tv") ?: JSONObject()
            val audio = tv.optJSONObject("audio") ?: JSONObject()
            val net = tv.optJSONObject("network") ?: JSONObject()
            val app = tv.optString("current_app", "")
            return TvStatus(
                power = tv.optString("power", ""),
                screenOff = tv.optBoolean("screen_off", false),
                currentAppId = if (app == "Unknown") "" else app,
                audio = AudioStatus(
                    soundOutput = audio.optString("soundOutput", ""),
                    volume = audio.optInt("volume", 0),
                    muted = audio.optBoolean("muted", false),
                ),
                primaryMac = net.optString("primary_mac", ""),
                hasGeminiKey = root.optBoolean("has_key", false),
            )
        }
    }
}

data class LauncherApp(
    val id: String,
    val title: String,
    val type: String = "app",
    val visible: Boolean = true,
    val brandColor: Long? = null,
    val usage: Double = 0.0,
) {
    val isInput: Boolean get() = type == "input" || id.startsWith("com.webos.app.hdmi")

    companion object {
        fun parseList(body: String): List<LauncherApp> {
            val arr = JSONObject(body).optJSONArray("apps") ?: JSONArray()
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                if (id.isBlank()) return@mapNotNull null
                LauncherApp(
                    id = id,
                    title = o.optString("title", id).ifBlank { id },
                    type = o.optString("type", "app"),
                    visible = o.optBoolean("visible", true),
                    brandColor = parseColor(o.optString("iconColor", "")),
                    usage = o.optDouble("usage", 0.0).let { if (it.isNaN()) 0.0 else it },
                )
            }
        }

        private fun parseColor(hex: String): Long? {
            val clean = hex.removePrefix("#")
            if (clean.length != 6) return null
            return clean.toLongOrNull(16)?.let { 0xFF000000 or it }
        }
    }
}

data class SmartDevice(
    val id: String,
    val name: String,
    val type: String,
    val isOn: Boolean,
) {
    companion object {
        fun parseList(body: String): List<SmartDevice> {
            val arr = JSONArray(body)
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                if (id.isBlank()) return@mapNotNull null
                SmartDevice(
                    id = id,
                    name = o.optString("name", id),
                    type = o.optString("type", "light"),
                    isOn = o.optString("state", "off").equals("on", ignoreCase = true),
                )
            }
        }
    }
}

data class AssistantReply(
    val text: String,
    val actions: List<String> = emptyList(),
    val isError: Boolean = false,
) {
    companion object {
        fun parse(body: String): AssistantReply {
            val o = JSONObject(body)
            val text = o.optString("text").ifBlank { o.optString("reply").ifBlank { o.optString("response") } }
            val arr = o.optJSONArray("actions") ?: JSONArray()
            val actions = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("tool") }
                .filter { it.isNotBlank() }
            return AssistantReply(text = text.ifBlank { "Erledigt." }, actions = actions)
        }
    }
}

/** Picture modes the hub understands (`/api/picture/mode`). */
enum class PictureMode(val key: String, val label: String, val hint: String) {
    GAME("game", "Game Optimizer", "144 Hz · VRR · niedrigste Latenz"),
    FILMMAKER("filmmaker", "Filmmaker", "Wie vom Regisseur gedacht"),
    CINEMA("cinema", "Kino", "Warm, kontrastreich"),
    ISF_DARK("isf_dark", "ISF Experte (dunkel)", "Kalibriert für dunkle Räume"),
    ISF_BRIGHT("isf_bright", "ISF Experte (hell)", "Kalibriert für helle Räume"),
    STANDARD("standard", "Standard", "Ausgewogen"),
}

/** Known sound outputs (`/api/audio/output`). */
enum class SoundOutput(val key: String, val label: String) {
    DENON("external_optical", "Denon AVR (optisch)"),
    TV("tv_speaker", "TV-Lautsprecher"),
    BOTH("tv_speaker_and_external_optical", "TV + Denon"),
    BLUETOOTH("bt_soundbar", "Bluetooth"),
}
