package com.modernlghub.remote.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import kotlin.coroutines.resume

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
 *
 * Calls are cancellable: cancelling the calling coroutine cancels the HTTP call (and its socket)
 * instead of letting it run into its timeout in the background. No call is retried, so a key
 * press is never sent twice.
 *
 * [socketFactoryFor] may return a factory that binds sockets to a specific network for a given
 * host (used to reach the LAN while a full-tunnel VPN is active); null = default routing.
 */
class HubApi(
    private val baseUrl: () -> String,
    private val token: () -> String = { "" },
    private val socketFactoryFor: (host: String) -> SocketFactory? = { null },
) {

    /**
     * Shared with Coil, so app icons also carry the token.
     *
     * "Connection: close": the hub (Python http.server) answers HTTP/1.0 and closes every
     * connection, but without saying so. OkHttp would pool the socket anyway, and the next request
     * could go out on the already closed connection and fail ("unexpected end of stream") – with
     * retries off, that is a lost key. Asking for close keeps OkHttp from pooling these sockets.
     */
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(4, 30, TimeUnit.SECONDS))
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .addInterceptor { chain ->
            val t = token()
            val builder = chain.request().newBuilder().header("Connection", "close")
            if (t.isNotBlank()) builder.header(TOKEN_HEADER, t)
            chain.proceed(builder.build())
        }
        .build()

    /**
     * Client for API calls: same pool and settings as [client], but its own dispatcher, so
     * remote keys never queue behind Coil's icon downloads (OkHttp allows 5 calls per host).
     */
    private val apiClient: OkHttpClient = client.newBuilder()
        .dispatcher(Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 16 })
        .build()

    /** [apiClient] variants with sockets bound via [socketFactoryFor] (same pool and dispatcher). */
    private val boundClients = ConcurrentHashMap<SocketFactory, OkHttpClient>()

    private val json = "application/json; charset=utf-8".toMediaType()

    suspend fun get(path: String, timeoutMs: Long = TIMEOUT_DEFAULT, base: String = baseUrl()): HubResult =
        execute(base + path, timeoutMs) { get() }

    suspend fun post(path: String, body: JSONObject = JSONObject(), timeoutMs: Long = TIMEOUT_DEFAULT): HubResult =
        execute(baseUrl() + path, timeoutMs) { post(body.toString().toRequestBody(json)) }

    /**
     * Hosts where binding to the Wi-Fi network failed to connect. A VPN that does not allow
     * bypassing (e.g. WireGuard "always on" into the home router) blocks sockets bound past it,
     * while the same host is reachable through the VPN's own routes. Those hosts use default routing.
     */
    private val unboundHosts = ConcurrentHashMap.newKeySet<String>()

    private fun boundClientFor(host: String): OkHttpClient? {
        if (host in unboundHosts) return null
        val factory = runCatching { socketFactoryFor(host) }.getOrNull() ?: return null
        return boundClients.getOrPut(factory) { apiClient.newBuilder().socketFactory(factory).build() }
    }

    /** A failure before the connection stood: nothing was sent, so trying another route cannot double a key. */
    private fun isConnectFailure(e: IOException): Boolean =
        e is java.net.ConnectException || e is java.net.NoRouteToHostException ||
            (e is java.net.SocketException && e !is javax.net.ssl.SSLException) ||
            (e is java.net.SocketTimeoutException && e.message.orEmpty().contains("connect", ignoreCase = true))

    private suspend fun execute(url: String, timeoutMs: Long, method: Request.Builder.() -> Request.Builder): HubResult {
        // A malformed address from the settings (e.g. with a space) must not throw out of here.
        val request = runCatching { Request.Builder().url(url).method().build() }.getOrElse { return HubResult.Unreachable }
        val host = request.url.host
        val bound = boundClientFor(host)
        val first = run(bound ?: apiClient, request, timeoutMs)
        if (bound != null && first is Attempt.ConnectFailed) {
            unboundHosts.add(host)
            return run(apiClient, request, timeoutMs).result
        }
        return first.result
    }

    private sealed class Attempt(val result: HubResult) {
        class Done(result: HubResult) : Attempt(result)
        object ConnectFailed : Attempt(HubResult.Unreachable)
    }

    private suspend fun run(client: OkHttpClient, request: Request, timeoutMs: Long): Attempt {
        val call = runCatching { client.newCall(request) }.getOrElse { return Attempt.Done(HubResult.Unreachable) }
        call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resume(if (isConnectFailure(e)) Attempt.ConnectFailed else Attempt.Done(HubResult.Unreachable))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use { resp ->
                            val text = resp.body?.string().orEmpty()
                            if (resp.isSuccessful) HubResult.Ok(text) else HubResult.Rejected(resp.code, errorMessage(text, resp.code))
                        }
                    }.getOrDefault(HubResult.Unreachable)
                    cont.resume(Attempt.Done(result))
                }
            })
        }
    }

    fun iconUrl(appId: String): String = "${baseUrl()}${HubPaths.ICON}$appId"

    companion object {
        const val TOKEN_HEADER = "X-Hub-Token"
        const val TIMEOUT_KEY = 1500L
        const val TIMEOUT_DEFAULT = 4000L
        const val TIMEOUT_STATUS = 5000L
        /** Local smart-home calls (WiZ/Tapo answer slowly when a device is offline). */
        const val TIMEOUT_SMARTHOME = 12_000L
        @Deprecated("The hub no longer uses SmartThings", ReplaceWith("TIMEOUT_SMARTHOME"))
        const val TIMEOUT_SMARTTHINGS = TIMEOUT_SMARTHOME
        const val TIMEOUT_ASSISTANT = 60_000L
        const val DEFAULT_PORT = 8080

        /** The hub answers errors as {"error": "..."} (or {"text": "..."} for the assistant). */
        fun errorMessage(body: String, code: Int): String {
            val o = parseObjectOrNull(body)
            return o?.str("error")?.trim()?.takeIf { it.isNotBlank() }
                ?: o?.str("text")?.trim()?.takeIf { it.isNotBlank() }
                ?: "HTTP $code"
        }

        /**
         * Accepts "192.168.1.5", "192.168.1.5:8080", "[fe80::1]" or a full URL and returns
         * `scheme://host:port` (any path is dropped, the default port 8080 is added).
         */
        fun normalizeBaseUrl(input: String): String {
            var s = input.trim()
            if (s.isEmpty()) return ""
            val lower = s.lowercase()
            s = when {
                lower.startsWith("http://") || lower.startsWith("https://") -> s
                "://" in s -> return ""
                else -> "http://$s"
            }
            val scheme = s.substringBefore("://").lowercase()
            val hostPort = s.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
            if (hostPort.isEmpty()) return ""
            val hasPort = if (hostPort.startsWith("[")) hostPort.substringAfter(']', "").startsWith(":") else ':' in hostPort
            return if (hasPort) "$scheme://$hostPort" else "$scheme://$hostPort:$DEFAULT_PORT"
        }

        /** Bare host of a base URL from [normalizeBaseUrl] (IPv6 without brackets). */
        fun hostOf(baseUrl: String): String {
            val hostPort = baseUrl.substringAfter("://", "")
            return if (hostPort.startsWith("[")) hostPort.substringAfter('[').substringBefore(']')
            else hostPort.substringBefore(':')
        }
    }
}

