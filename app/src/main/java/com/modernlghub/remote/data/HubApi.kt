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

/** Outcome of a hub call. "Rejected" means the hub answered, but with an error status. */
sealed interface HubResult {
    data class Ok(val body: String) : HubResult {
        fun json(): JSONObject = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
    }

    /** 4xx/5xx from the hub, e.g. 401 (token), 400 (invalid input), 502 (TV refused the command). */
    data class Rejected(val code: Int, val message: String) : HubResult

    data object Unreachable : HubResult

    val isOk: Boolean get() = this is Ok
}

/**
 * Thin HTTP layer for the Modern-LG-Hub backend (`gemini-tv-controller/server.py`, port 8080).
 * Every call has its own deadline: remote keys must fail fast, Gemini may take a while.
 * If a hub token is configured (server env GEMINI_TOKEN), it is sent as `X-Hub-Token`.
 */
class HubApi(private val baseUrl: () -> String, private val token: () -> String = { "" }) {

    /** Shared with Coil, so app icons also carry the token. */
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(4, 30, TimeUnit.SECONDS))
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .addInterceptor { chain ->
            val t = token()
            val req = chain.request()
            chain.proceed(if (t.isBlank()) req else req.newBuilder().header(TOKEN_HEADER, t).build())
        }
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    suspend fun get(path: String, timeoutMs: Long = TIMEOUT_DEFAULT, base: String = baseUrl()): HubResult =
        execute(Request.Builder().url(base + path).get(), timeoutMs)

    suspend fun post(path: String, body: JSONObject = JSONObject(), timeoutMs: Long = TIMEOUT_DEFAULT): HubResult =
        execute(Request.Builder().url(baseUrl() + path).post(body.toString().toRequestBody(json)), timeoutMs)

    private suspend fun execute(builder: Request.Builder, timeoutMs: Long): HubResult = withContext(Dispatchers.IO) {
        val request = runCatching { builder.build() }.getOrElse { return@withContext HubResult.Unreachable }
        runCatching {
            val call = client.newCall(request)
            call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
            call.execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.isSuccessful) HubResult.Ok(text) else HubResult.Rejected(resp.code, errorMessage(text, resp.code))
            }
        }.getOrDefault(HubResult.Unreachable)
    }

    fun iconUrl(appId: String): String = "${baseUrl()}/api/icon/$appId"

    companion object {
        const val TOKEN_HEADER = "X-Hub-Token"
        const val TIMEOUT_KEY = 1500L
        const val TIMEOUT_DEFAULT = 4000L
        const val TIMEOUT_STATUS = 5000L
        const val TIMEOUT_SMARTTHINGS = 12_000L
        const val TIMEOUT_ASSISTANT = 60_000L
        const val DEFAULT_PORT = 8080

        /** The hub answers errors as {"error": "..."} (or {"text": "..."} for the assistant). */
        fun errorMessage(body: String, code: Int): String {
            val o = runCatching { JSONObject(body) }.getOrNull()
            return o?.optString("error")?.takeIf { it.isNotBlank() }
                ?: o?.optString("text")?.takeIf { it.isNotBlank() }
                ?: "HTTP $code"
        }

        /** Accepts "192.168.1.5", "192.168.1.5:8080" or a full URL and returns a base URL. */
        fun normalizeBaseUrl(input: String): String {
            var s = input.trim().removeSuffix("/")
            if (s.isEmpty()) return ""
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
            val hostPart = s.substringAfter("://")
            return if (hostPart.contains(":")) s else "$s:$DEFAULT_PORT"
        }
    }
}
