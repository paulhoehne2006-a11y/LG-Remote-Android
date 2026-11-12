package com.modernlghub.remote.system

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.modernlghub.remote.R
import com.modernlghub.remote.data.TvApiClient
import com.modernlghub.remote.data.TvPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TvRemoteWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val apiClient = TvApiClient(context)
        val prefs = TvPreferences(context)

        scope.launch {
            val status = apiClient.getStatus()
            for (widgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_remote_2x2)

                // Header status
                views.setTextViewText(R.id.widget_title, "LG OLED G4")
                views.setTextViewText(
                    R.id.widget_sub,
                    if (status.isOnline) {
                        if (status.isScreenOff) "Display Aus" else status.currentAppTitle
                    } else "Standby"
                )

                // Bind Slots 1 to 4
                bindSlot(context, views, R.id.btn_slot_1, R.id.icon_slot_1, R.id.text_slot_1, prefs.widgetSlot1, 1)
                bindSlot(context, views, R.id.btn_slot_2, R.id.icon_slot_2, R.id.text_slot_2, prefs.widgetSlot2, 2)
                bindSlot(context, views, R.id.btn_slot_3, R.id.icon_slot_3, R.id.text_slot_3, prefs.widgetSlot3, 3)
                bindSlot(context, views, R.id.btn_slot_4, R.id.icon_slot_4, R.id.text_slot_4, prefs.widgetSlot4, 4)

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }

    private fun bindSlot(
        context: Context,
        views: RemoteViews,
        btnId: Int,
        iconId: Int,
        textId: Int,
        actionType: String,
        requestCode: Int
    ) {
        val (iconRes, text) = when (actionType) {
            TvPreferences.ACTION_POWER -> Pair(R.drawable.ic_power, "Power")
            TvPreferences.ACTION_SCREEN_OFF -> Pair(R.drawable.ic_oled_off, "Display Aus")
            TvPreferences.ACTION_PC -> Pair(R.drawable.ic_pc, "PC (HDMI 1)")
            TvPreferences.ACTION_OVERLAY -> Pair(R.drawable.ic_remote, "Remote")
            TvPreferences.ACTION_AUDIO -> Pair(R.drawable.ic_audio, "Denon ⇄ TV")
            TvPreferences.ACTION_MUTE -> Pair(R.drawable.ic_mute, "Mute")
            TvPreferences.ACTION_HOME -> Pair(R.drawable.ic_home, "Home")
            TvPreferences.ACTION_NETFLIX -> Pair(R.drawable.ic_remote, "Netflix")
            TvPreferences.ACTION_YOUTUBE -> Pair(R.drawable.ic_remote, "YouTube")
            TvPreferences.ACTION_SPOTIFY -> Pair(R.drawable.ic_remote, "Spotify")
            else -> Pair(R.drawable.ic_remote, actionType)
        }

        views.setImageViewResource(iconId, iconRes)
        views.setTextViewText(textId, text)

        val intent = Intent(context, TvRemoteWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_CLICK
            putExtra(EXTRA_ACTION_TYPE, actionType)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(btnId, pendingIntent)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_CLICK) {
            val actionType = intent.getStringExtra(EXTRA_ACTION_TYPE) ?: return
            val apiClient = TvApiClient(context)

            scope.launch {
                when (actionType) {
                    TvPreferences.ACTION_POWER -> {
                        val st = apiClient.getStatus()
                        if (st.isOnline) {
                            apiClient.quickAction("power_off")
                        } else {
                            apiClient.sendWakeOnLan()
                        }
                    }
                    TvPreferences.ACTION_SCREEN_OFF -> apiClient.quickAction("screen_toggle")
                    TvPreferences.ACTION_PC -> apiClient.quickAction("pc")
                    TvPreferences.ACTION_OVERLAY -> {
                        val overlayIntent = Intent(context, QuickRemoteOverlayActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        context.startActivity(overlayIntent)
                    }
                    TvPreferences.ACTION_AUDIO -> apiClient.toggleAudioOutput()
                    TvPreferences.ACTION_MUTE -> apiClient.quickAction("mute")
                    TvPreferences.ACTION_HOME -> apiClient.quickAction("home")
                    TvPreferences.ACTION_NETFLIX -> apiClient.launchApp("netflix")
                    TvPreferences.ACTION_YOUTUBE -> apiClient.launchApp("youtube.leanback.v4")
                    TvPreferences.ACTION_SPOTIFY -> apiClient.launchApp("spotify-beehive")
                }

                delay(500)
                // Refresh all widgets
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, TvRemoteWidgetProvider::class.java))
                onUpdate(context, manager, ids)
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_CLICK = "com.modernlghub.remote.action.WIDGET_CLICK"
        const val EXTRA_ACTION_TYPE = "extra_action_type"
    }
}
