package com.modernlghub.remote.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Magic-Remote pointer over webOS SSAP (the same protocol LG's ThinQ app uses).
 *
 * Flow: wss://tv:3001 (falls back to ws://tv:3000) → register (pairing prompt on the TV once,
 * client-key is stored) → getPointerInputSocket → stream move/click/scroll messages.
 *
 * Lifecycle: [connect] / [disconnect] are called from the UI thread; socket callbacks arrive on
 * OkHttp threads. Each connection attempt is a *session* with its own id – callbacks from an
 * older session (a socket still closing after [disconnect], a failed attempt) are ignored, so
 * they can neither flip the state of a newer connection nor reconnect after the user left.
 *
 * Recovery: a stale client key (TV reset) is dropped and registration retried once without it
 * (the TV shows the pairing prompt again). If the pointer socket drops after it was ready, the
 * client reconnects automatically up to [MAX_AUTO_RETRIES] times.
 */
class WebOsPointerClient(context: Context) {

    enum class State { IDLE, CONNECTING, PAIRING, READY, FAILED }

    private val appContext = context.applicationContext
    private val prefs = TvPreferences(appContext)
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val lock = Any()
    private var session = 0
    private var control: WebSocket? = null
    @Volatile private var pointer: WebSocket? = null
    private var autoRetries = 0

    // Pending sub-pixel movement, flushed as integers (webOS ignores fractions). UI thread only.
    private var pendingDx = 0f
    private var pendingDy = 0f

    private val client: OkHttpClient by lazy { buildLocalTvClient() }

    fun connect() {
        val s = _state.value
        if (s == State.READY || s == State.CONNECTING || s == State.PAIRING) return
        autoRetries = 0
        start()
    }

    private fun start() {
        val id = synchronized(lock) {
            closeSockets()
            ++session
        }
        pendingDx = 0f
        pendingDy = 0f
        open(id, secure = true, withKey = true)
    }

    private fun isCurrent(id: Int) = synchronized(lock) { id == session }

    /** Sets [state] only if [id] is still the current session. */
    private fun setState(id: Int, state: State) = synchronized(lock) {
        if (id == session) _state.value = state
    }

