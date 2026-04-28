package com.mcgo.app.ui.model

import java.util.Locale

fun formatRuntime(totalMinutes: Int): String {
    val safeMinutes = totalMinutes.coerceAtLeast(0)
    val hours = safeMinutes / 60
    val minutes = safeMinutes % 60
    return "${hours}h ${minutes.toString().padStart(2, '0')}m"
}

fun formatPlayerCapacity(onlinePlayers: Int, maxPlayers: Int): String =
    "$onlinePlayers/$maxPlayers 人"

fun formatBatteryCurrent(currentMilliAmps: Int): String =
    String.format(Locale.US, "%+d mA", currentMilliAmps)
