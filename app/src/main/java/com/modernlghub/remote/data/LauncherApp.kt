package com.modernlghub.remote.data

data class LauncherApp(
    val id: String,
    val title: String,
    val icon: String = "",
    val type: String = "app",
    val alias: String = "",
    val usage: Double = 0.0
)
