package com.modernlghub.remote.data

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

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
        /** Display name of the TV in notifications, media session and widgets. */
        const val TV_NAME = "LG OLED G4"

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
 * Threading: every public function may be called from any thread. Fire-and-forget actions
 * (`key`, `toggleScreen`, …) return at once and run on [scope]; suspend functions are
 * main-safe. Shared state is either a StateFlow (updated atomically) or atomics/locks.
 *
 * - Status is polled only while someone holds polling ([acquirePolling]): the visible UI
 *   (ModernLgApplication via ProcessLifecycleOwner) or the live notification (Wi-Fi only).
 *   While the hub does not answer, the poll interval backs off ([PollPolicy]).
 * - Concurrent [refresh] calls share one request: a caller waits for a poll that started after
 *   it asked instead of firing another one.
 * - Remote keys go through one ordered, bounded queue ([KeyDispatcher]): no reordering, no
 *   pile-up on a slow network, nothing replayed late.
 * - State changes are applied optimistically and reconciled with polls by [OptimisticState]:
 *   a poll that was already under way can no longer flip the UI back.
 */
class TvRepository private constructor(private val appContext: Context) {

    val prefs = TvPreferences(appContext)

    /** Local networks (Wi-Fi/Ethernet), also while a VPN is the default network. */
    val lan = LanMonitor(appContext)

    val api = HubApi(baseUrl = { prefs.baseUrl }, token = { prefs.hubToken }, socketFactoryFor = lan::socketFactoryFor)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(TvState())
    val state: StateFlow<TvState> = _state.asStateFlow()

    /** True while the phone is on a Wi-Fi/Ethernet network (where the hub can be). */
    val lanAvailable: StateFlow<Boolean> get() = lan.available

    // Never suspends the sender: a slow snackbar must not stall the key queue.
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Short user-facing messages (shown as snackbar). */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val optimistic = OptimisticState()

    /** Bumped by [resetConnection]; results of requests started before are discarded. */
    private val generation = AtomicInteger()
    private val failedPolls = AtomicInteger()

    private val keys = KeyDispatcher(
        scope = scope,
        send = { key -> command(HubPaths.REMOTE_KEY, JSONObject().put("key", key).put("fast", true), HubApi.TIMEOUT_KEY) },
        onSent = { key, result -> if (KeyDispatcher.isVolumeKey(key)) endVolume(result.isOk) },
        onDropped = { key, reason ->
            // Refused at offer() time is handled by volume() itself; a key dropped later was shown
            // optimistically but never reached the TV, so roll back via a poll.
            if (KeyDispatcher.isVolumeKey(key) && reason.afterQueued) endVolume(false)
        },
    )

    // ---------------------------------------------------------------- polling

    private val pollLock = Any()
    private val pollClients = HashMap<String, PollClient>()
    private var pollJob: Job? = null
    private val pollKick = Channel<Unit>(Channel.CONFLATED)

    /** Start polling on behalf of [client]; polling continues while at least one client is registered. */
    fun acquirePolling(client: String, intervalMs: Long = PollPolicy.UI.intervalMs) =
        acquirePolling(client, PollClient(intervalMs, maxOf(PollPolicy.UI.maxOfflineIntervalMs, intervalMs * 2)))

    /**
     * Registers [client] with its own interval/back-off. The loop uses the most demanding client;
     * registering while the loop sleeps (e.g. in a long back-off) triggers a poll right away.
     */
    fun acquirePolling(client: String, config: PollClient) {
        synchronized(pollLock) {
            pollClients[client] = config
            if (pollJob?.isActive == true) {
                pollKick.trySend(Unit)
                return
            }
            pollJob = scope.launch {
                while (isActive) {
                    val ok = refresh()
                    val wait = synchronized(pollLock) {
                        PollPolicy.nextDelay(pollClients.values.toList(), ok || _state.value.isOnline, if (ok) 0 else failedPolls.get())
                    }
                    withTimeoutOrNull(wait) { pollKick.receive() }
                }
            }
        }
    }

    fun releasePolling(client: String) {
        synchronized(pollLock) {
            pollClients -= client
            if (pollClients.isEmpty()) {
                pollJob?.cancel()
                pollJob = null
            }
        }
    }

