package com.modernlghub.remote.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Thin HTTP layer for the Modern-LG-Hub backend (`gemini-tv-controller/server.py`, port 8080).
 * Every call has its own deadline: remote keys must fail fast, Gemini may take a while.
 */
class HubApi(private val baseUrl: () -> String) {

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(4, 30, TimeUnit.SECONDS))
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    /** GET [path]; returns the body or null on any failure. */
    suspend fun get(path: String, timeoutMs: Long = TIMEOUT_DEFAULT, base: String = baseUrl()): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val call = client.newCall(Request.Builder().url(base + path).get().build())
                call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
                call.execute().use { resp -> if (resp.isSuccessful) resp.body?.string() else null }
            }.getOrNull()
        }

    /** POST a JSON body; returns the parsed JSON reply, or null if the hub did not answer with 2xx. */
    suspend fun post(path: String, body: JSONObject = JSONObject(), timeoutMs: Long = TIMEOUT_DEFAULT): JSONObject? =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url(baseUrl() + path)
                    .post(body.toString().toRequestBody(json))
                    .build()
                val call = client.newCall(req)
                call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
                call.execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val text = resp.body?.string().orEmpty()
                    if (text.isBlank()) JSONObject() else runCatching { JSONObject(text) }.getOrDefault(JSONObject())
                }
            }.getOrNull()
        }

    /** Raw POST that hands back the body string (used for the assistant). */
    suspend fun postRaw(path: String, body: JSONObject, timeoutMs: Long): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(baseUrl() + path).post(body.toString().toRequestBody(json)).build()
                val call = client.newCall(req)
                call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
                call.execute().use { resp -> if (resp.isSuccessful) resp.body?.string() else null }
            }.getOrNull()
        }

    fun iconUrl(appId: String): String = "${baseUrl()}/api/icon/$appId"

    companion object {
        const val TIMEOUT_KEY = 1500L
        const val TIMEOUT_DEFAULT = 4000L
        const val TIMEOUT_STATUS = 5000L
        const val TIMEOUT_SMARTTHINGS = 12_000L
        const val TIMEOUT_ASSISTANT = 60_000L

        /** Accepts "192.168.1.5", "192.168.1.5:8080" or a full URL and returns a base URL. */
        fun normalizeBaseUrl(input: String): String {
            var s = input.trim().removeSuffix("/")
            if (s.isEmpty()) return ""
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
            val hostPart = s.substringAfter("://")
            return if (hostPart.contains(":")) s else "$s:$DEFAULT_PORT"
        }

        const val DEFAULT_PORT = 8080
    }
}
