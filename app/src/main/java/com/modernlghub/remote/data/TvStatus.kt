package com.modernlghub.remote.data

data class TvStatus(
    val isOnline: Boolean = false,
    val powerState: String = "Standby",
    val isScreenOff: Boolean = false,
    val currentApp: String = "",
    val currentAppTitle: String = "",
    val audio: AudioStatus = AudioStatus(),
    val macAddresses: Map<String, String> = emptyMap(),
    val primaryMac: String = ""
)