    private val refreshMutex = Mutex()
    @Volatile private var lastPollTicket = 0L
    @Volatile private var lastPollResult = false

    /**
     * Fetch status (and the app list if stale). Returns true if the hub answered with a status.
     * Calls that overlap share a request: if a poll started after this call was made, its result
     * is reused.
     */
    suspend fun refresh(): Boolean {
        val asked = optimistic.tick()
        return refreshMutex.withLock {
            if (lastPollTicket > asked) lastPollResult else pollStatus().also { lastPollResult = it }
        }
    }

    private suspend fun pollStatus(): Boolean {
        val gen = generation.get()
        val ticket = optimistic.tick()
        lastPollTicket = ticket
        val result = api.get(HubPaths.STATUS, HubApi.TIMEOUT_STATUS)
        if (gen != generation.get()) return false // address/token changed meanwhile

        if (result is HubResult.Rejected && result.code == 401) {
            // Hub is there but wants (another) X-Hub-Token: no point in counting this as "off".
            failedPolls.set(0)
            _state.update { it.copy(connection = Connection.UNAUTHORIZED, waking = false) }
            return false
        }
        val status = (result as? HubResult.Ok)?.let { runCatching { TvStatus.parse(it.body) }.getOrNull() }
        if (status == null) {
            val failures = failedPolls.incrementAndGet()
            // One dropped poll on Wi-Fi is normal; only flip to offline after two.
            _state.update {
                if (failures >= 2 || it.connection != Connection.ONLINE) it.copy(connection = Connection.OFFLINE) else it
            }
            return false
        }
        failedPolls.set(0)
        prefs.learnMacFromHub(status.primaryMac)
        val waking = wakeJob?.isActive == true
        _state.update {
            it.copy(
                connection = Connection.ONLINE,
                status = optimistic.merge(it.status, status, ticket),
                waking = waking && status.isStandby,
            )
        }
        if (appsDue()) scope.launch { loadApps() }
        return true
    }

    private val appsMutex = Mutex()
    private val lastAppsLoad = AtomicLong(0)
    private val lastAppsAttempt = AtomicLong(0)

    private fun appsDue(): Boolean {
        val now = monotonicMillis()
        return if (_state.value.apps.isEmpty()) {
            lastAppsAttempt.get() == 0L || now - lastAppsAttempt.get() > APPS_RETRY_MS
        } else {
            now - lastAppsLoad.get() > APPS_MAX_AGE_MS
        }
    }

    /** Loads the launcher's app list. Concurrent calls collapse into one request. */
    suspend fun loadApps() {
        if (!appsMutex.tryLock()) return
        try {
            val gen = generation.get()
            lastAppsAttempt.set(monotonicMillis())
            val body = (api.get(HubPaths.APPS) as? HubResult.Ok)?.body ?: return
            val apps = runCatching { LauncherApp.parseList(body) }.getOrNull() ?: return
            if (apps.isNotEmpty() && gen == generation.get()) {
                lastAppsLoad.set(monotonicMillis())
                _state.update { it.copy(apps = apps) }
            }
        } finally {
            appsMutex.unlock()
        }
    }

    /** Call after the hub address or token changed. */
    fun resetConnection() {
        generation.incrementAndGet()
        keys.cancelPending()
        optimistic.clear()
        failedPolls.set(0)
        lastAppsLoad.set(0)
        lastAppsAttempt.set(0)
        _state.value = TvState()
        scope.launch { refresh() }
    }

    private fun refreshSoon(delayMs: Long = 700) {
        scope.launch {
            delay(delayMs)
            refresh()
        }
    }

    private val lastFailureMessage = AtomicLong(Long.MIN_VALUE / 2)

    /** At most one snackbar every few seconds, so a burst of failing keys doesn't spam. */
    private fun notify(message: String) {
        val now = monotonicMillis()
        val last = lastFailureMessage.get()
        if (now - last > 4000 && lastFailureMessage.compareAndSet(last, now)) {
            _messages.tryEmit(message)
        }
    }