/**
 * Every hub endpoint the app uses, in one place (gemini-tv-controller/server.py, _route_get /
 * _route_post). Request bodies: see the callers in [TvRepository].
 */
object HubPaths {
    const val INFO = "/api"                         // GET  {service, version, status, has_gemini_key}
    const val STATUS = "/api/status"                // GET  {has_key, tv: {power, screen_off, current_app, audio, network}}
    const val APPS = "/api/apps"                    // GET  {apps: [...], count}
    const val ICON = "/api/icon/"                   // GET  png, + app id
    const val QUICK = "/api/quick"                  // POST {action}  -> 200 / 502 (TV refused) / 400 (unknown)
    const val REMOTE_KEY = "/api/remote/key"        // POST {key, fast}
    const val REMOTE_TEXT = "/api/remote/text"      // POST {text, replace}
    const val REMOTE_ENTER = "/api/remote/enter"    // POST {}
    const val LAUNCH = "/api/launch"                // POST {id}
    const val AUDIO_OUTPUT = "/api/audio/output"    // POST {output} -> {success}
    const val AUDIO_TOGGLE = "/api/audio/toggle"    // POST {}       -> {success}
    const val PICTURE_MODE = "/api/picture/mode"    // POST {mode}   -> {success}
    const val ASSISTANT = "/api/command"            // POST {prompt, history} -> {text, actions, suggestions?, needs_key?}
    const val GEMINI_KEY = "/api/key"               // POST {api_key} -> {success, has_key}
    const val SMARTHOME_DEVICES = "/api/smarthome/devices"
    const val SMARTHOME_TOGGLE = "/api/smarthome/toggle"
    const val SMARTHOME_SET = "/api/smarthome/set"
    /** Paths of hubs before the local smart-home control (still served as aliases). */
    const val LEGACY_SMARTTHINGS_DEVICES = "/api/smartthings/devices"
    const val LEGACY_SMARTTHINGS_TOGGLE = "/api/smartthings/toggle"
}
