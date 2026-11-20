package com.modernlghub.remote.system

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.modernlghub.remote.ModernLgApplication
import com.modernlghub.remote.R
import com.modernlghub.remote.data.Connection
import com.modernlghub.remote.data.PollPolicy
import com.modernlghub.remote.data.TvAction
import com.modernlghub.remote.data.TvRepository
import com.modernlghub.remote.data.TvState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Optional "Live-Steuerung": a media session for the TV, so One UI shows it on the lock screen /
 * Now Bar with play-pause, display-off and mute, and the media volume slider drives the TV.
 *
 * Battery: the TV is only polled while the phone is on Wi-Fi/Ethernet ([TvRepository.lan]),
 * with a long back-off while the TV is off ([PollPolicy.BACKGROUND]).
 *
 * Start rules (Android 12+): it may only be started while the app is in the foreground. A sticky
 * restart by the system after the process was killed can be refused; then the service stops
 * itself instead of crashing, and MainActivity starts it again next time.
 */
class TvPresenceService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repo: TvRepository
    private var session: MediaSession? = null
    private var volumeProvider: VolumeProvider? = null
    private var onWifi = false
    private var playing = true
    private var started = false

    override fun onCreate() {
        super.onCreate()
        repo = TvRepository.get(this)
        val mediaSession = MediaSession(this, TV_NAME).apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = togglePlay()
                override fun onPause() = togglePlay()
                override fun onSkipToNext() = repo.key("FASTFORWARD")
                override fun onSkipToPrevious() = repo.key("REWIND")
                // onStop is deliberately not handled: ACTION_STOP is not advertised, and any
                // connected controller (watch, car) sending "stop" must not switch the TV off.
            })
            setSessionActivity(openRemoteIntent())
            isActive = true
        }
        session = mediaSession
        started = try {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(repo.state.value, mediaSession),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
            true
        } catch (e: RuntimeException) {
            // ForegroundServiceStartNotAllowedException (background restart) or a SecurityException
            // for the service type: give up quietly.
            false
        }
        // startForeground() must come first even when we stop right away: after
        // startForegroundService(), stopping without it crashes the app.
        // Turned off in the settings meanwhile (e.g. sticky restart): don't come back.
        if (!started || !repo.prefs.liveNotificationEnabled) {
            if (started) stopForeground(STOP_FOREGROUND_REMOVE)
            started = false
            stopSelf()
            return
        }

        scope.launch { repo.lanAvailable.collect { setWifi(it) } }
        scope.launch {
            repo.state
                .map { Snapshot(it.connection, it.waking, it.status.isStandby, it.status.screenOff, it.appTitle(), it.status.audio.outputLabel, it.status.audio.volume, it.status.audio.muted) }
                .distinctUntilChanged()
                .collect { publish(repo.state.value) }
        }
    }

    private data class Snapshot(
        val connection: Connection, val waking: Boolean, val standby: Boolean, val screenOff: Boolean,
        val app: String, val output: String, val volume: Int, val muted: Boolean,
    )

    private fun setWifi(available: Boolean) {
        if (available == onWifi) return
        onWifi = available
        if (available) repo.acquirePolling(POLL_CLIENT, PollPolicy.BACKGROUND) else repo.releasePolling(POLL_CLIENT)
        publish(repo.state.value)
    }

    private fun togglePlay() {
        playing = !playing
        repo.key(if (playing) "PLAY" else "PAUSE")
        publish(repo.state.value)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) return START_NOT_STICKY
        when (intent?.action) {
            ACTION_PLAY -> togglePlay()
            else -> {
                val key = intent?.getStringExtra(EXTRA_ACTION)
                val power = TvAction.explicitPower(key)
                TvAction.fromExternal(key)?.let { action ->
                    if (!action.opensUi) repo.scope.launch { if (power != null) repo.setPower(power) else repo.perform(action) }
                }
            }
        }
        return START_STICKY
    }

    private fun publish(state: TvState) {
        val session = session ?: return
        if (!started) return
        val on = state.isOn
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, if (on) state.appTitle().ifBlank { TV_NAME } else TV_NAME)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, statusText(state))
                .build()
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(
                    if (on && playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    1f,
                )
                .build()
        )
        // Remote volume: the system media volume slider controls the TV while it is on.
        if (on) {
            val vp = volumeProvider ?: object : VolumeProvider(VOLUME_CONTROL_ABSOLUTE, 100, state.status.audio.volume) {
                override fun onAdjustVolume(direction: Int) {
                    when (direction) {
                        AudioManager.ADJUST_RAISE -> repo.volumeUp()
                        AudioManager.ADJUST_LOWER -> repo.volumeDown()
                        // The mute button in the volume panel: these are not "louder" (> 0)!
                        AudioManager.ADJUST_TOGGLE_MUTE -> repo.toggleMute()
                        AudioManager.ADJUST_MUTE -> if (!repo.state.value.status.audio.muted) repo.toggleMute()
                        AudioManager.ADJUST_UNMUTE -> if (repo.state.value.status.audio.muted) repo.toggleMute()
                        else -> Unit
                    }
                    currentVolume = repo.state.value.status.audio.volume
                }

                override fun onSetVolumeTo(volume: Int) {
                    repo.adjustVolume(volume.coerceIn(0, 100) - repo.state.value.status.audio.volume)
                    currentVolume = repo.state.value.status.audio.volume
                }
            }.also {
                volumeProvider = it
                session.setPlaybackToRemote(it)
            }
            vp.currentVolume = state.status.audio.volume
        } else if (volumeProvider != null) {
            volumeProvider = null
            session.setPlaybackToLocal(android.media.AudioAttributes.Builder().build())
        }
        runCatching {
            getSystemService(android.app.NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(state, session))
        }
    }

    private fun statusText(state: TvState): String = when {
        !repo.lanAvailable.value -> "Nicht im Heim-WLAN"
        state.connection == Connection.UNAUTHORIZED -> "Hub verlangt einen Token"
        state.waking -> "Wird eingeschaltet …"
        state.connection == Connection.CONNECTING -> "Verbinde …"
        !state.isOnline -> "Aus"
        state.status.isStandby -> "Standby"
        state.status.screenOff -> "Display aus · ${state.status.audio.outputLabel}"
        else -> state.status.audio.outputLabel
    }

    private fun buildNotification(state: TvState, session: MediaSession): Notification {
        val on = state.isOn
        val builder = Notification.Builder(this, ModernLgApplication.CHANNEL_ID_PRESENCE)
            .setSmallIcon(R.drawable.ic_remote)
            .setContentTitle(if (on) state.appTitle().ifBlank { TV_NAME } else TV_NAME)
            .setContentText(statusText(state))
            .setContentIntent(openRemoteIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)

        if (on) {
            builder.addAction(action(R.drawable.ic_play, if (playing) "Pause" else "Wiedergabe", ACTION_PLAY, null, 1))
            builder.addAction(action(R.drawable.ic_oled_off, if (state.status.screenOff) "Display an" else "Display aus", null, TvAction.SCREEN.key, 2))
            builder.addAction(action(R.drawable.ic_mute, if (state.status.audio.muted) "Ton an" else "Stumm", null, TvAction.MUTE.key, 3))
            builder.setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
        } else {
            // Explicit "on": the notification may be stale, a toggle could switch a running TV off.
            builder.addAction(action(R.drawable.ic_power, "Einschalten", null, TvAction.KEY_POWER_ON, 4))
            builder.setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0))
        }
        return builder.build()
    }

    private fun action(icon: Int, title: String, intentAction: String?, tvActionKey: String?, code: Int): Notification.Action {
        val intent = Intent(this, TvPresenceService::class.java).apply {
            intentAction?.let { action = it }
            tvActionKey?.let { putExtra(EXTRA_ACTION, it) }
        }
        val pi = PendingIntent.getService(this, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(Icon.createWithResource(this, icon), title, pi).build()
    }

    private fun openRemoteIntent(): PendingIntent = PendingIntent.getActivity(
        this, 10,
        Intent(this, QuickRemoteOverlayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onDestroy() {
        scope.cancel()
        if (::repo.isInitialized) repo.releasePolling(POLL_CLIENT)
        session?.run {
            isActive = false
            release()
        }
        session = null
        volumeProvider = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val TV_NAME = TvState.TV_NAME
        private const val POLL_CLIENT = "presence"
        private const val ACTION_PLAY = "com.modernlghub.remote.action.PLAY_PAUSE"
        private const val EXTRA_ACTION = "tv_action"

        /** Must be called while the app is in the foreground (Android 12+ FGS start rules). */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, TvPresenceService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TvPresenceService::class.java))
        }
    }
}
