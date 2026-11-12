package com.modernlghub.remote

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.modernlghub.remote.data.TvPreferences
import com.modernlghub.remote.system.TvPresenceService

class ModernLgApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        val prefs = TvPreferences(this)
        if (prefs.liveNotificationEnabled) {
            try {
                val intent = Intent(this, TvPresenceService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } catch (e: Exception) {
                // Background start restrictions may apply until activity is opened
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val desc = getString(R.string.notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID_PRESENCE, name, importance).apply {
                description = desc
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID_PRESENCE = "channel_tv_presence"
    }
}
