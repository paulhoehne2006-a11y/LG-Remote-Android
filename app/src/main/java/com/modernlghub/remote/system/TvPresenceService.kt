package com.modernlghub.remote.system

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.modernlghub.remote.ModernLgApplication
import com.modernlghub.remote.R
import com.modernlghub.remote.data.Connection
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
 * Battery: the TV is only polled while the phone is on Wi-Fi.
 */
class TvPresenceService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repo: TvRepository
    private lateinit var session: MediaSession
    private var volumeProvider: VolumeProvider? = null
    private var onWifi = false
    private var playing = true

    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            scope.launch { setWifi(true) }
        }

        override fun onLost(network: Network) {
            scope.launch { setWifi(false) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repo = TvRepository.get(this)
        session = MediaSession(this, "LG OLED").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = togglePlay()
                override fun onPause() = togglePlay()
                override fun onSkipToNext() = repo.key("FASTFORWARD")
                override fun onSkipToPrevious() = repo.key("REWIND")
                override fun onStop() = repo.powerOff()
            })
            setSessionActivity(openRemoteIntent())
            isActive = true
        }
        startForeground(
            NOTIFICATION_ID,
            buildNotification(repo.state.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )

        getSystemService(ConnectivityManager::class.java).registerNetworkCallback(
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
            wifiCallback,
        )

        scope.launch {
            repo.state
                .map { Snapshot(it.isOnline, it.status.isStandby, it.status.screenOff, it.appTitle(), it.status.audio.outputLabel, it.status.audio.volume, it.status.audio.muted) }
                .distinctUntilChanged()
                .collect { publish(repo.state.value) }
        }
    }

    private data class Snapshot(
        val online: Boolean, val standby: Boolean, val screenOff: Boolean,
        val app: String, val output: String, val volume: Int, val muted: Boolean,
    )

    private fun setWifi(available: Boolean) {
        if (available == onWifi) return
        onWifi = available
        if (available) repo.acquirePolling(POLL_CLIENT, intervalMs = 5000) else repo.releasePolling(POLL_CLIENT)
    }

    private fun togglePlay() {
        playing = !playing
        repo.key(if (playing) "PLAY" else "PAUSE")
        publish(repo.state.value)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> togglePlay()
            else -> TvAction.fromKey(intent?.getStringExtra(EXTRA_ACTION))?.let { action ->
                repo.scope.launch { repo.perform(action) }
            }
        }
        return START_STICKY
    }

    private fun publish(state: TvState) {
        val on = state.isOn
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, if (on) state.appTitle().ifBlank { "LG OLED G4" } else "LG OLED G4")
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
                    when {
                        direction > 0 -> repo.volumeUp()
                        direction < 0 -> repo.volumeDown()
                    }
                    currentVolume = repo.state.value.status.audio.volume
                }

                override fun onSetVolumeTo(volume: Int) {
                    val diff = (volume - repo.state.value.status.audio.volume).coerceIn(-10, 10)
                    repeat(kotlin.math.abs(diff)) { if (diff > 0) repo.volumeUp() else repo.volumeDown() }
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
        getSystemService(android.app.NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun statusText(state: TvState): String = when {
        state.connection == Connection.UNAUTHORIZED -> "Hub verlangt einen Token"
        !state.isOnline -> if (onWifi) "Aus" else "Nicht im Heim-WLAN"
        state.status.isStandby -> "Standby"
        state.status.screenOff -> "Bild aus · ${state.status.audio.outputLabel}"
        else -> state.status.audio.outputLabel
    }

    private fun buildNotification(state: TvState): Notification {
        val on = state.isOn
        val builder = Notification.Builder(this, ModernLgApplication.CHANNEL_ID_PRESENCE)
            .setSmallIcon(R.drawable.ic_remote)
            .setContentTitle(if (on) state.appTitle().ifBlank { "LG OLED G4" } else "LG OLED G4")
            .setContentText(statusText(state))
            .setContentIntent(openRemoteIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)

        if (on) {
            builder.addAction(action(R.drawable.ic_play, if (playing) "Pause" else "Wiedergabe", ACTION_PLAY, null, 1))
            builder.addAction(action(R.drawable.ic_oled_off, if (state.status.screenOff) "Bild an" else "Bild aus", null, TvAction.SCREEN, 2))
            builder.addAction(action(R.drawable.ic_mute, if (state.status.audio.muted) "Ton an" else "Stumm", null, TvAction.MUTE, 3))
            builder.setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
        } else {
            builder.addAction(action(R.drawable.ic_power, "Einschalten", null, TvAction.POWER, 4))
            builder.setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0))
        }
        return builder.build()
    }

    private fun action(icon: Int, title: String, intentAction: String?, tvAction: TvAction?, code: Int): Notification.Action {
        val intent = Intent(this, TvPresenceService::class.java).apply {
            intentAction?.let { action = it }
            tvAction?.let { putExtra(EXTRA_ACTION, it.key) }
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
        runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(wifiCallback) }
        repo.releasePolling(POLL_CLIENT)
        session.release()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
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