    /**
     * POST a command and report problems. Distinguishes "hub not reachable" from
     * "hub answered with an error" (e.g. 502 when the TV refused a quick action).
     */
    private suspend fun command(path: String, body: JSONObject = JSONObject(), timeoutMs: Long = HubApi.TIMEOUT_DEFAULT): HubResult {
        val gen = generation.get()
        val result = api.post(path, body, timeoutMs)
        if (gen != generation.get()) return result
        when (result) {
            is HubResult.Ok -> Unit
            is HubResult.Unreachable -> if (_state.value.isOnline) {
                failedPolls.incrementAndGet()
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

    /**
     * Queue a webOS special key (UP, DOWN, ENTER, BACK, HOME, PLAY, …). Never blocks.
     * Repeats that the network can't keep up with are dropped (see [KeyDispatcher]).
     */
    fun key(name: String) {
        val k = name.trim()
        if (k.isNotEmpty()) keys.offer(k)
    }

    /**
     * Drops keys that are queued but not sent yet. Call when a hold-to-repeat ends (finger lifted)
     * so nothing keeps moving afterwards.
     */
    fun cancelPendingKeys() {
        keys.cancelPending()
    }

    fun volumeUp() = volume(+1)
    fun volumeDown() = volume(-1)

    /** Changes the volume by [steps] (positive = louder), e.g. from the system volume slider. */
    fun adjustVolume(steps: Int) {
        repeat(kotlin.math.abs(steps).coerceAtMost(MAX_VOLUME_STEPS)) { volume(if (steps > 0) +1 else -1) }
    }

    /** Volume steps go through the key queue as VOLUMEUP/VOLUMEDOWN, so they stay ordered and bounded. */
    private fun volume(delta: Int) {
        // Hold before offering: the key may be sent (and finish) before offer() even returns.
        optimistic.begin(OptimisticState.Field.VOLUME)
        optimistic.begin(OptimisticState.Field.MUTE)
        if (!keys.offer(if (delta > 0) "VOLUMEUP" else "VOLUMEDOWN")) {
            optimistic.abandon(OptimisticState.Field.VOLUME)
            optimistic.abandon(OptimisticState.Field.MUTE)
            return
        }
        _state.update {
            val a = it.status.audio
            it.copy(status = it.status.copy(audio = a.copy(volume = (a.volume + delta).coerceIn(0, 100), muted = false)))
        }
    }

    private fun endVolume(ok: Boolean) {
        optimistic.end(OptimisticState.Field.VOLUME, ok)
        optimistic.end(OptimisticState.Field.MUTE, ok)
        if (!ok) refreshSoon(0)
    }

    fun toggleMute() {
        _state.update {
            val a = it.status.audio
            it.copy(status = it.status.copy(audio = a.copy(muted = !a.muted)))
        }
        quick("mute", OptimisticState.Field.MUTE)
    }

    // ---------------------------------------------------------------- actions

    /** Runs a quick action; [fields] are held optimistically until the TV confirms them. */
    private fun quick(action: String, vararg fields: OptimisticState.Field) {
        fields.forEach(optimistic::begin)
        scope.launch {
            val res = command(HubPaths.QUICK, JSONObject().put("action", action))
            fields.forEach { optimistic.end(it, res.isOk) }
            // On a refusal, fetch the real state at once so optimistic changes are rolled back.
            refreshSoon(if (res.isOk) 700 else 0)
        }
    }

    fun toggleScreen() {
        _state.update { it.copy(status = it.status.copy(screenOff = !it.status.screenOff)) }
        quick("screen_toggle", OptimisticState.Field.SCREEN)
    }

    fun switchToPc() = launch(TvAction.PC_INPUT_ID)

    fun goHome() = quick("home")

    fun launch(appId: String) {
        if (appId.isBlank()) return
        optimistic.begin(OptimisticState.Field.APP)
        optimistic.begin(OptimisticState.Field.SCREEN)
        _state.update { it.copy(status = it.status.copy(currentAppId = appId, screenOff = false)) }
        scope.launch {
            val res = command(HubPaths.LAUNCH, JSONObject().put("id", appId))
            optimistic.end(OptimisticState.Field.APP, res.isOk)
            optimistic.end(OptimisticState.Field.SCREEN, res.isOk)
            refreshSoon(if (res.isOk) 1200 else 0)
        }
    }

    fun setPictureMode(mode: PictureMode) {
        scope.launch {
            val res = command(HubPaths.PICTURE_MODE, JSONObject().put("mode", mode.key))
            if (res is HubResult.Ok) {
                _messages.tryEmit(
                    if (res.json().bool("success", true)) "Bildmodus: ${mode.label}" else "Bildmodus wurde vom TV abgelehnt"
                )
            }
        }
    }

    fun setSoundOutput(output: SoundOutput) {
        optimistic.begin(OptimisticState.Field.OUTPUT)
        _state.update { it.copy(status = it.status.copy(audio = it.status.audio.copy(soundOutput = output.key))) }
        scope.launch {
            val res = command(HubPaths.AUDIO_OUTPUT, JSONObject().put("output", output.key))
            // The hub answers 200 with success=false when the TV refused the output.
            val ok = res is HubResult.Ok && res.json().bool("success", true)
            optimistic.end(OptimisticState.Field.OUTPUT, ok)
            refreshSoon(if (ok) 700 else 0)
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
        keys.cancelPending()
        _state.update { it.copy(status = it.status.copy(power = "Power Off")) }
        quick("power_off", OptimisticState.Field.POWER)
    }

    private val wakeLock = Any()
    @Volatile private var wakeJob: Job? = null

    /**
     * Wake-on-LAN plus "screen on" for active standby, then polls quickly for ~25 s. Pressing it
     * again while a wake is running only re-sends the packet.
     */
    fun wake() {
        synchronized(wakeLock) {
            if (wakeJob?.isActive == true) {
                scope.launch { sendWakeOnLan() }
                return
            }
            wakeJob = scope.launch {
                _state.update { it.copy(waking = true) }
                try {
                    if (!sendWakeOnLan()) {
                        _messages.tryEmit(
                            if (WakeOnLan.magicPacket(prefs.tvMac) == null) "Keine MAC-Adresse bekannt – bitte in den Einstellungen eintragen"
                            else "Einschalt-Paket konnte nicht gesendet werden (WLAN?)"
                        )
                        return@launch
                    }
                    // TV may already run the hub in active standby: also turn the panel on (silently).
                    api.post(HubPaths.QUICK, JSONObject().put("action", "screen_on"), HubApi.TIMEOUT_KEY)
                    // Poll faster for ~25 s so the UI flips to "on" as soon as the hub is up.
                    // Magic packets are fire-and-forget UDP: repeat them while the TV is not up yet.
                    for (attempt in 1..WAKE_POLLS) {
                        delay(WAKE_POLL_MS)
                        if (refresh() && !_state.value.status.isStandby) return@launch
                        if (attempt in WAKE_RESEND_AT) sendWakeOnLan()
                    }
                } finally {
                    _state.update { it.copy(waking = false) }
                }
            }
        }
    }

    // ---------------------------------------------------------------- text input

    fun sendText(text: String) {
        scope.launch { command(HubPaths.REMOTE_TEXT, JSONObject().put("text", text).put("replace", true)) }
    }

    fun sendEnter() {
        scope.launch { command(HubPaths.REMOTE_ENTER) }
    }

    // ---------------------------------------------------------------- smart home & assistant

    /** Lamps and plugs from the hub's local smart-home control; null if the hub can't be read. */
    suspend fun smartDevices(): List<SmartDevice>? {
        var res = api.get(HubPaths.SMARTHOME_DEVICES, HubApi.TIMEOUT_SMARTHOME)
        // Hubs before the local smart-home control only know the old SmartThings path.
        if (res is HubResult.Rejected && res.code == 404) res = api.get(HubPaths.LEGACY_SMARTTHINGS_DEVICES, HubApi.TIMEOUT_SMARTHOME)
        val body = (res as? HubResult.Ok)?.body ?: return null
        return runCatching { SmartDevice.parseList(body) }.getOrNull()
    }

    /** Toggle a lamp or plug; returns the new on-state, or null on failure. */
    suspend fun toggleDevice(id: String): Boolean? {
        val body = JSONObject().put("deviceId", id)
        var res = api.post(HubPaths.SMARTHOME_TOGGLE, body, HubApi.TIMEOUT_SMARTHOME)
        if (res is HubResult.Rejected && res.code == 404) res = api.post(HubPaths.LEGACY_SMARTTHINGS_TOGGLE, body, HubApi.TIMEOUT_SMARTHOME)
        val json = smartHomeResult(res) ?: return null
        val state = json.str("new_state").ifBlank { json.str("state") }.trim()
        return if (state.isBlank()) null else state.equals("on", ignoreCase = true)
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
        val res = api.post(HubPaths.SMARTHOME_SET, body, HubApi.TIMEOUT_SMARTHOME)
        if (res is HubResult.Rejected && res.code == 404) {
            notify("Hub zu alt für Helligkeit/Farbe – bitte Hub aktualisieren")
            return false
        }
        return smartHomeResult(res) != null
    }

    /** The hub's JSON answer, or null (with a message) when it failed. */
    private fun smartHomeResult(res: HubResult): JSONObject? {
        if (res !is HubResult.Ok) {
            notify(
                when {
                    res is HubResult.Rejected && res.code == 401 -> "Hub verlangt einen gültigen Token (Einstellungen)"
                    res is HubResult.Rejected -> "Smart Home: ${res.message}"
                    else -> "Hub antwortet nicht"
                }
            )
            return null
        }
        val json = parseObjectOrNull(res.body)
        if (json == null) {
            notify("Smart Home: unerwartete Antwort")
            return null
        }
        if (json.str("status") == "error" || json.opt("success") == false) {
            notify("Smart Home: ${json.str("error").ifBlank { "Fehler" }}")
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
        val reply = when (val res = api.post(HubPaths.ASSISTANT, body, HubApi.TIMEOUT_ASSISTANT)) {
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
        when (val res = api.post(HubPaths.GEMINI_KEY, JSONObject().put("api_key", key.trim()))) {
            is HubResult.Ok -> if (res.json().bool("success", true)) null else "Schlüssel wurde nicht gespeichert"
            is HubResult.Rejected -> res.message
            HubResult.Unreachable -> "Hub nicht erreichbar"
        }

    // ---------------------------------------------------------------- Wake-on-LAN & discovery

    /**
     * Sends magic packets on every local network (bound to it, so a VPN can't swallow them):
     * directed broadcast, 255.255.255.255 and the TV's last address. Returns false without a
     * valid MAC or if nothing could be sent.
     */
    suspend fun sendWakeOnLan(mac: String = prefs.tvMac): Boolean = withContext(Dispatchers.IO) {
        val packet = WakeOnLan.magicPacket(mac) ?: return@withContext false
        lan.awaitSettled()
        val host = prefs.tvHost
        val byNetwork = lan.addressesByNetwork()
        if (byNetwork.isEmpty()) return@withContext WakeOnLan.send(packet, WakeOnLan.targets(emptyList(), host))
        var any = false
        for ((network, addresses) in byNetwork) {
            any = WakeOnLan.send(packet, WakeOnLan.targets(addresses, host)) { network.bindSocket(it) } || any
        }
        any
    }

    /**
     * Scans the local /24 network(s) for a Modern-LG-Hub on port 8080 and returns its IP, or null.
     * The remembered address is tried first; the scan stops at the first hub found.
     * Takes ~2-5 s on a typical home network; returns null at once without Wi-Fi.
     */
    suspend fun discoverHub(): String? = withContext(Dispatchers.IO) {
        lan.awaitSettled()
        val local = lan.addresses()
        val remembered = prefs.tvHost
        suspend fun probe(host: String) =
            isHubAnswer(api.get(HubPaths.INFO, DISCOVERY_TIMEOUT_MS, base = "http://$host:${HubApi.DEFAULT_PORT}"))

        if (Ipv4.parse(remembered) != null && local.any { it.contains(remembered) } && probe(remembered)) {
            return@withContext remembered
        }
        val candidates = Ipv4.scanCandidates(local, preferred = null) - remembered
        if (candidates.isEmpty()) return@withContext null
        coroutineScope {
            val found = CompletableDeferred<String?>()
            val gate = Semaphore(DISCOVERY_PARALLELISM)
            val probes = candidates.map { host ->
                launch { gate.withPermit { if (!found.isCompleted && probe(host)) found.complete(host) } }
            }
            launch {
                probes.joinAll()
                found.complete(null)
            }
            found.await().also { coroutineContext.cancelChildren() }
        }
    }

    // ---------------------------------------------------------------- one-tap actions

    /**
     * Execute a widget / shortcut / routine / notification action and *wait* for the hub,
     * so callers like a BroadcastReceiver (goAsync) don't lose it.
     * Returns false if the hub could not be reached or refused. UI actions ([TvAction.opensUi]) are no-ops.
     */
    suspend fun perform(action: TvAction): Boolean {
        suspend fun quickNow(name: String) = api.post(HubPaths.QUICK, JSONObject().put("action", name)).isOk
        suspend fun launchNow(id: String) = api.post(HubPaths.LAUNCH, JSONObject().put("id", id)).isOk

        lan.awaitSettled()
        val ok = when (action) {
            TvAction.POWER -> {
                // No live state here (widget, routine): ask the hub first.
                if (refresh() && !_state.value.status.isStandby) {
                    quickNow("power_off")
                } else {
                    val sent = sendWakeOnLan()
                    api.post(HubPaths.QUICK, JSONObject().put("action", "screen_on"), HubApi.TIMEOUT_KEY)
                    sent
                }
            }
            TvAction.SCREEN -> quickNow("screen_toggle")
            TvAction.PC -> launchNow(TvAction.PC_INPUT_ID)
            TvAction.AUDIO -> api.post(HubPaths.AUDIO_TOGGLE).let { it is HubResult.Ok && it.json().bool("success", true) }
            TvAction.MUTE -> quickNow("mute")
            TvAction.PLAY_PAUSE -> api.post(HubPaths.REMOTE_KEY, JSONObject().put("key", "PLAY"), HubApi.TIMEOUT_KEY).isOk
            TvAction.HOME -> quickNow("home")
            TvAction.NETFLIX, TvAction.YOUTUBE, TvAction.SPOTIFY -> launchNow(TvAction.APP_IDS.getValue(action))
            TvAction.REMOTE -> true
        }
        if (action != TvAction.REMOTE) {
            if (ok) delay(600)
            refresh()
        }
        return ok
    }

    // After all properties: everything it touches must be initialised before the coroutine runs.
    init {
        // Back on Wi-Fi: poll at once. Off Wi-Fi: the hub cannot be reached, don't show stale state.
        scope.launch {
            lan.available.drop(1).distinctUntilChanged().collect { available ->
                if (available) {
                    pollKick.trySend(Unit)
                } else {
                    failedPolls.set(0)
                    _state.update { if (it.connection == Connection.ONLINE) it.copy(connection = Connection.OFFLINE, waking = false) else it }
                }
            }
        }
    }

    /**
     * Switch on or off explicitly (routines "power_on" / "power_off"): no toggle, so running it
     * twice or with stale state never does the opposite. Waits for the hub like [perform].
     */
    suspend fun setPower(on: Boolean): Boolean {
        lan.awaitSettled()
        val ok = if (on) {
            // Already on: nothing to do (but make sure the panel is lit).
            if (refresh() && !_state.value.status.isStandby) {
                api.post(HubPaths.QUICK, JSONObject().put("action", "screen_on"), HubApi.TIMEOUT_KEY)
                true
            } else {
                val sent = sendWakeOnLan()
                api.post(HubPaths.QUICK, JSONObject().put("action", "screen_on"), HubApi.TIMEOUT_KEY)
                sent
            }
        } else {
            // Unreachable hub = TV already off: that counts as done.
            when (api.post(HubPaths.QUICK, JSONObject().put("action", "power_off"))) {
                is HubResult.Ok, HubResult.Unreachable -> true
                is HubResult.Rejected -> false
            }
        }
        if (ok) delay(600)
        refresh()
        return ok
    }

    companion object {
        @Volatile private var instance: TvRepository? = null

        /** The hub keeps more itself; a short tail is enough for follow-up questions. */
        const val MAX_HISTORY_TURNS = 10

        private const val MAX_VOLUME_STEPS = 10
        private const val APPS_MAX_AGE_MS = 10 * 60_000L
        private const val APPS_RETRY_MS = 30_000L
        private const val WAKE_POLLS = 12
        private const val WAKE_POLL_MS = 2_000L
        private val WAKE_RESEND_AT = setOf(1, 3)
        private const val DISCOVERY_TIMEOUT_MS = 900L
        private const val DISCOVERY_PARALLELISM = 48

        /** HubPaths.INFO answered by a Modern-LG-Hub, or 401 because it wants a token (still our hub). */
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
