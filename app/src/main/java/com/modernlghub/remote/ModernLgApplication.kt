package com.modernlghub.remote

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.modernlghub.remote.data.PollPolicy
import com.modernlghub.remote.data.TvRepository
import com.modernlghub.remote.system.TvRemoteWidgetProvider
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ModernLgApplication : Application(), ImageLoaderFactory {

    /** App icons come from the hub, so they use the hub's HTTP client (and its X-Hub-Token). */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(TvRepository.get(this).api.client)
            .crossfade(true)
            .build()

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        // Poll the TV only while any of our UI (app or mini remote) is in the foreground.
        // ProcessLifecycleOwner reports "stopped" ~700 ms after the last activity stopped, so
        // rotating or switching between our own activities does not restart polling.
        val repo = TvRepository.get(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = repo.acquirePolling(POLL_CLIENT_UI, PollPolicy.UI)
            override fun onStop(owner: LifecycleOwner) = repo.releasePolling(POLL_CLIENT_UI)
        })

        // Widgets placed by an older version may still carry old click intents: re-render once.
        TvRemoteWidgetProvider.refreshAll(this, fetch = false)
        // Keep placed widgets in step with what polling learns (only re-rendered when the visible
        // line or the on/off dot changes; no extra network traffic).
        repo.scope.launch {
            repo.state
                .map { TvRemoteWidgetProvider.statusLine(it) to it.isOn }
                .distinctUntilChanged()
                .drop(1)
                .debounce(WIDGET_DEBOUNCE_MS)
                .collect { if (TvRemoteWidgetProvider.hasWidgets(this@ModernLgApplication)) TvRemoteWidgetProvider.refreshAll(this@ModernLgApplication, fetch = false) }
        }
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
        private const val WIDGET_DEBOUNCE_MS = 500L
    }
}
