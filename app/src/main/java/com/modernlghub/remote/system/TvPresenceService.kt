package com.modernlghub.remote.system

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.modernlghub.remote.ModernLgApplication
import com.modernlghub.remote.R
import com.modernlghub.remote.data.TvApiClient
import com.modernlghub.remote.data.TvPreferences
import com.modernlghub.remote.data.TvStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TvPresenceService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var apiClient: TvApiClient
    private lateinit var prefs: TvPreferences
    private var lastStatus: TvStatus? = null

    override fun onCreate() {
        super.onCreate()
        apiClient = TvApiClient(this)
        prefs = TvPreferences(this)
        startMonitoringLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action != null) {
            handleNotificationAction(action)
        }
        return START_STICKY
    }

    private fun handleNotificationAction(action: String) {
        scope.launch {
            when (action) {
                ACTION_PLAY_PAUSE -> apiClient.sendRemoteKey("PLAY")
                ACTION_SCREEN_OFF -> apiClient.quickAction("screen_toggle")
                ACTION_MUTE -> apiClient.quickAction("mute")
            }
            delay(400)
            checkAndPublishNotification()
        }
    }

    private fun startMonitoringLoop() {
        scope.launch {
            // Initial placeholder notification for foreground service requirement
            val initialNotif = buildNotification(TvStatus(isOnline = false))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotif)
            }

            while (isActive) {
                if (prefs.liveNotificationEnabled) {
                    checkAndPublishNotification()
                }
                delay(4000)
            }
        }
    }

    private suspend fun checkAndPublishNotification() {
        val status = apiClient.getStatus()
        lastStatus = status
        val notif = buildNotification(status)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notif)
    }

    private fun buildNotification(status: TvStatus): android.app.Notification {
        val openIntent = Intent(this, QuickRemoteOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            1,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Actions
        val playIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, TvPresenceService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val screenIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, TvPresenceService::class.java).apply { action = ACTION_SCREEN_OFF },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val muteIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, TvPresenceService::class.java).apply { action = ACTION_MUTE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (status.isOnline) "LG OLED: ${status.currentAppTitle}" else "LG OLED G4 (Standby)"
        val content = if (status.isOnline) {
            "${status.audio.displayTitle} · ${if (status.isScreenOff) "OLED Aus" else "Aktiv"}"
        } else {
            "Bereit im Heim-WLAN"
        }

        val builder = NotificationCompat.Builder(this, ModernLgApplication.CHANNEL_ID_PRESENCE)
            .setSmallIcon(R.drawable.ic_remote)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (status.isOnline) {
            builder.addAction(R.drawable.ic_remote, "Play", playIntent)
            builder.addAction(R.drawable.ic_oled_off, "OLED Aus", screenIntent)
            builder.addAction(R.drawable.ic_mute, "Mute", muteIntent)
            builder.addAction(R.drawable.ic_remote, "Remote", openPendingIntent)
        }

        return builder.build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_PAUSE = "com.modernlghub.remote.action.PLAY_PAUSE"
        const val ACTION_SCREEN_OFF = "com.modernlghub.remote.action.SCREEN_OFF"
        const val ACTION_MUTE = "com.modernlghub.remote.action.MUTE"
    }
}
