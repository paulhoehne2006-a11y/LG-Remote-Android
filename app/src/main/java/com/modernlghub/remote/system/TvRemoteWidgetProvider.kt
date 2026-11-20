package com.modernlghub.remote.system

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.modernlghub.remote.R
import com.modernlghub.remote.data.Connection
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
 *
 * Taps go to the non-exported [TvActionReceiver] (this provider must be exported for the system,
 * so it does not act on click broadcasts itself – any app could send those). Network work runs
 * under goAsync(), so Android doesn't kill it half-way.
 */
class TvRemoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val repo = TvRepository.get(context)
        // Draw immediately with what we know, then refresh from the hub.
        render(context, manager, ids, repo.state.value)
        val pending = goAsync()
        scope.launch {
            try {
                // Periodic updates away from home would only run into timeouts.
                if (repo.lan.awaitSettled()) withTimeoutOrNull(8_000) { repo.refresh() }
                render(context, manager, ids, repo.state.value)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Widgets placed with an older version still carry click intents to this exported
        // receiver. They are not trusted; re-rendering gives those widgets the new intents.
        if (intent.action == ACTION_WIDGET_CLICK) refreshAll(context, fetch = false)
    }

    companion object {
        /** Legacy click action (pre-2.4 widgets); no longer acted upon. */
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
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = runCatching { manager.getAppWidgetIds(ComponentName(context, TvRemoteWidgetProvider::class.java)) }
                .getOrNull() ?: return
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

        /** True if at least one widget is placed (cheap enough to call on state changes). */
        fun hasWidgets(context: Context): Boolean = runCatching {
            AppWidgetManager.getInstance(context)
                ?.getAppWidgetIds(ComponentName(context, TvRemoteWidgetProvider::class.java))
                ?.isNotEmpty() == true
        }.getOrDefault(false)

        /** Line under the title; also used to decide whether a re-render is needed. */
        internal fun statusLine(state: TvState): String = when {
            state.connection == Connection.UNAUTHORIZED -> "Token fehlt"
            state.waking -> "Wird eingeschaltet …"
            !state.isOnline -> "Aus"
            state.status.isStandby -> "Standby"
            state.status.screenOff -> "Bild aus"
            else -> state.appTitle().ifBlank { "An" }
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray, state: TvState) {
            val prefs = TvRepository.get(context).prefs
            val views = RemoteViews(context.packageName, R.layout.widget_remote_2x2)

            views.setTextViewText(R.id.widget_sub, statusLine(state))
            views.setImageViewResource(R.id.widget_status_dot, if (state.isOn) R.drawable.widget_dot_on else R.drawable.widget_dot_off)
            views.setOnClickPendingIntent(
                R.id.widget_header,
                PendingIntent.getActivity(
                    context, REQUEST_OPEN_APP, Intent(context, MainActivity::class.java),
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
                    context, REQUEST_OVERLAY + slot,
                    Intent(context, QuickRemoteOverlayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            } else {
                // One request code per slot: PendingIntents differing only in extras would be merged.
                PendingIntent.getBroadcast(
                    context, REQUEST_ACTION + slot,
                    TvActionReceiver.intent(context, action, fromWidget = true),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }

        private const val REQUEST_OPEN_APP = 100
        private const val REQUEST_OVERLAY = 200
        private const val REQUEST_ACTION = 400
    }
}
