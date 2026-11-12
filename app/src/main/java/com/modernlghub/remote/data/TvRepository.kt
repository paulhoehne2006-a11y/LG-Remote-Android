package com.modernlghub.remote.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetAddress

data class TvState(
    val connection: Connection = Connection.CONNECTING,
    val status: TvStatus = TvStatus(),
    val apps: List<LauncherApp> = emptyList(),
    val waking: Boolean = false,
) {
    val isOnline: Boolean get() = connection == Connection.ONLINE

    /** TV is reachable and the panel is on (or deliberately off for audio only). */
    val isOn: Boolean get() = isOnline && !status.isStandby

    fun appTitle(appId: String = status.currentAppId): String {
        if (appId.isBlank()) return ""
        apps.firstOrNull { it.id == appId }?.let { return it.title }
        return WELL_KNOWN_TITLES.entries.firstOrNull { appId.contains(it.key) }?.value
            ?: appId.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }

    val currentApp: LauncherApp? get() = apps.firstOrNull { it.id == status.currentAppId }

    companion object {
        private val WELL_KNOWN_TITLES = linkedMapOf(
            "hdmi1" to "PC (HDMI 1)",
            "hdmi2" to "HDMI 2",
            "hdmi3" to "HDMI 3",
            "hdmi4" to "HDMI 4",
            "custom.launcher" to "Home",
            "livetv" to "Live TV",
            "netflix" to "Netflix",
            "youtube" to "YouTube",
            "spotify" to "Spotify",
            "amazon" to "Prime Video",
            "disney" to "Disney+",
            "appletv" to "Apple TV",
            "limelight" to "Moonlight",
        )
    }
}

/**
 * Single source of truth for the TV. One instance per process (see [get]).
 *
 * - Status is polled only while some UI is visible (or the live notification is on).
 * - Remote keys go through one ordered queue so fast swipes arrive in order.
 * - State changes are applied optimistically and confirmed by the next poll.
 */
class TvRepository private constructor(private val appContext: Context) {

    val prefs = TvPreferences(appContext)
    val api = HubApi { prefs.baseUrl }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(TvState())
    val state: StateFlow<TvState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Short user-facing messages (shown as snackbar). */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val keyQueue = Channel<String>(capacity = 32)
    private var failedPolls = 0
    private var pollJob: Job? = null
    private val pollClients = mutableSetOf<String>()
    private var lastAppsLoad = 0L

    init {
        scope.launch {
            for (key in keyQueue) {
                val ok = api.post("/api/remote/key", JSONObject().put("key", key).put("fast", true), HubApi.TIMEOUT_KEY)
                if (ok == null) onCommandFailed()
            }
        }
    }

    // ---------------------------------------------------------------- polling

