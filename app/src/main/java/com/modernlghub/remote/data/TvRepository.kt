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
    val api = HubApi(baseUrl = { prefs.baseUrl }, token = { prefs.hubToken })
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
                command("/api/remote/key", JSONObject().put("key", key).put("fast", true), HubApi.TIMEOUT_KEY)
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

    /** Fetch status (and the app list if stale). Returns true if the hub answered with a status. */
    suspend fun refresh(): Boolean {
        val result = api.get("/api/status", HubApi.TIMEOUT_STATUS)
        if (result is HubResult.Rejected && result.code == 401) {
            // Hub is there but wants (another) X-Hub-Token: no point in counting this as "off".
            failedPolls = 0
            _state.update { it.copy(connection = Connection.UNAUTHORIZED, waking = false) }
            return false
        }
        val status = (result as? HubResult.Ok)?.let { runCatching { TvStatus.parse(it.body) }.getOrNull() }
        if (status == null) {
            failedPolls++
            // One dropped poll on Wi-Fi is normal; only flip to offline after two.
            if (failedPolls >= 2 || _state.value.connection != Connection.ONLINE) {
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
        val body = (api.get("/api/apps") as? HubResult.Ok)?.body ?: return
        val apps = runCatching { LauncherApp.parseList(body) }.getOrNull() ?: return
        if (apps.isNotEmpty()) {
            lastAppsLoad = System.currentTimeMillis()
            _state.update { it.copy(apps = apps) }
        }
    }

    /** Call after the hub address or token changed. */
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

    /** At most one snackbar every few seconds, so a burst of failing keys doesn't spam. */
    private suspend fun notify(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastFailureMessage > 4000) {
            lastFailureMessage = now
            _messages.emit(message)
        }
    }

    /**
     * POST a command and report problems. Distinguishes "hub not reachable" from
     * "hub answered with an error" (e.g. 502 when the TV refused a quick action).
     */
    private suspend fun command(path: String, body: JSONObject = JSONObject(), timeoutMs: Long = HubApi.TIMEOUT_DEFAULT): HubResult {
        val result = api.post(path, body, timeoutMs)
        when (result) {
            is HubResult.Ok -> Unit
            is HubResult.Unreachable -> if (_state.value.isOnline) {
                failedPolls++
                notify("Hub antwortet nicht")
                refreshSoon(0)
            }
            is HubResult.Rejected -> when (result.code) {
                401 -> {
                    _state.update { it.copy(connection = Connection.UNAUTHORIZED) }
                    notify("Hub verlangt einen gültigen Token (Einstellungen)")
                }
                502 -> notify("Der Fernseher hat den Befehl nicht ausgeführt")
                else -> notify("Hub: ${result.message}")
            }
        }
        return result
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
            val res = command("/api/quick", JSONObject().put("action", action))
            // On a refusal, fetch the real state at once so optimistic changes are rolled back.
            if (!res.isOk) refreshSoon(0) else if (refresh) refreshSoon()
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
            command("/api/launch", JSONObject().put("id", appId))
            refreshSoon(1200)
        }
    }

    fun setPictureMode(mode: PictureMode) {
        scope.launch {
            val res = command("/api/picture/mode", JSONObject().put("mode", mode.key))
            if (res is HubResult.Ok) {
                _messages.emit(
                    if (res.json().optBoolean("success", true)) "Bildmodus: ${mode.label}" else "Bildmodus wurde vom TV abgelehnt"
                )
            }
        }
    }

    fun setSoundOutput(output: SoundOutput) {
        _state.update { it.copy(status = it.status.copy(audio = it.status.audio.copy(soundOutput = output.key))) }
        scope.launch {
            command("/api/audio/output", JSONObject().put("output", output.key))
            refreshSoon()
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
            // TV may already run the hub in active standby: also turn the panel on (silently).
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
        scope.launch { command("/api/remote/text", JSONObject().put("text", text).put("replace", true)) }
    }

    fun sendEnter() {
        scope.launch { command("/api/remote/enter") }
    }

    // ---------------------------------------------------------------- smart home & assistant

    /** Lamps and plugs from the hub's local smart-home control; null if the hub can't be read. */
    suspend fun smartDevices(): List<SmartDevice>? {
        var res = api.get("/api/smarthome/devices", HubApi.TIMEOUT_SMARTTHINGS)
        // Hubs before the local smart-home control only know the old SmartThings path.
        if (res is HubResult.Rejected && res.code == 404) res = api.get("/api/smartthings/devices", HubApi.TIMEOUT_SMARTTHINGS)
        val body = (res as? HubResult.Ok)?.body ?: return null
        return runCatching { SmartDevice.parseList(body) }.getOrNull()
    }

    /** Toggle a lamp or plug; returns the new on-state, or null on failure. */
    suspend fun toggleDevice(id: String): Boolean? {
        val body = JSONObject().put("deviceId", id)
        var res = api.post("/api/smarthome/toggle", body, HubApi.TIMEOUT_SMARTTHINGS)
        if (res is HubResult.Rejected && res.code == 404) res = api.post("/api/smartthings/toggle", body, HubApi.TIMEOUT_SMARTTHINGS)
        val json = smartHomeResult(res) ?: return null
        val state = json.optString("new_state", json.optString("state", ""))
        return if (state.isBlank()) null else state == "on"
    }

    /**
     * Switch a lamp and optionally set its brightness (10..100), an RGB colour (`#rrggbb`)
     * or a white tone in kelvin. Returns true when the hub confirmed it.
     */
    suspend fun setDevice(
        id: String,
        on: Boolean,
        brightness: Int? = null,
        color: String? = null,
        kelvin: Int? = null,
    ): Boolean {
        val body = JSONObject().put("deviceId", id).put("state", if (on) "on" else "off")
        brightness?.let { body.put("brightness", it.coerceIn(10, 100)) }
        color?.let { body.put("color", it) }
        kelvin?.let { body.put("kelvin", it.coerceIn(SmartDevice.KELVIN_MIN, SmartDevice.KELVIN_MAX)) }
        val res = api.post("/api/smarthome/set", body, HubApi.TIMEOUT_SMARTTHINGS)
        if (res is HubResult.Rejected && res.code == 404) {
            notify("Hub zu alt für Helligkeit/Farbe – bitte Hub aktualisieren")
            return false
        }
        return smartHomeResult(res) != null
    }

    /** The hub's JSON answer, or null (with a message) when it failed. */
    private suspend fun smartHomeResult(res: HubResult): JSONObject? {
        if (res !is HubResult.Ok) {
            notify(
                when {
                    res is HubResult.Rejected && res.code == 401 -> "Hub verlangt einen gültigen Token (Einstellungen)"
                    res is HubResult.Rejected -> "Zuhause: ${res.message}"
                    else -> "Hub antwortet nicht"
                }
            )
            return null
        }
        val json = runCatching { res.json() }.getOrNull()
        if (json == null) {
            notify("Zuhause: unerwartete Antwort")
            return null
        }
        if (json.optString("status") == "error") {
            notify("Zuhause: ${json.optString("error", "Fehler")}")
            return null
        }
        return json
    }

    /**
     * Ask Gemini via the hub. [history] are earlier turns of this conversation, so the hub can
     * answer follow-up questions ("und jetzt leiser").
     */
    suspend fun ask(prompt: String, history: List<AssistantTurn> = emptyList()): AssistantReply {
        val body = JSONObject()
            .put("prompt", prompt)
            .put("history", AssistantTurn.toJson(history.takeLast(MAX_HISTORY_TURNS)))
        val reply = when (val res = api.post("/api/command", body, HubApi.TIMEOUT_ASSISTANT)) {
            is HubResult.Ok -> runCatching { AssistantReply.parse(res.body) }
                .getOrElse { AssistantReply("Antwort konnte nicht gelesen werden.", isError = true) }
            is HubResult.Rejected -> AssistantReply(res.message, isError = true)
            HubResult.Unreachable -> AssistantReply("Der Hub hat nicht geantwortet.", isError = true)
        }
        if (reply.needsKey) _state.update { it.copy(status = it.status.copy(hasGeminiKey = false)) }
        refreshSoon(500)
        return reply
    }

    /** Returns null on success, otherwise a message for the user. */
    suspend fun saveGeminiKey(key: String): String? =
        when (val res = api.post("/api/key", JSONObject().put("api_key", key.trim()))) {
            is HubResult.Ok -> if (res.json().optBoolean("success", true)) null else "Schlüssel wurde nicht gespeichert"
            is HubResult.Rejected -> res.message
            HubResult.Unreachable -> "Hub nicht erreichbar"
        }

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
                    host.takeIf { isHubAnswer(api.get("/api", 900, base = "http://$host:${HubApi.DEFAULT_PORT}")) }
                }
            }
        }.awaitAll()
        results.firstOrNull { it != null }
    }

    // ---------------------------------------------------------------- one-tap actions

    /**
     * Execute a widget / shortcut / routine / notification action and *wait* for the hub,
     * so callers like a BroadcastReceiver (goAsync) or a finishing Activity don't lose it.
     * Returns false if the hub could not be reached or refused. UI actions ([TvAction.opensUi]) are no-ops.
     */
    suspend fun perform(action: TvAction): Boolean {
        suspend fun quickNow(name: String) = api.post("/api/quick", JSONObject().put("action", name)).isOk
        suspend fun launchNow(id: String) = api.post("/api/launch", JSONObject().put("id", id)).isOk

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
            TvAction.AUDIO -> api.post("/api/audio/toggle").isOk
            TvAction.MUTE -> quickNow("mute")
            TvAction.PLAY_PAUSE -> api.post("/api/remote/key", JSONObject().put("key", "PLAY")).isOk
            TvAction.HOME -> quickNow("home")
            TvAction.NETFLIX, TvAction.YOUTUBE, TvAction.SPOTIFY -> launchNow(TvAction.APP_IDS.getValue(action))
            TvAction.REMOTE -> true
        }
        if (ok) delay(600)
        refresh()
        return ok
    }

    companion object {
        @Volatile private var instance: TvRepository? = null

        /** The hub keeps more itself; a short tail is enough for follow-up questions. */
        const val MAX_HISTORY_TURNS = 10

        /** "/api" answered by a Modern-LG-Hub, or 401 because it wants a token (still our hub). */
        fun isHubAnswer(result: HubResult): Boolean = when (result) {
            is HubResult.Ok -> result.body.contains("LG Hub", ignoreCase = true)
            is HubResult.Rejected -> result.code == 401 && result.message.contains("X-Hub-Token")
            HubResult.Unreachable -> false
        }

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
