package com.modernlghub.remote.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class TvApiClient(private val context: Context) {
    private val prefs = TvPreferences(context)

    private val httpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(5, 1, TimeUnit.MINUTES))
        .connectTimeout(2000, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .writeTimeout(2000, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val baseUrl: String
        get() = "http://${prefs.tvIp}:8080"

    suspend fun getStatus(): TvStatus = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$baseUrl/api/status").get().build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext TvStatus(isOnline = false)

            val body = resp.body?.string() ?: return@withContext TvStatus(isOnline = false)
            val json = JSONObject(body)
            val tvObj = json.optJSONObject("tv") ?: JSONObject()

            val power = tvObj.optString("power", "Active")
            val isScreenOff = tvObj.optBoolean("screen_off", false)
            val currentApp = tvObj.optString("current_app", "")

            // Parse Audio
            val audioObj = tvObj.optJSONObject("audio") ?: JSONObject()
            val soundOut = audioObj.optString("soundOutput", "external_optical")
            val vol = audioObj.optInt("volume", 15)
            val muted = audioObj.optBoolean("muted", false)
            val isDenon = audioObj.optBoolean("is_denon_optical", true)
            val isTv = audioObj.optBoolean("is_tv_speaker", false)
            val displayTitle = audioObj.optString("display_title", "Denon AVR-1803 (Optisch)")
            val audioStatus = AudioStatus(soundOut, vol, muted, isDenon, isTv, displayTitle)

            // Parse MAC addresses
            val netObj = tvObj.optJSONObject("network") ?: JSONObject()
            val macsObj = netObj.optJSONObject("mac_addresses") ?: JSONObject()
            val macMap = mutableMapOf<String, String>()
            val keys = macsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                macMap[k] = macsObj.optString(k)
            }
            val primaryMac = netObj.optString("primary_mac", "")
            if (primaryMac.isNotEmpty() && prefs.tvMac.isEmpty()) {
                prefs.tvMac = primaryMac
            }

            TvStatus(
                isOnline = true,
                powerState = power,
                isScreenOff = isScreenOff,
                currentApp = currentApp,
                currentAppTitle = formatAppTitle(currentApp),
                audio = audioStatus,
                macAddresses = macMap,
                primaryMac = primaryMac
            )
        } catch (e: Exception) {
            TvStatus(isOnline = false)
        }
    }

    suspend fun getApps(): List<LauncherApp> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$baseUrl/api/apps").get().build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()

            val body = resp.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val appsArr = json.optJSONArray("apps") ?: JSONArray()
            val list = mutableListOf<LauncherApp>()

            for (i in 0 until appsArr.length()) {
                val o = appsArr.getJSONObject(i)
                val id = o.optString("id")
                val title = o.optString("title", id)
                val icon = o.optString("icon", "")
                val type = o.optString("type", "app")
                val alias = o.optString("alias", "")
                val usage = o.optDouble("usage", 0.0)
                list.add(LauncherApp(id, title, icon, type, alias, usage))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getAppIconUrl(appId: String): String {
        return "$baseUrl/api/icon/$appId"
    }

    suspend fun sendRemoteKey(key: String, fast: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().put("key", key).put("fast", fast).toString()
            val body = payload.toRequestBody(jsonMediaType)
            val req = Request.Builder().url("$baseUrl/api/remote/key").post(body).build()
            val resp = httpClient.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun sendText(text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().put("text", text).toString()
            val body = payload.toRequestBody(jsonMediaType)
            val req = Request.Builder().url("$baseUrl/api/remote/text").post(body).build()
            val resp = httpClient.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteText(length: Int = 1): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().put("length", length).toString()
            val body = payload.toRequestBody(jsonMediaType)
            val req = Request.Builder().url("$baseUrl/api/remote/delete").post(body).build()
            val resp = httpClient.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun sendEnter(): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = "{}".toRequestBody(jsonMediaType)
            val req = Request.Builder().url("$baseUrl/api/remote/enter").post(body).build()
            val resp = httpClient.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setAudioOutput(output: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().put("output", output).toString()
            val body = payload.toRequestBody(jsonMediaType)
            val req = Request.Builder().url("$baseUrl/api/audio/output").post(body).build()
            val resp = httpClient.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun toggleAudioOutput(): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = "{}".toRequestBody(jsonMediaType)
            val req = Request.Builder().url("$baseUrl/api/audio/toggle").post(body).build()
            val resp = httpClient.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun launchApp(appId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().put("id", appId).toString()
            val body = payload.toRequestBody(jsonMediaType)
            val req = Request.Builder().url("$baseUrl/api/launch").post(body).build()
            val resp = httpClient.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun quickAction(action: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().put("action", action).toString()
            val body = payload.toRequestBody(jsonMediaType)
            val req = Request.Builder().url("$baseUrl/api/quick").post(body).build()
            val resp = httpClient.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setPictureMode(mode: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().put("mode", mode).toString()
            val body = payload.toRequestBody(jsonMediaType)
            val req = Request.Builder().url("$baseUrl/api/picture/mode").post(body).build()
            val resp = httpClient.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getSmartDevices(): List<SmartDevice> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$baseUrl/api/smartthings/devices").get().build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()

            val body = resp.body?.string() ?: return@withContext emptyList()
            val arr = JSONArray(body)
            val list = mutableListOf<SmartDevice>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("id")
                val name = o.optString("name", id)
                val raw = o.optString("raw_name", name)
                val type = o.optString("type", "light")
                val state = o.optString("state", "off")
                list.add(SmartDevice(id, name, raw, type, state))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun toggleSmartDevice(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().put("deviceId", deviceId).toString()
            val body = payload.toRequestBody(jsonMediaType)
            val req = Request.Builder().url("$baseUrl/api/smartthings/toggle").post(body).build()
            val resp = httpClient.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun sendGeminiCommand(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().put("prompt", prompt).toString()
            val body = payload.toRequestBody(jsonMediaType)
            val req = Request.Builder().url("$baseUrl/api/command").post(body).build()
            val resp = httpClient.newCall(req).execute()
            val out = resp.body?.string() ?: ""
            val json = JSONObject(out)
            json.optString("reply", json.optString("response", out))
        } catch (e: Exception) {
            "Fehler beim Senden an Gemini: ${e.message}"
        }
    }

    suspend fun sendWakeOnLan(macTarget: String = ""): Boolean = withContext(Dispatchers.IO) {
        val mac = if (macTarget.isNotEmpty()) macTarget else prefs.tvMac
        if (mac.isBlank()) return@withContext false

        try {
            val cleanMac = mac.replace(":", "").replace("-", "")
            if (cleanMac.length != 12) return@withContext false

            val macBytes = ByteArray(6)
            for (i in 0 until 6) {
                macBytes[i] = cleanMac.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }

            val packetBytes = ByteArray(102)
            // 6 bytes of 0xFF
            for (i in 0 until 6) {
                packetBytes[i] = 0xFF.toByte()
            }
            // 16 repetitions of MAC
            for (i in 0 until 16) {
                System.arraycopy(macBytes, 0, packetBytes, 6 + i * 6, 6)
            }

            val socket = DatagramSocket()
            socket.broadcast = true

            // Send to standard broadcast ports 9 and 7
            val targetIps = listOf("255.255.255.255", "192.168.178.255")
            for (ip in targetIps) {
                try {
                    val addr = InetAddress.getByName(ip)
                    socket.send(DatagramPacket(packetBytes, packetBytes.size, addr, 9))
                    socket.send(DatagramPacket(packetBytes, packetBytes.size, addr, 7))
                } catch (e: Exception) {
                    // Ignore individual destination failure
                }
            }
            socket.close()

            // Also send screen_on quick action in case TV was in active standby / screen off
            quickAction("screen_on")
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun formatAppTitle(appId: String): String {
        return when {
            appId.contains("hdmi1") -> "PC (HDMI 1)"
            appId.contains("hdmi2") -> "HDMI 2"
            appId.contains("custom.launcher") -> "Modern LG Hub"
            appId.contains("netflix") -> "Netflix"
            appId.contains("youtube") -> "YouTube"
            appId.contains("spotify") -> "Spotify"
            appId.contains("amazon") -> "Prime Video"
            appId.contains("disney") -> "Disney+"
            appId.contains("apple") -> "Apple TV"
            appId.contains("moonlight") -> "Moonlight"
            appId.contains("browser") -> "Web Browser"
            appId.isEmpty() -> "Standby / Aus"
            else -> appId.substringAfterLast(".")
        }
    }
}