    /** Start polling on behalf of [client]; polling continues while at least one client is registered. */
    @Synchronized
    fun acquirePolling(client: String, intervalMs: Long = 3000L) {
        pollClients += client
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                refresh()
                val online = _state.value.isOnline
                delay(if (online) intervalMs else intervalMs * 2)
            }
        }
    }

    @Synchronized
    fun releasePolling(client: String) {
        pollClients -= client
        if (pollClients.isEmpty()) {
            pollJob?.cancel()
            pollJob = null
        }
    }

    /** Fetch status (and the app list if stale). Returns true if the hub answered. */
    suspend fun refresh(): Boolean {
        val body = api.get("/api/status", HubApi.TIMEOUT_STATUS)
        val status = body?.let { runCatching { TvStatus.parse(it) }.getOrNull() }
        if (status == null) {
            failedPolls++
            // One dropped poll on Wi-Fi is normal; only flip to offline after two.
            if (failedPolls >= 2 || _state.value.connection == Connection.CONNECTING) {
                _state.update { it.copy(connection = Connection.OFFLINE) }
            }
            return false
        }
        failedPolls = 0
        if (status.primaryMac.isNotBlank() && prefs.tvMac.isBlank()) prefs.tvMac = status.primaryMac
        _state.update { it.copy(connection = Connection.ONLINE, status = status, waking = false) }
        if (_state.value.apps.isEmpty() || System.currentTimeMillis() - lastAppsLoad > 10 * 60_000) loadApps()
        return true
    }

    suspend fun loadApps() {
        val body = api.get("/api/apps") ?: return
        val apps = runCatching { LauncherApp.parseList(body) }.getOrNull() ?: return
        if (apps.isNotEmpty()) {
            lastAppsLoad = System.currentTimeMillis()
            _state.update { it.copy(apps = apps) }
        }
    }

    /** Call after the hub address changed. */
    fun resetConnection() {
        failedPolls = 0
        lastAppsLoad = 0
        _state.value = TvState()
        scope.launch { refresh() }
    }

    private fun refreshSoon(delayMs: Long = 700) {
        scope.launch {
            delay(delayMs)
            refresh()
        }
    }

    private var lastFailureMessage = 0L

    private suspend fun onCommandFailed() {
        if (!_state.value.isOnline) return
        failedPolls++
        val now = System.currentTimeMillis()
        if (now - lastFailureMessage > 5000) {
            lastFailureMessage = now
            _messages.emit("Hub antwortet nicht")
        }
        refreshSoon(0)
    }

    // ---------------------------------------------------------------- remote keys

    /** Queue a webOS special key (UP, DOWN, ENTER, BACK, HOME, PLAY, …). Never blocks. */
    fun key(name: String) {
        keyQueue.trySend(name)
    }

    fun volumeUp() = volume(+1)
    fun volumeDown() = volume(-1)

    private fun volume(delta: Int) {
        _state.update {
            val a = it.status.audio
            it.copy(status = it.status.copy(audio = a.copy(volume = (a.volume + delta).coerceIn(0, 100), muted = false)))
        }
        quick(if (delta > 0) "vol_up" else "vol_down", refresh = false)
    }

    fun toggleMute() {
        _state.update {
            val a = it.status.audio
            it.copy(status = it.status.copy(audio = a.copy(muted = !a.muted)))
        }
        quick("mute")
    }

    // ---------------------------------------------------------------- actions

    private fun quick(action: String, refresh: Boolean = true) {
        scope.launch {
            val res = api.post("/api/quick", JSONObject().put("action", action))
            if (res == null) onCommandFailed() else if (refresh) refreshSoon()
        }
    }

    fun toggleScreen() {
        _state.update { it.copy(status = it.status.copy(screenOff = !it.status.screenOff)) }
        quick("screen_toggle")
    }

    fun switchToPc() = launch("com.webos.app.hdmi1")

    fun goHome() = quick("home")

    fun launch(appId: String) {
        _state.update { it.copy(status = it.status.copy(currentAppId = appId, screenOff = false)) }
        scope.launch {
            val res = api.post("/api/launch", JSONObject().put("id", appId))
            if (res == null) onCommandFailed() else refreshSoon(1200)
        }
    }

    fun setPictureMode(mode: PictureMode) {
        scope.launch {
            val res = api.post("/api/picture/mode", JSONObject().put("mode", mode.key))
            when {
                res == null -> onCommandFailed()
                res.optBoolean("success", true) -> _messages.emit("Bildmodus: ${mode.label}")
                else -> _messages.emit("Bildmodus wurde vom TV abgelehnt")
            }
        }
    }

    fun setSoundOutput(output: SoundOutput) {
        _state.update { it.copy(status = it.status.copy(audio = it.status.audio.copy(soundOutput = output.key))) }
        scope.launch {
            val res = api.post("/api/audio/output", JSONObject().put("output", output.key))
            if (res == null) onCommandFailed() else refreshSoon()
        }
    }

    fun toggleSoundOutput() {
        val next = if (_state.value.status.audio.isOptical) SoundOutput.TV else SoundOutput.DENON
        setSoundOutput(next)
    }

    /** Power button semantics: wake when off/standby, otherwise switch off. */
    fun togglePower() {
        if (_state.value.isOn) powerOff() else wake()
    }

    fun powerOff() {
        _state.update { it.copy(status = it.status.copy(power = "Power Off")) }
        quick("power_off")
    }

    fun wake() {
        scope.launch {
            _state.update { it.copy(waking = true) }
            val sent = sendWakeOnLan()
            if (!sent) {
                _state.update { it.copy(waking = false) }
                _messages.emit("Keine MAC-Adresse bekannt – bitte in den Einstellungen eintragen")
                return@launch
            }
            // TV may already run the hub in active standby: also turn the panel on.
            api.post("/api/quick", JSONObject().put("action", "screen_on"), HubApi.TIMEOUT_KEY)
            // Poll faster for ~25 s so the UI flips to "on" as soon as the hub is up.
            repeat(12) {
                delay(2000)
                if (refresh() && !_state.value.status.isStandby) return@launch
            }
            _state.update { it.copy(waking = false) }
        }
    }

    // ---------------------------------------------------------------- text input

    fun sendText(text: String) {
        scope.launch { api.post("/api/remote/text", JSONObject().put("text", text).put("replace", true)) }
    }

    fun sendEnter() {
        scope.launch { api.post("/api/remote/enter") }
    }

    // ---------------------------------------------------------------- smart home & assistant

    suspend fun smartDevices(): List<SmartDevice>? {
        val body = api.get("/api/smartthings/devices", HubApi.TIMEOUT_SMARTTHINGS) ?: return null
        return runCatching { SmartDevice.parseList(body) }.getOrNull()
    }

    /** Toggle a SmartThings switch; returns the new on-state, or null on failure. */
    suspend fun toggleDevice(id: String): Boolean? {
        val res = api.post("/api/smartthings/toggle", JSONObject().put("deviceId", id), HubApi.TIMEOUT_SMARTTHINGS)
            ?: return null
        val state = res.optString("new_state", res.optString("state", ""))
        return if (state.isBlank()) null else state == "on"
    }

    suspend fun ask(prompt: String): AssistantReply {
        val body = api.postRaw("/api/command", JSONObject().put("prompt", prompt), HubApi.TIMEOUT_ASSISTANT)
            ?: return AssistantReply("Der Hub hat nicht geantwortet.", isError = true)
        refreshSoon(500)
        return runCatching { AssistantReply.parse(body) }
            .getOrElse { AssistantReply("Antwort konnte nicht gelesen werden.", isError = true) }
    }

    suspend fun saveGeminiKey(key: String): Boolean =
        api.post("/api/key", JSONObject().put("api_key", key.trim())) != null

    // ---------------------------------------------------------------- Wake-on-LAN & discovery

    suspend fun sendWakeOnLan(mac: String = prefs.tvMac): Boolean = withContext(Dispatchers.IO) {
        val packet = WakeOnLan.magicPacket(mac) ?: return@withContext false
        val targets = buildSet {
            add("255.255.255.255")
            localIpv4()?.let { add(WakeOnLan.broadcastAddress(it.address.hostAddress ?: "", it.prefixLength)) }
            prefs.tvHost.takeIf { it.isNotBlank() }?.let { add(it) }
        }.filter { it.isNotBlank() }
        WakeOnLan.send(packet, targets)
    }

    private fun localIpv4(): LinkAddress? {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return null
        val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return props.linkAddresses.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
    }

    /**
     * Scan the local /24 for a Modern-LG-Hub on port 8080.
     * Returns the host (IP) or null. Takes ~2 s on a typical home network.
     */
    suspend fun discoverHub(): String? = withContext(Dispatchers.IO) {
        val own = localIpv4()?.address?.hostAddress ?: return@withContext null
        val prefix = own.substringBeforeLast('.')
        val gate = Semaphore(48)
        val candidates = (1..254).map { "$prefix.$it" }.filter { it != own }
        // Try the remembered address first.
        val ordered = listOf(prefs.tvHost).filter { it in candidates } + candidates
        val results = ordered.map { host ->
            async {
                gate.withPermit {
                    val body = api.get("/api", 900, base = "http://$host:${HubApi.DEFAULT_PORT}")
                    host.takeIf { body != null && body.contains("LG Hub", ignoreCase = true) }
                }
            }
        }.awaitAll()
        results.firstOrNull { it != null }
    }

    // ---------------------------------------------------------------- one-tap actions

    /**
     * Execute a widget / shortcut / routine / notification action and *wait* for the hub,
     * so callers like a BroadcastReceiver (goAsync) or a finishing Activity don't lose it.
     * Returns false if the hub could not be reached. UI actions ([TvAction.opensUi]) are no-ops.
     */
    suspend fun perform(action: TvAction): Boolean {
        suspend fun quickNow(name: String) = api.post("/api/quick", JSONObject().put("action", name)) != null
        suspend fun launchNow(id: String) = api.post("/api/launch", JSONObject().put("id", id)) != null

        val ok = when (action) {
            TvAction.POWER -> {
                // No live state here (widget, routine): ask the hub first.
                if (refresh() && !_state.value.status.isStandby) {
                    quickNow("power_off")
                } else {
                    val sent = sendWakeOnLan()
                    api.post("/api/quick", JSONObject().put("action", "screen_on"), HubApi.TIMEOUT_KEY)
                    sent
                }
            }
            TvAction.SCREEN -> quickNow("screen_toggle")
            TvAction.PC -> launchNow("com.webos.app.hdmi1")
            TvAction.AUDIO -> api.post("/api/audio/toggle") != null
            TvAction.MUTE -> quickNow("mute")
            TvAction.PLAY_PAUSE -> api.post("/api/remote/key", JSONObject().put("key", "PLAY")) != null
            TvAction.HOME -> quickNow("home")
            TvAction.NETFLIX, TvAction.YOUTUBE, TvAction.SPOTIFY -> launchNow(TvAction.APP_IDS.getValue(action))
            TvAction.REMOTE -> true
        }
        if (ok) {
            delay(600)
            refresh()
        }
        return ok
    }

    companion object {
        @Volatile private var instance: TvRepository? = null

        fun get(context: Context): TvRepository =
            instance ?: synchronized(this) {
                instance ?: TvRepository(context.applicationContext).also { instance = it }
            }
    }
}

