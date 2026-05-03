package com.mcgo.app.server

import android.app.ActivityManager
import android.app.Service
import android.content.Context

const val MaxPaperRuntimeSlots: Int = 4

fun paperRuntimeSlotProcessSuffix(slot: Int): String = when (slot) {
    1 -> ":paper_runtime"
    2 -> ":paper_runtime_2"
    3 -> ":paper_runtime_3"
    4 -> ":paper_runtime_4"
    else -> error("不支持的运行时槽位：$slot")
}

fun paperRuntimeNotificationId(slot: Int): Int = 2000 + slot

fun activePaperRuntimeSlots(context: Context): Set<Int> {
    val activityManager = context.getSystemService(ActivityManager::class.java) ?: return emptySet()
    @Suppress("DEPRECATION")
    val processes = activityManager.runningAppProcesses.orEmpty()
    return (1..MaxPaperRuntimeSlots).filterTo(mutableSetOf()) { slot ->
        val processName = context.packageName + paperRuntimeSlotProcessSuffix(slot)
        processes.any { it.processName == processName }
    }
}

fun paperRuntimeServiceClass(slot: Int): Class<out Service> = when (slot) {
    1 -> PaperServerService::class.java
    2 -> PaperServerServiceSlot2::class.java
    3 -> PaperServerServiceSlot3::class.java
    4 -> PaperServerServiceSlot4::class.java
    else -> error("不支持的运行时槽位：$slot")
}

class PaperServerServiceSlot2 : PaperServerService()
class PaperServerServiceSlot3 : PaperServerService()
class PaperServerServiceSlot4 : PaperServerService()
