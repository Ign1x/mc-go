package com.mcgo.app.status

import com.mcgo.app.ui.model.formatBatteryCurrent
import kotlin.math.max

const val GIGABYTE_BYTES: Long = 1024L * 1024L * 1024L
private const val BYTES_PER_MEGABIT = 125_000f

data class CpuStatSnapshot(
    val totalJiffies: Long,
    val idleJiffies: Long,
)

data class FormattedMetric(
    val valueLabel: String,
    val detailLabel: String,
)

fun calculateCpuUsagePercent(previous: CpuStatSnapshot, current: CpuStatSnapshot): Float {
    val totalDelta = (current.totalJiffies - previous.totalJiffies).coerceAtLeast(1L)
    val idleDelta = (current.idleJiffies - previous.idleJiffies).coerceAtLeast(0L)
    val busyDelta = max(totalDelta - idleDelta, 0L)
    return (busyDelta * 100f) / totalDelta
}

fun appendHistorySample(history: List<Float>, nextValue: Float, maxPoints: Int = 8): List<Float> {
    val safeMaxPoints = maxPoints.coerceAtLeast(1)
    return (history + nextValue).takeLast(safeMaxPoints)
}

fun formatRamMetric(usedBytes: Long, totalBytes: Long): FormattedMetric {
    val safeTotalBytes = totalBytes.coerceAtLeast(1L)
    val safeUsedBytes = usedBytes.coerceIn(0L, safeTotalBytes)
    val freeBytes = (safeTotalBytes - safeUsedBytes).coerceAtLeast(0L)
    return FormattedMetric(
        valueLabel = "${toGigabytesLabel(safeUsedBytes)} / ${toGigabytesLabel(safeTotalBytes)} GB",
        detailLabel = "空闲 ${toGigabytesLabel(freeBytes)} GB",
    )
}

fun formatNetworkMetric(uploadBytesPerSecond: Long, downloadBytesPerSecond: Long): FormattedMetric {
    val safeUploadBytes = uploadBytesPerSecond.coerceAtLeast(0L)
    val safeDownloadBytes = downloadBytesPerSecond.coerceAtLeast(0L)
    val uploadMbps = safeUploadBytes / BYTES_PER_MEGABIT
    val downloadMbps = safeDownloadBytes / BYTES_PER_MEGABIT
    return FormattedMetric(
        valueLabel = "${formatOneDecimal(uploadMbps + downloadMbps)} Mbps",
        detailLabel = "上传 ${formatOneDecimal(uploadMbps)} · 下载 ${formatOneDecimal(downloadMbps)}",
    )
}

fun formatBatteryMetric(currentMilliAmps: Int?, batteryPercent: Int?, isCharging: Boolean): FormattedMetric {
    val valueLabel = currentMilliAmps?.let(::formatBatteryCurrent) ?: "不可用"
    val batteryLabel = batteryPercent?.let { "$it%" } ?: "未知电量"
    val detailPrefix = if (isCharging) "充电中" else "放电中"
    return FormattedMetric(
        valueLabel = valueLabel,
        detailLabel = "$detailPrefix · $batteryLabel",
    )
}

fun calculateBatteryPercent(level: Int, scale: Int): Int? {
    if (level < 0 || scale <= 0) return null
    return ((level * 100f) / scale).toInt().coerceIn(0, 100)
}

private fun toGigabytesLabel(bytes: Long): String = formatOneDecimal(bytes / GIGABYTE_BYTES.toFloat())

private fun formatOneDecimal(value: Float): String = "%.1f".format(value)
