package com.modernlghub.remote.system

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.modernlghub.remote.data.TvAction

/**
 * Invisible trampoline for launcher shortcuts and Samsung Modes & Routines
 * ("App-Aktion" → com.modernlghub.remote.action.SHORTCUT, extra `action`).
 *
 * Exported by necessity, so the input is treated as untrusted: only a short, known action key
 * is accepted ([TvAction.fromExternal]); anything else is ignored. The actual request is handed
 * to [TvActionReceiver] (goAsync), because this activity finishes immediately and a finished,
 * cached process may be frozen before a coroutine started here completes.
 */
class ShortcutActionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A config-change re-creation must not fire the action twice.
        if (savedInstanceState == null) handle(intent)
        finish()
    }

    private fun handle(intent: Intent?) {
        val key = keyOf(intent) ?: return
        val action = TvAction.fromExternal(key) ?: return
        if (action.opensUi) {
            startActivity(Intent(this, QuickRemoteOverlayActivity::class.java))
        } else {
            // The raw key, so "power_on" / "power_off" stay explicit instead of a toggle.
            sendBroadcast(TvActionReceiver.intent(this, key))
        }
    }

    companion object {
        const val EXTRA_ACTION = "action"
        const val ACTION_SHORTCUT = "com.modernlghub.remote.action.SHORTCUT"

        /**
         * The requested action, or null. Accepts our SHORTCUT action or an explicit launch without
         * an action (some routine apps start the component directly); a malformed extras bundle
         * from another app is caught instead of crashing.
         */
        fun actionOf(intent: Intent?): TvAction? = TvAction.fromExternal(keyOf(intent))

        /** The validated action key of [intent] (e.g. "power_on"), or null. */
        fun keyOf(intent: Intent?): String? {
            if (intent == null) return null
            val raw = runCatching { intent.extras?.let { @Suppress("DEPRECATION") it.get(EXTRA_ACTION) } }.getOrNull()
            return if (actionOf(intent.action, raw) != null) raw as String else null
        }

        /** Pure part of [actionOf] (unit-tested). */
        fun actionOf(intentAction: String?, extra: Any?): TvAction? {
            if (intentAction != null && intentAction != ACTION_SHORTCUT) return null
            return TvAction.fromExternal(extra)
        }
    }
}