/** Builds and sends Wake-on-LAN magic packets. Pure functions are unit-tested. */
object WakeOnLan {
    fun magicPacket(mac: String): ByteArray? {
        val hex = mac.filter { it.isLetterOrDigit() }
        if (hex.length != 12 || !hex.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) return null
        val macBytes = ByteArray(6) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        return ByteArray(6 + 16 * 6).also { p ->
            for (i in 0 until 6) p[i] = 0xFF.toByte()
            for (r in 0 until 16) System.arraycopy(macBytes, 0, p, 6 + r * 6, 6)
        }
    }

    fun broadcastAddress(ip: String, prefixLength: Int): String {
        val parts = ip.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || prefixLength !in 0..32) return ""
        val addr = parts.fold(0L) { acc, b -> (acc shl 8) or b.toLong() }
        val mask = if (prefixLength == 0) 0L else (0xFFFFFFFFL shl (32 - prefixLength)) and 0xFFFFFFFFL
        val bc = (addr and mask) or (mask.inv() and 0xFFFFFFFFL)
        return (3 downTo 0).joinToString(".") { ((bc shr (it * 8)) and 0xFF).toString() }
    }

    fun send(packet: ByteArray, hosts: List<String>): Boolean {
        var anySent = false
        runCatching {
            java.net.DatagramSocket().use { socket ->
                socket.broadcast = true
                for (host in hosts) {
                    runCatching {
                        val addr = InetAddress.getByName(host)
                        for (port in intArrayOf(9, 7)) {
                            socket.send(java.net.DatagramPacket(packet, packet.size, addr, port))
                        }
                        anySent = true
                    }
                }
            }
        }
        return anySent
    }
}
