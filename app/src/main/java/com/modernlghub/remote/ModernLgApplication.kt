package com.modernlghub.remote

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.lifecycle.DefaultLifecycleObserver
import coil.ImageLoader
import coil.ImageLoaderFactory
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.modernlghub.remote.data.TvRepository

class ModernLgApplication : Application(), ImageLoaderFactory {

    /** App icons come from the hub, so they use the hub's HTTP client (and its X-Hub-Token). */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(TvRepository.get(this).api.client)
            .crossfade(true)
            .build()


    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        // Poll the TV only while any of our UI (app or mini remote) is in the foreground.
        val repo = TvRepository.get(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = repo.acquirePolling(POLL_CLIENT_UI)
            override fun onStop(owner: LifecycleOwner) = repo.releasePolling(POLL_CLIENT_UI)
        })
    }

    private fun createNotificationChannels() {
        val channel = NotificationChannel(
            CHANNEL_ID_PRESENCE,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID_PRESENCE = "channel_tv_presence"
        private const val POLL_CLIENT_UI = "ui"
    }
}
