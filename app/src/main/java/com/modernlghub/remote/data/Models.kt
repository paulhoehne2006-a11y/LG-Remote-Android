package com.modernlghub.remote.data

import org.json.JSONArray
import org.json.JSONObject

/** Where the hub connection currently stands. */
enum class Connection {
    CONNECTING,
    ONLINE,
    OFFLINE,

    /** Hub reachable, but it requires an X-Hub-Token (server env GEMINI_TOKEN) we don't have. */
    UNAUTHORIZED,
}

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
        /**
         * Parses `/api/status`. Throws if the body is not a JSON object with a `tv` object
         * (e.g. an HTML error page from something else on port 8080); every field inside is optional.
         */
        fun parse(body: String): TvStatus {
            val root = JSONObject(body)
            val tv = root.optJSONObject("tv") ?: throw IllegalArgumentException("status without \"tv\" object")
            val audio = tv.obj("audio")
            val net = tv.obj("network")
            val app = tv.str("current_app").trim()
            return TvStatus(
                power = tv.str("power").trim(),
                screenOff = tv.bool("screen_off"),
                currentAppId = if (app.equals("Unknown", ignoreCase = true) || app == "null") "" else app,
                audio = AudioStatus(
                    soundOutput = audio.str("soundOutput").trim(),
                    volume = (audio.intOrNull("volume") ?: 0).coerceIn(0, 100),
                    muted = audio.bool("muted"),
                ),
                primaryMac = net.str("primary_mac").trim(),
                hasGeminiKey = root.bool("has_key"),
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
            val seen = HashSet<String>()
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.str("id").trim()
                // Blank or duplicate ids would break list keys in the UI.
                if (id.isBlank() || !seen.add(id)) return@mapNotNull null
                LauncherApp(
                    id = id,
                    title = o.str("title").trim().ifBlank { id },
                    type = o.str("type", "app").ifBlank { "app" },
                    visible = o.bool("visible", true),
                    brandColor = parseColor(o.str("iconColor")),
                    usage = o.doubleOrNull("usage") ?: 0.0,
                )
            }
        }

        private fun parseColor(hex: String): Long? {
            val clean = hex.trim().removePrefix("#")
            if (clean.length != 6) return null
            return clean.toLongOrNull(16)?.let { 0xFF000000 or it }
        }
    }
}

/**
 * A lamp or plug the hub controls locally (WiZ bulbs, Tapo plugs: `/api/smarthome/devices`).
 * Older hubs only send id/name/type/state; every newer field is optional.
 */
