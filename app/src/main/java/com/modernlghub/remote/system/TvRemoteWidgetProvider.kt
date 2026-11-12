package com.modernlghub.remote.system

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.modernlghub.remote.R
import com.modernlghub.remote.data.TvAction
import com.modernlghub.remote.data.TvRepository
import com.modernlghub.remote.data.TvState
import com.modernlghub.remote.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 2×2 home screen widget with four configurable actions.
 * Network work runs under goAsync(), so Android doesn't kill it half-way.
 */
class TvRemoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val repo = TvRepository.get(context)
        // Draw immediately with what we know, then refresh from the hub.
        render(context, manager, ids, repo.state.value)
        val pending = goAsync()
        scope.launch {
            try {
                withTimeoutOrNull(8_000) { repo.refresh() }
                render(context, manager, ids, repo.state.value)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_WIDGET_CLICK) return
        val action = TvAction.fromKey(intent.getStringExtra(EXTRA_ACTION)) ?: return
        val repo = TvRepository.get(context)
        val pending = goAsync()
        scope.launch {
            try {
                withTimeoutOrNull(9_000) { repo.perform(action) }
                refreshAll(context, fetch = false)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_CLICK = "com.modernlghub.remote.action.WIDGET_CLICK"
        const val EXTRA_ACTION = "extra_action_type"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        private val SLOTS = listOf(
            Triple(R.id.btn_slot_1, R.id.icon_slot_1, R.id.text_slot_1),
            Triple(R.id.btn_slot_2, R.id.icon_slot_2, R.id.text_slot_2),
            Triple(R.id.btn_slot_3, R.id.icon_slot_3, R.id.text_slot_3),
            Triple(R.id.btn_slot_4, R.id.icon_slot_4, R.id.text_slot_4),
        )

        /** Re-render every placed widget (e.g. after the slot configuration changed). */
        fun refreshAll(context: Context, fetch: Boolean = true) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TvRemoteWidgetProvider::class.java))
            if (ids.isEmpty()) return
            if (fetch) {
                context.sendBroadcast(
                    Intent(context, TvRemoteWidgetProvider::class.java)
                        .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                )
            } else {
                render(context, manager, ids, TvRepository.get(context).state.value)
            }
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray, state: TvState) {
            val prefs = TvRepository.get(context).prefs
            val views = RemoteViews(context.packageName, R.layout.widget_remote_2x2)

            views.setTextViewText(
                R.id.widget_sub,
                when {
                    !state.isOnline -> "Aus"
                    state.status.isStandby -> "Standby"
                    state.status.screenOff -> "Bild aus"
                    else -> state.appTitle().ifBlank { "An" }
                },
            )
            views.setImageViewResource(R.id.widget_status_dot, if (state.isOn) R.drawable.widget_dot_on else R.drawable.widget_dot_off)
            views.setOnClickPendingIntent(
                R.id.widget_header,
                PendingIntent.getActivity(
                    context, 100, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )

            prefs.widgetSlots().forEachIndexed { i, action ->
                val (btn, icon, text) = SLOTS[i]
                views.setImageViewResource(icon, action.iconRes)
                views.setTextViewText(text, action.shortLabel)
                views.setOnClickPendingIntent(btn, clickIntent(context, action, i))
            }
            manager.updateAppWidget(ids, views)
        }

        private fun clickIntent(context: Context, action: TvAction, slot: Int): PendingIntent =
            if (action.opensUi) {
                // Activities must be started directly from the PendingIntent (Android 10+ blocks
                // background activity starts from a receiver).
                PendingIntent.getActivity(
                    context, 200 + slot,
                    Intent(context, QuickRemoteOverlayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            } else {
                PendingIntent.getBroadcast(
                    context, 300 + slot,
                    Intent(context, TvRemoteWidgetProvider::class.java)
                        .setAction(ACTION_WIDGET_CLICK)
                        .putExtra(EXTRA_ACTION, action.key),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
    }
}
