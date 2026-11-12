package com.modernlghub.remote.data

data class AudioStatus(
    val soundOutput: String = "external_optical",
    val volume: Int = 15,
    val muted: Boolean = false,
    val isDenonOptical: Boolean = true,
    val isTvSpeaker: Boolean = false,
    val displayTitle: String = "Denon AVR-1803 (Optisch)"
)
