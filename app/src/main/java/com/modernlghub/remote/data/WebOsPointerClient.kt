package com.modernlghub.remote.data

import android.annotation.SuppressLint
import android.content.Context
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
 */
class WebOsPointerClient(context: Context) {

    enum class State { IDLE, CONNECTING, PAIRING, READY, FAILED }

    private val appContext = context.applicationContext
    private val prefs = TvPreferences(appContext)

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private var control: WebSocket? = null
    private var pointer: WebSocket? = null
    private var triedInsecure = false

    // Pending sub-pixel movement, flushed as integers (webOS ignores fractions).
    private var pendingDx = 0f
    private var pendingDy = 0f

    private val client: OkHttpClient by lazy { buildLocalTvClient() }

    fun connect() {
        if (_state.value == State.READY || _state.value == State.CONNECTING || _state.value == State.PAIRING) return
        triedInsecure = false
        open(secure = true)
    }

    private fun open(secure: Boolean) {
        val host = prefs.tvHost
        if (host.isBlank()) {
            _state.value = State.FAILED
            return
        }
        _state.value = State.CONNECTING
        val url = if (secure) "wss://$host:3001/" else "ws://$host:3000/"
        control = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(registerMessage().toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
                val payload = msg.optJSONObject("payload")
                when (msg.optString("type")) {
                    "response" -> {
                        if (payload?.optString("pairingType") == "PROMPT") _state.value = State.PAIRING
                        payload?.optString("socketPath")?.takeIf { it.isNotBlank() }?.let { openPointer(it) }
                    }
                    "registered" -> {
                        payload?.optString("client-key")?.takeIf { it.isNotBlank() }?.let { prefs.ssapClientKey = it }
                        webSocket.send(
                            JSONObject()
                                .put("type", "request")
                                .put("id", "pointer_1")
                                .put("uri", "ssap://com.webos.service.networkinput/getPointerInputSocket")
                                .toString()
                        )
                    }
                    "error" -> {
                        // A stale client key (e.g. TV reset) → forget it so the next attempt re-pairs.
                        if (msg.optString("id") == "register_0") prefs.ssapClientKey = ""
                        _state.value = State.FAILED
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (secure && !triedInsecure) {
                    triedInsecure = true
                    open(secure = false)
                } else {
                    _state.value = State.FAILED
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (_state.value != State.IDLE) _state.value = State.FAILED
            }
        })
    }

    private fun openPointer(path: String) {
        pointer = client.newWebSocket(Request.Builder().url(path).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _state.value = State.READY
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _state.value = State.FAILED
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (_state.value == State.READY) _state.value = State.FAILED
            }
        })
    }

    private fun registerMessage(): JSONObject {
        val payload = runCatching {
            JSONObject(appContext.assets.open("webos_pairing.json").bufferedReader().use { it.readText() })
        }.getOrDefault(JSONObject().put("pairingType", "PROMPT"))
        val key = prefs.ssapClientKey
        if (key.isNotBlank()) payload.put("client-key", key)
        return JSONObject().put("type", "register").put("id", "register_0").put("payload", payload)
    }

    fun move(dx: Float, dy: Float) {
        val socket = pointer ?: return
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
        pointer?.send("type:click\n\n")
    }

    fun scroll(dy: Float) {
        pointer?.send(String.format(Locale.US, "type:scroll\ndx:0\ndy:%d\n\n", dy.toInt()))
    }

    fun disconnect() {
        _state.value = State.IDLE
        runCatching { pointer?.close(1000, null) }
        runCatching { control?.close(1000, null) }
        pointer = null
        control = null
    }

    /**
     * webOS serves a self-signed certificate on :3001. This client is only ever used for the
     * TV's LAN address, so certificate checks are relaxed for it alone.
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
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }
}
