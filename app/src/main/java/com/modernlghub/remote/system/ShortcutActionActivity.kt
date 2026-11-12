package com.modernlghub.remote.system

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.modernlghub.remote.data.TvAction
import com.modernlghub.remote.data.TvRepository
import kotlinx.coroutines.launch

/**
 * Invisible trampoline for launcher shortcuts and Samsung Modes & Routines
 * ("App-Aktion" → com.modernlghub.remote.action.SHORTCUT, extra `action`).
 */
class ShortcutActionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = TvAction.fromKey(intent.getStringExtra(EXTRA_ACTION))
        when {
            action == null -> Unit
            action.opensUi -> startActivity(Intent(this, QuickRemoteOverlayActivity::class.java))
            else -> {
                // The repository scope outlives this activity; the request finishes in the background.
                val repo = TvRepository.get(this)
                repo.scope.launch { repo.perform(action) }
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_ACTION = "action"
    }
}
