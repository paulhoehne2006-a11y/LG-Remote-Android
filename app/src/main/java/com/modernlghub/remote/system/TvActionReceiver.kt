package com.modernlghub.remote.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.modernlghub.remote.data.TvAction
import com.modernlghub.remote.data.TvRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs one-tap actions in the background for the widget and for shortcuts / routines.
 *
 * Not exported: only our own PendingIntents and explicit broadcasts reach it, so other apps
 * cannot switch the TV through it (the widget provider itself must stay exported for the system).
 *
 * The request runs under goAsync(): while the broadcast is pending the process is not frozen
 * (Android 14 freezes cached apps), which is not true for a coroutine started from an activity
 * that has already finished.
 */
class TvActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PERFORM) return
        val key = runCatching { intent.getStringExtra(EXTRA_ACTION) }.getOrNull()
        val action = TvAction.fromExternal(key) ?: return
        val power = TvAction.explicitPower(key)
        if (action.opensUi || !Debounce.accept(key ?: action.key)) return
        val repo = TvRepository.get(context)
        val pending = goAsync()
        scope.launch {
            try {
                withTimeoutOrNull(BUDGET_MS) { if (power != null) repo.setPower(power) else repo.perform(action) }
                if (intent.getBooleanExtra(EXTRA_FROM_WIDGET, false)) TvRemoteWidgetProvider.refreshAll(context, fetch = false)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PERFORM = "com.modernlghub.remote.action.PERFORM"
        const val EXTRA_ACTION = "tv_action"
        const val EXTRA_FROM_WIDGET = "from_widget"

        /** goAsync() work must finish well within the 10 s broadcast timeout. */
        private const val BUDGET_MS = 9_000L

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Explicit, foreground-priority broadcast for [action] (from our own components only). */
        fun intent(context: Context, action: TvAction, fromWidget: Boolean = false): Intent =
            intent(context, action.key, fromWidget)

        /** Same for a raw, already validated key (keeps "power_on" / "power_off" explicit). */
        fun intent(context: Context, key: String, fromWidget: Boolean = false): Intent =
            Intent(context, TvActionReceiver::class.java)
                .setAction(ACTION_PERFORM)
                .putExtra(EXTRA_ACTION, key)
                .putExtra(EXTRA_FROM_WIDGET, fromWidget)
                // Foreground queue: no multi-second delay behind other apps' broadcasts.
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
    }
}

/**
 * Ignores the same action fired again within [windowMs] (double taps on the widget, a routine
 * firing twice) – a toggle sent twice would undo itself.
 */
internal object Debounce {
    private val last = HashMap<String, Long>()
    var windowMs = 1_500L

    @Synchronized
    fun accept(key: String, now: Long = System.nanoTime() / 1_000_000L): Boolean {
        val prev = last[key]
        if (prev != null && now - prev < windowMs) return false
        last[key] = now
        return true
    }

    @Synchronized
    fun reset() = last.clear()
}
