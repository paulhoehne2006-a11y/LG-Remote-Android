package com.modernlghub.remote.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebOsPointerClient(private val context: Context) {
    private val prefs = TvPreferences(context)
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var ssapSocket: WebSocket? = null
    private var pointerSocket: WebSocket? = null
    private var isConnected = false
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun connect() {
        if (isConnected) return
        val url = "ws://${prefs.tvIp}:3000/"
        val request = Request.Builder().url(url).build()

        ssapSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Register / authenticate handshake
                val registerMsg = JSONObject().apply {
                    put("type", "register")
                    put("id", "register_0")
                    put("payload", JSONObject().apply {
                        put("forcePairing", false)
                        put("pairingType", "PROMPT")
                        put("client-key", "")
                        put("manifest", JSONObject().apply {
                            put("manifestVersion", 1)
                            put("appVersion", "1.0")
                            put("signed", JSONObject().apply {
                                put("created", "20261112")
                                put("appId", "com.modernlghub.remote")
                                put("vendorId", "modernlghub")
                                put("localizedAppNames", JSONObject().put("", "Modern LG Remote"))
                                put("permissions", org.json.JSONArray().apply {
                                    put("CONTROL_INPUT_TEXT")
                                    put("CONTROL_MOUSE_AND_KEYBOARD")
                                    put("READ_RUNNING_APPS")
                                })
                            })
                        })
                    })
                }
                webSocket.send(registerMsg.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type")
                    if (type == "registered") {
                        // Request pointer input socket
                        val getPointerMsg = JSONObject().apply {
                            put("type", "request")
                            put("id", "req_pointer")
                            put("uri", "ssap://com.webos.service.networkinput/getPointerInputSocket")
                        }
                        webSocket.send(getPointerMsg.toString())
                    } else if (type == "response") {
                        val payload = json.optJSONObject("payload")
                        val socketPath = payload?.optString("socketPath")
                        if (!socketPath.isNullOrEmpty()) {
                            connectPointerSocket(socketPath)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore parse errors
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                disconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                disconnect()
            }
        })
    }

    private fun connectPointerSocket(socketPath: String) {
        val req = Request.Builder().url(socketPath).build()
        pointerSocket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
            }
        })
    }

    fun move(dx: Float, dy: Float) {
        if (!isConnected || pointerSocket == null) {
            connect()
            return
        }
        val msg = "type:move\ndx:$dx\ndy:$dy\ndown:0\n\n"
        pointerSocket?.send(msg)
    }

    fun click() {
        if (!isConnected || pointerSocket == null) {
            connect()
            return
        }
        val msg = "type:click\n\n"
        pointerSocket?.send(msg)
    }

    fun scroll(dy: Float) {
        if (!isConnected || pointerSocket == null) {
            connect()
            return
        }
        val msg = "type:scroll\ndx:0\ndy:$dy\n\n"
        pointerSocket?.send(msg)
    }

    fun disconnect() {
        isConnected = false
        try {
            pointerSocket?.close(1000, "Normal Closure")
            ssapSocket?.close(1000, "Normal Closure")
        } catch (e: Exception) {
            // Ignore
        }
        pointerSocket = null
        ssapSocket = null
    }
}
