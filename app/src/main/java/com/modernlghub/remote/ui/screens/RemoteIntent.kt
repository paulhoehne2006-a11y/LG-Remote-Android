package com.modernlghub.remote.ui.screens

import com.modernlghub.remote.data.PictureMode
import com.modernlghub.remote.data.SoundOutput
import com.modernlghub.remote.data.TvRepository

/** Everything the remote UI can ask for. Keeps screens free of networking code. */
sealed interface RemoteIntent {
    data class Key(val name: String) : RemoteIntent
    data class Launch(val appId: String) : RemoteIntent
    data class Text(val text: String) : RemoteIntent
    data class Picture(val mode: PictureMode) : RemoteIntent
    data class Sound(val output: SoundOutput) : RemoteIntent
    data object Enter : RemoteIntent
    data object Power : RemoteIntent
    data object ToggleScreen : RemoteIntent
    data object ToggleSound : RemoteIntent
    data object Pc : RemoteIntent
    data object VolumeUp : RemoteIntent
    data object VolumeDown : RemoteIntent
    data object Mute : RemoteIntent
    data object Retry : RemoteIntent
    /** A held key was released: keys still waiting are dropped so the TV stops at once. */
    data object StopRepeat : RemoteIntent
}

fun TvRepository.handle(intent: RemoteIntent) {
    when (intent) {
        is RemoteIntent.Key -> key(intent.name)
        is RemoteIntent.Launch -> launch(intent.appId)
        is RemoteIntent.Text -> sendText(intent.text)
        is RemoteIntent.Picture -> setPictureMode(intent.mode)
        is RemoteIntent.Sound -> setSoundOutput(intent.output)
        RemoteIntent.Enter -> sendEnter()
        RemoteIntent.Power -> togglePower()
        RemoteIntent.ToggleScreen -> toggleScreen()
        RemoteIntent.ToggleSound -> toggleSoundOutput()
        RemoteIntent.Pc -> switchToPc()
        RemoteIntent.VolumeUp -> volumeUp()
        RemoteIntent.VolumeDown -> volumeDown()
        RemoteIntent.Mute -> toggleMute()
        RemoteIntent.Retry -> resetConnection()
        RemoteIntent.StopRepeat -> cancelPendingKeys()
    }
}