    private fun open(id: Int, secure: Boolean, withKey: Boolean) {
        val host = prefs.tvHost
        if (host.isBlank()) {
            setState(id, State.FAILED)
            return
        }
        setState(id, State.CONNECTING)
        val url = if (secure) "wss://${hostForUrl(host)}:3001/" else "ws://${hostForUrl(host)}:3000/"
        val request = runCatching { Request.Builder().url(url).build() }.getOrElse {
            setState(id, State.FAILED)
            return
        }
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!isCurrent(id)) return webSocket.cancel()
                webSocket.send(registerMessage(withKey).toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isCurrent(id)) return
                val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
                val payload = msg.optJSONObject("payload")
                when (msg.str("type")) {
                    "response" -> {
                        if (payload?.str("pairingType") == "PROMPT") setState(id, State.PAIRING)
                        payload?.str("socketPath")?.takeIf { it.isNotBlank() }?.let { openPointer(id, it) }
                    }
                    "registered" -> {
                        payload?.str("client-key")?.takeIf { it.isNotBlank() }?.let { prefs.ssapClientKey = it }
                        webSocket.send(
                            JSONObject()
                                .put("type", "request")
                                .put("id", "pointer_1")
                                .put("uri", "ssap://com.webos.service.networkinput/getPointerInputSocket")
                                .toString()
                        )
                    }
                    "error" -> {
                        val usedKey = withKey && prefs.ssapClientKey.isNotBlank()
                        if (msg.str("id") == "register_0" && usedKey) {
                            // A stale client key (e.g. TV reset): forget it and pair again once.
                            prefs.ssapClientKey = ""
                            webSocket.send(registerMessage(withKey = false).toString())
                        } else {
                            setState(id, State.FAILED)
                        }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!isCurrent(id)) return
                // Older webOS without TLS on 3001: try plain 3000 once, still in this session.
                if (secure && _state.value == State.CONNECTING) open(id, secure = false, withKey = withKey)
                else setState(id, State.FAILED)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (isCurrent(id)) setState(id, State.FAILED)
            }
        })
        synchronized(lock) {
            if (id == session) control = socket else socket.cancel()
        }
    }

    private fun openPointer(id: Int, path: String) {
        val request = runCatching { Request.Builder().url(path).build() }.getOrElse {
            setState(id, State.FAILED)
            return
        }
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!isCurrent(id)) return webSocket.cancel()
                autoRetries = 0
                setState(id, State.READY)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = lost(id)

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = lost(id)
        })
        synchronized(lock) {
            if (id == session) pointer = socket else socket.cancel()
        }
    }

    /** Pointer socket gone: reconnect a few times if it had been working, else report failure. */
    private fun lost(id: Int) {
        if (!isCurrent(id)) return
        val wasReady = _state.value == State.READY
        if (wasReady && autoRetries < MAX_AUTO_RETRIES) {
            autoRetries++
            setState(id, State.CONNECTING)
            main.postDelayed({ if (isCurrent(id)) start() }, RETRY_DELAY_MS * autoRetries)
        } else {
            setState(id, State.FAILED)
        }
    }

    private fun registerMessage(withKey: Boolean): JSONObject {
        val payload = runCatching {
            JSONObject(appContext.assets.open("webos_pairing.json").bufferedReader().use { it.readText() })
        }.getOrDefault(JSONObject().put("pairingType", "PROMPT"))
        val key = prefs.ssapClientKey
        if (withKey && key.isNotBlank()) payload.put("client-key", key) else payload.remove("client-key")
        return JSONObject().put("type", "register").put("id", "register_0").put("payload", payload)
    }

    fun move(dx: Float, dy: Float) {
        val socket = pointer ?: return
        if (_state.value != State.READY) return
        pendingDx += dx
        pendingDy += dy
        val ix = pendingDx.toInt()
        val iy = pendingDy.toInt()
        if (ix == 0 && iy == 0) return
        pendingDx -= ix
        pendingDy -= iy
        socket.send("type:move\ndx:$ix\ndy:$iy\ndown:0\n\n")
    }

    fun click() {
        if (_state.value == State.READY) pointer?.send("type:click\n\n")
    }

    fun scroll(dy: Float) {
        if (_state.value != State.READY) return
        val step = dy.toInt()
        if (step != 0) pointer?.send(String.format(Locale.US, "type:scroll\ndx:0\ndy:%d\n\n", step))
    }

    fun disconnect() {
        synchronized(lock) {
            session++ // invalidates every callback of the current connection
            closeSockets()
            _state.value = State.IDLE
        }
        main.removeCallbacksAndMessages(null)
    }

    /** Caller holds [lock]. */
    private fun closeSockets() {
        runCatching { pointer?.close(1000, null) }
        runCatching { control?.close(1000, null) }
        pointer = null
        control = null
    }

    /**
     * webOS serves a self-signed certificate on :3001, so certificate checks are relaxed for this
     * client – and only towards the configured TV host or a private LAN address (the pointer
     * socketPath names the TV by IP even if the hub is configured by name); any other host, e.g. a
     * socketPath pointing to the internet, fails the hostname check.
     */
    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private fun buildLocalTvClient(): OkHttpClient {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }
        return OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trustAll)
            .hostnameVerifier { hostname, _ -> hostname.equals(prefs.tvHost, ignoreCase = true) || Ipv4.isPrivate(hostname) }
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    companion object {
        private const val MAX_AUTO_RETRIES = 2
        private const val RETRY_DELAY_MS = 1_000L

        private fun hostForUrl(host: String) = if (':' in host) "[$host]" else host
    }
}
