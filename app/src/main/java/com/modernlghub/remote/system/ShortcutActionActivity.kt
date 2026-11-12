package com.modernlghub.remote.system

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.modernlghub.remote.data.TvApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ShortcutActionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent.getStringExtra("action") ?: ""
        val apiClient = TvApiClient(this)

        if (action.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO + Job()).launch {
                when (action) {
                    "screen_toggle" -> apiClient.quickAction("screen_toggle")
                    "pc" -> apiClient.quickAction("pc")
                    "power_off" -> apiClient.quickAction("power_off")
                    "power_on" -> apiClient.sendWakeOnLan()
                    "mute" -> apiClient.quickAction("mute")
                    "audio_toggle" -> apiClient.toggleAudioOutput()
                    else -> apiClient.quickAction(action)
                }
            }
        }

        // Invisible headless activity completes immediately
        finish()
    }
}
