package com.modernlghub.remote.system

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.modernlghub.remote.data.TvApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class TvQuickTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var apiClient: TvApiClient

    override fun onCreate() {
        super.onCreate()
        apiClient = TvApiClient(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, QuickRemoteOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTileState() {
        scope.launch {
            val status = apiClient.getStatus()
            val tile = qsTile ?: return@launch
            tile.state = if (status.isOnline) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = if (status.isOnline) "LG OLED: ${status.currentAppTitle}" else "LG OLED: Standby"
            tile.updateTile()
        }
    }
}
