package com.modernlghub.remote.system

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.modernlghub.remote.data.Connection
import com.modernlghub.remote.data.TvRepository
import com.modernlghub.remote.data.TvState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick Settings tile: shows what the TV is doing, tap opens the mini remote. */
class TvQuickTileService : TileService() {

    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        val repo = TvRepository.get(this)
        render(repo.state.value)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { s ->
            s.launch { repo.state.collect { render(it) } }
            s.launch(Dispatchers.Default) { repo.refresh() }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    private fun render(state: TvState) {
        val tile = qsTile ?: return
        tile.state = if (state.isOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "LG OLED"
        tile.subtitle = when {
            state.connection == Connection.UNAUTHORIZED -> "Token fehlt"
            !state.isOnline -> "Aus"
            state.status.screenOff -> "Bild aus"
            else -> state.appTitle().ifBlank { "An" }
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, QuickRemoteOverlayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
