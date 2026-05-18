package com.mcgo.app.status

import com.mcgo.app.ui.model.MetricTrendSample
import com.mcgo.app.ui.model.formatBatteryCurrent
import com.mcgo.app.ui.model.usedMemoryPercent
import java.util.Locale
import kotlin.math.max

const val GIGABYTE_BYTES: Long = 1024L * 1024L * 1024L
private const val BYTES_PER_MEGABIT = 125_000f

enum class ThermalSensorKind {
    Cpu,
    Gpu,
}

data class ThermalSensorReading(
    val type: String,
    val celsius: Float,
)

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

const val MetricHistoryMaxWindowMillis: Long = 60 * 60_000L

data class MetricChartAxis(
    val windowStartMillis: Long,
    val windowEndMillis: Long,
    val xTicks: List<Long>,
    val yTicks: List<Float>,
)

fun appendTimedHistorySample(
    history: List<MetricTrendSample>,
    nextValue: Float?,
    elapsedMillis: Long,
    maxWindowMillis: Long = MetricHistoryMaxWindowMillis,
): List<MetricTrendSample> {
    val safeElapsedMillis = elapsedMillis.coerceAtLeast(0L)
    val windowStart = (safeElapsedMillis - maxWindowMillis.coerceAtLeast(1L)).coerceAtLeast(0L)
    return (history + MetricTrendSample(safeElapsedMillis, nextValue))
        .filter { it.elapsedMillis >= windowStart && it.elapsedMillis <= safeElapsedMillis }
}

fun buildMetricChartAxis(samples: List<MetricTrendSample>): MetricChartAxis {
    val latestElapsed = samples.maxOfOrNull { it.elapsedMillis } ?: 0L
    val windowEnd = latestElapsed
    val windowStart = (windowEnd - MetricHistoryMaxWindowMillis).coerceAtLeast(0L)
    val visibleValues = samples
        .filter { it.elapsedMillis in windowStart..windowEnd }
        .mapNotNull { it.value }
    val minValue = visibleValues.minOrNull() ?: 0f
    val maxValue = visibleValues.maxOrNull() ?: 1f
    val paddedRange = ((maxValue - minValue).takeIf { it > 0f } ?: 1f) * 0.12f
    val axisMin = (minValue - paddedRange).coerceAtMost(minValue)
    val axisMax = (maxValue + paddedRange).coerceAtLeast(maxValue + 0.001f)
    val tickStep = (axisMax - axisMin) / 3f
    return MetricChartAxis(
        windowStartMillis = windowStart,
        windowEndMillis = windowEnd,
        xTicks = (0..3).map { windowStart + ((windowEnd - windowStart) * it / 3) },
        yTicks = (0..3).map { axisMin + tickStep * it },
    )
}

fun clampMetricChartAxisYBounds(
    axis: MetricChartAxis,
    minimum: Float,
    maximum: Float,
): MetricChartAxis = axis.copy(
    yTicks = axis.yTicks.map { it.coerceIn(minimum, maximum) },
)

fun formatElapsedAxisLabel(elapsedMillis: Long): String {
    val safeMillis = elapsedMillis.coerceAtLeast(0L)
    val totalSeconds = safeMillis / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes == 0L) {
        "${seconds}s"
    } else {
        "${minutes}m"
    }
}

fun formatRamMetric(usedBytes: Long, totalBytes: Long): FormattedMetric {
    val safeTotalBytes = totalBytes.coerceAtLeast(1L)
    val safeUsedBytes = usedBytes.coerceIn(0L, safeTotalBytes)
    return FormattedMetric(
        valueLabel = "${usedMemoryPercent(safeUsedBytes, safeTotalBytes)}%",
        detailLabel = "${toGigabytesLabel(safeUsedBytes)} / ${toGigabytesLabel(safeTotalBytes)} GB",
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

fun formatTemperatureMetric(
    temperatureCelsius: Float?,
    detailLabel: String,
    unavailableDetailLabel: String = detailLabel,
): FormattedMetric = if (temperatureCelsius == null) {
    FormattedMetric(
        valueLabel = "不可用",
        detailLabel = unavailableDetailLabel,
    )
} else {
    FormattedMetric(
        valueLabel = "${formatOneDecimal(temperatureCelsius)}°C",
        detailLabel = detailLabel,
    )
}

fun formatBatteryMetric(currentMilliAmps: Int?, batteryPercent: Int?, isCharging: Boolean): FormattedMetric {
    val normalizedCurrent = currentMilliAmps?.let { current ->
        if (isCharging) kotlin.math.abs(current) else -kotlin.math.abs(current)
    }
    val valueLabel = normalizedCurrent?.let(::formatBatteryCurrent) ?: "不可用"
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

fun normalizeThermalCelsius(rawValue: Int): Float = when {
    rawValue >= 1_000 -> rawValue / 1_000f
    rawValue >= 150 -> rawValue / 10f
    else -> rawValue.toFloat()
}

fun selectThermalReading(
    readings: List<ThermalSensorReading>,
    kind: ThermalSensorKind,
): ThermalSensorReading? = readings
    .filter { matchesThermalKind(it.type, kind) }
    .maxByOrNull { it.celsius }

fun selectThermalCelsius(
    readings: List<ThermalSensorReading>,
    kind: ThermalSensorKind,
): Float? = selectThermalReading(readings, kind)?.celsius

private fun matchesThermalKind(type: String, kind: ThermalSensorKind): Boolean {
    val normalized = type.lowercase()
    return when (kind) {
        ThermalSensorKind.Cpu -> CpuThermalAliases.any { normalized.contains(it) }
        ThermalSensorKind.Gpu -> GpuThermalAliases.any { normalized.contains(it) }
    }
}

private fun toGigabytesLabel(bytes: Long): String = formatOneDecimal(bytes / GIGABYTE_BYTES.toFloat())

private fun formatOneDecimal(value: Float): String = String.format(Locale.US, "%.1f", value)

private val CpuThermalAliases = listOf(
    "cpu",
    "soc",
    "ap",
    "cluster",
    "big",
    "little",
    "gold",
    "silver",
    "tsens",
)

private val GpuThermalAliases = listOf(
    "gpu",
    "adreno",
    "kgsl",
    "gpuss",
)