data class SmartDevice(
    val id: String,
    val name: String,
    val type: String,
    val isOn: Boolean,
    /** False when the hub could not reach the device (`"online": false`). */
    val online: Boolean = true,
    val room: String = "",
    /** 10..100 for dimmable lamps, null otherwise. */
    val brightness: Int? = null,
    /** Current light colour as `#rrggbb` (white tones are converted by the hub). */
    val color: String? = null,
    /** Colour temperature when the lamp is in white mode. */
    val kelvin: Int? = null,
    /** What the hub can set: "brightness", "color", "white". */
    val capabilities: Set<String> = emptySet(),
) {
    val isPlug get() = type == "plug"
    val canDim get() = CAP_BRIGHTNESS in capabilities
    val canColor get() = CAP_COLOR in capabilities
    val canWhite get() = CAP_WHITE in capabilities
    /** Has anything beyond on/off worth opening the light sheet for. */
    val adjustable get() = online && !isPlug && (canDim || canColor || canWhite)
    /** Colour as 0xRRGGBB, or null if the hub sent none. */
    val rgb: Int? get() = parseHexColor(color)

    companion object {
        const val CAP_BRIGHTNESS = "brightness"
        const val CAP_COLOR = "color"
        const val CAP_WHITE = "white"
        const val KELVIN_MIN = 2200
        const val KELVIN_MAX = 6500

        fun parseList(body: String): List<SmartDevice> {
            // The hub answers a JSON array; tolerate {"devices": [...]} as well.
            val arr = runCatching { JSONArray(body) }.getOrNull()
                ?: JSONObject(body).optJSONArray("devices")
                ?: JSONArray()
            val seen = HashSet<String>()
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.str("id").trim()
                if (id.isBlank() || !seen.add(id)) return@mapNotNull null
                val type = o.str("type", "light").ifBlank { "light" }
                val brightness = o.intOrNull("brightness")?.coerceIn(0, 100)
                val caps = o.optJSONArray("capabilities")
                    ?.let { a -> (0 until a.length()).mapNotNull { a.opt(it) as? String }.toSet() }
                    // Older hubs don't list capabilities: a lamp reporting brightness can at least be dimmed.
                    ?: if (type != "plug" && brightness != null) setOf(CAP_BRIGHTNESS) else emptySet()
                SmartDevice(
                    id = id,
                    name = o.str("name").trim().ifBlank { id },
                    type = type,
                    isOn = o.str("state", "off").trim().equals("on", ignoreCase = true) || o.opt("state") == true,
                    online = o.bool("online", true),
                    room = o.str("room").trim(),
                    brightness = brightness,
                    color = o.str("color").trim().takeIf { parseHexColor(it) != null },
                    kelvin = o.intOrNull("kelvin")?.takeIf { it > 0 },
                    capabilities = caps,
                )
            }
        }

        /** `#rrggbb` → 0xRRGGBB; null for anything else. */
        fun parseHexColor(hex: String?): Int? {
            val h = hex?.trim()?.removePrefix("#") ?: return null
            if (h.length != 6) return null
            return h.toIntOrNull(16)
        }

        fun toHexColor(rgb: Int): String = "#%06x".format(rgb and 0xFFFFFF)

        /** Approximate colour of a white light at [kelvin] (Tanner Helland), as 0xRRGGBB. */
        fun kelvinToRgb(kelvin: Int): Int {
            val t = kelvin.coerceIn(1000, 40000) / 100.0
            val r = if (t <= 66) 255.0 else 329.698727446 * Math.pow(t - 60, -0.1332047592)
            val g = if (t <= 66) 99.4708025861 * Math.log(t) - 161.1195681661
            else 288.1221695283 * Math.pow(t - 60, -0.0755148492)
            val b = when {
                t >= 66 -> 255.0
                t <= 19 -> 0.0
                else -> 138.5177312231 * Math.log(t - 10) - 305.0447927307
            }
            fun c(v: Double) = v.toInt().coerceIn(0, 255)
            return (c(r) shl 16) or (c(g) shl 8) or c(b)
        }
    }
}

data class AssistantReply(
    val text: String,
    val actions: List<String> = emptyList(),
    val isError: Boolean = false,
    /** Follow-up questions Gemini proposes (hub field "suggestions"). */
    val suggestions: List<String> = emptyList(),
    /** Hub has no Gemini API key ("needs_key": true). */
    val needsKey: Boolean = false,
) {
    companion object {
        fun parse(body: String): AssistantReply {
            val o = JSONObject(body)
            val text = o.str("text").ifBlank { o.str("reply").ifBlank { o.str("response") } }.trim()
            val arr = o.optJSONArray("actions") ?: JSONArray()
            // Actions are {"tool": ...} objects; a bare string is accepted too.
            val actions = (0 until arr.length()).mapNotNull {
                when (val a = arr.opt(it)) {
                    is JSONObject -> a.str("tool")
                    is String -> a
                    else -> null
                }?.trim()?.takeIf { t -> t.isNotEmpty() }
            }
            val sug = o.optJSONArray("suggestions") ?: JSONArray()
            val suggestions = (0 until sug.length())
                .mapNotNull { (sug.opt(it) as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } }
                .take(3)
            return AssistantReply(
                text = text.ifBlank { "Erledigt." },
                actions = actions,
                suggestions = suggestions,
                needsKey = o.bool("needs_key"),
            )
        }
    }
}

/** One earlier turn sent back to the hub as conversation history. */
data class AssistantTurn(val fromUser: Boolean, val text: String) {
    companion object {
        /** Hub format: [{"role": "user"|"model", "text": "..."}]. */
        fun toJson(turns: List<AssistantTurn>): JSONArray = JSONArray().apply {
            turns.filter { it.text.isNotBlank() }.forEach {
                put(JSONObject().put("role", if (it.fromUser) "user" else "model").put("text", it.text))
            }
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
