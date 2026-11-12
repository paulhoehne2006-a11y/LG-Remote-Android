package com.modernlghub.remote.data

data class SmartDevice(
    val id: String,
    val name: String,
    val rawName: String,
    val type: String, // light, plug
    val state: String // on, off
)
