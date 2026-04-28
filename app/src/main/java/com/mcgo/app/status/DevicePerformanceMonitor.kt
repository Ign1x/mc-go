package com.mcgo.app.status

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.SystemClock
import com.mcgo.app.ui.model.DashboardMetric
import com.mcgo.app.ui.model.HeroStatus
import com.mcgo.app.ui.model.MetricAccent
import kotlin.math.roundToInt
import java.io.File

data class StatusDashboardState(
    val hero: HeroStatus,
    val metrics: List<DashboardMetric>,
    val events: List<String>,
)

private enum class SampleState {
    Ready,
    WarmingUp,
    Unavailable,
}

private data class CpuReading(
    val usagePercent: Float?,
    val state: SampleState,
)

private data class NetworkSnapshot(
    val rxBytes: Long?,
    val txBytes: Long?,
    val timestampMillis: Long,
)

private data class NetworkStats(
    val uploadBytesPerSecond: Long,
    val downloadBytesPerSecond: Long,
)

private data class NetworkReading(
    val stats: NetworkStats?,
    val state: SampleState,
)

private data class RamStats(
    val usedBytes: Long,
    val totalBytes: Long,
)

private data class BatteryStats(
    val currentMilliAmps: Int?,
    val batteryPercent: Int?,
    val isCharging: Boolean,
)

class DevicePerformanceMonitor(private val context: Context) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val historyLength = 8

    private var previousCpuSnapshot: CpuStatSnapshot? = readCpuSnapshot()
    private var previousCpuTimestampMillis: Long = SystemClock.elapsedRealtime()
    private var previousNetworkSnapshot: NetworkSnapshot = readNetworkSnapshot()

    private var cpuHistory: List<Float> = emptyList()
    private var ramHistory: List<Float> = emptyList()
    private var networkHistory: List<Float> = emptyList()
    private var batteryHistory: List<Float> = emptyList()

    fun resetSamplingBaselines() {
        previousCpuSnapshot = readCpuSnapshot()
        previousCpuTimestampMillis = SystemClock.elapsedRealtime()
        previousNetworkSnapshot = readNetworkSnapshot()
    }

    fun readDashboardState(heroTemplate: HeroStatus): StatusDashboardState {
        val cpuReading = readCpuReading()
        val ramStats = readRamStats()
        val networkReading = readNetworkReading()
        val batteryStats = readBatteryStats()

        cpuReading.usagePercent?.let { cpuHistory = appendHistorySample(cpuHistory, it, historyLength) }
        ramHistory = appendHistorySample(ramHistory, ramStats.usedBytes / GIGABYTE_BYTES.toFloat(), historyLength)
        networkReading.stats?.let {
            val combinedMbps = (it.uploadBytesPerSecond + it.downloadBytesPerSecond) / 125_000f
            networkHistory = appendHistorySample(networkHistory, combinedMbps, historyLength)
        }
        batteryStats.currentMilliAmps?.let {
            batteryHistory = appendHistorySample(batteryHistory, it.toFloat() / 1000f, historyLength)
        }

        val cpuMetric = buildCpuMetric(cpuReading)
        val ramMetric = buildRamMetric(ramStats)
        val networkMetric = buildNetworkMetric(networkReading)
        val batteryMetric = buildBatteryMetric(batteryStats)

        val events = listOf(
            when (cpuReading.state) {
                SampleState.Ready -> "CPU 使用率 ${cpuReading.usagePercent?.roundToInt() ?: 0}% · ${ramMetric.valueLabel}"
                SampleState.WarmingUp -> "CPU 正在建立首个采样窗口"
                SampleState.Unavailable -> "CPU 统计暂不可用"
            },
            when (networkReading.state) {
                SampleState.Ready -> "网络吞吐 ${networkMetric.detailLabel} Mbps"
                SampleState.WarmingUp -> "网络吞吐正在等待下一次采样"
                SampleState.Unavailable -> "当前设备不提供网络吞吐统计"
            },
            "电池状态 ${batteryMetric.detailLabel}",
        )

        return StatusDashboardState(
            hero = heroTemplate,
            metrics = listOf(cpuMetric, ramMetric, networkMetric, batteryMetric),
            events = events,
        )
    }

    private fun buildCpuMetric(cpuReading: CpuReading): DashboardMetric {
        val latestPercent = cpuReading.usagePercent ?: cpuHistory.lastOrNull()
        val detailLabel = when (cpuReading.state) {
            SampleState.Ready -> "${Runtime.getRuntime().availableProcessors()} 核设备 · 最近 2 秒"
            SampleState.WarmingUp -> "${Runtime.getRuntime().availableProcessors()} 核设备 · 等待下一次采样"
            SampleState.Unavailable -> "当前设备无法读取 CPU 统计"
        }
        return DashboardMetric(
            title = "CPU",
            valueLabel = when (cpuReading.state) {
                SampleState.Ready -> "${cpuReading.usagePercent?.roundToInt() ?: 0}%"
                SampleState.WarmingUp -> latestPercent?.let { "${it.roundToInt()}%" } ?: "采集中"
                SampleState.Unavailable -> "不可用"
            },
            detailLabel = detailLabel,
            trendValues = cpuHistory,
            accent = MetricAccent.Blue,
        )
    }

    private fun buildRamMetric(ramStats: RamStats): DashboardMetric {
        val formatted = formatRamMetric(ramStats.usedBytes, ramStats.totalBytes)
        return DashboardMetric(
            title = "RAM",
            valueLabel = formatted.valueLabel,
            detailLabel = formatted.detailLabel,
            trendValues = ramHistory,
            accent = MetricAccent.Green,
        )
    }

    private fun buildNetworkMetric(networkReading: NetworkReading): DashboardMetric {
        val formatted = networkReading.stats?.let {
            formatNetworkMetric(it.uploadBytesPerSecond, it.downloadBytesPerSecond)
        }
        val detailLabel = when (networkReading.state) {
            SampleState.Ready -> formatted?.detailLabel ?: "上传 0.0 · 下载 0.0"
            SampleState.WarmingUp -> "等待下一次吞吐采样"
            SampleState.Unavailable -> "当前设备不提供吞吐统计"
        }
        return DashboardMetric(
            title = "Network I/O",
            valueLabel = when (networkReading.state) {
                SampleState.Ready -> formatted?.valueLabel ?: "0.0 Mbps"
                SampleState.WarmingUp -> if (networkHistory.isEmpty()) "采集中" else "${networkHistory.last().roundToInt()} Mbps"
                SampleState.Unavailable -> "不可用"
            },
            detailLabel = detailLabel,
            trendValues = networkHistory,
            accent = MetricAccent.Violet,
        )
    }

    private fun buildBatteryMetric(batteryStats: BatteryStats): DashboardMetric {
        val formatted = formatBatteryMetric(
            currentMilliAmps = batteryStats.currentMilliAmps,
            batteryPercent = batteryStats.batteryPercent,
            isCharging = batteryStats.isCharging,
        )
        return DashboardMetric(
            title = "Battery Current",
            valueLabel = formatted.valueLabel,
            detailLabel = formatted.detailLabel,
            trendValues = batteryHistory,
            accent = MetricAccent.Gold,
        )
    }

    private fun readCpuReading(): CpuReading {
        val currentSnapshot = readCpuSnapshot() ?: return CpuReading(null, SampleState.Unavailable)
        val currentTimestampMillis = SystemClock.elapsedRealtime()
        val previousSnapshot = previousCpuSnapshot
        val elapsedMillis = currentTimestampMillis - previousCpuTimestampMillis
        previousCpuSnapshot = currentSnapshot
        previousCpuTimestampMillis = currentTimestampMillis
        if (previousSnapshot == null || elapsedMillis < 1_000L) {
            return CpuReading(null, SampleState.WarmingUp)
        }
        return CpuReading(
            usagePercent = calculateCpuUsagePercent(previousSnapshot, currentSnapshot),
            state = SampleState.Ready,
        )
    }

    private fun readCpuSnapshot(): CpuStatSnapshot? = runCatching {
        val firstLine = File("/proc/stat").useLines { lines -> lines.firstOrNull() ?: return null }
        val parts = firstLine.trim().split(Regex("\\s+"))
        if (parts.size < 6 || parts.first() != "cpu") return null
        val values = parts.drop(1).map { it.toLong() }
        val idleJiffies = values.getOrElse(3) { 0L } + values.getOrElse(4) { 0L }
        CpuStatSnapshot(totalJiffies = values.sum(), idleJiffies = idleJiffies)
    }.getOrNull()

    private fun readRamStats(): RamStats {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalBytes = memoryInfo.totalMem.coerceAtLeast(1L)
        val usedBytes = (totalBytes - memoryInfo.availMem).coerceIn(0L, totalBytes)
        return RamStats(usedBytes = usedBytes, totalBytes = totalBytes)
    }

    private fun readNetworkReading(): NetworkReading {
        val currentSnapshot = readNetworkSnapshot()
        val previousSnapshot = previousNetworkSnapshot
        previousNetworkSnapshot = currentSnapshot

        val currentRxBytes = currentSnapshot.rxBytes
        val currentTxBytes = currentSnapshot.txBytes
        val previousRxBytes = previousSnapshot.rxBytes
        val previousTxBytes = previousSnapshot.txBytes
        if (currentRxBytes == null || currentTxBytes == null || previousRxBytes == null || previousTxBytes == null) {
            return NetworkReading(null, SampleState.Unavailable)
        }

        val elapsedMillis = (currentSnapshot.timestampMillis - previousSnapshot.timestampMillis).coerceAtLeast(1L)
        if (elapsedMillis < 1_000L) {
            return NetworkReading(null, SampleState.WarmingUp)
        }

        val uploadDelta = (currentTxBytes - previousTxBytes).coerceAtLeast(0L)
        val downloadDelta = (currentRxBytes - previousRxBytes).coerceAtLeast(0L)
        return NetworkReading(
            stats = NetworkStats(
                uploadBytesPerSecond = uploadDelta * 1000L / elapsedMillis,
                downloadBytesPerSecond = downloadDelta * 1000L / elapsedMillis,
            ),
            state = SampleState.Ready,
        )
    }

    private fun readNetworkSnapshot(): NetworkSnapshot = NetworkSnapshot(
        rxBytes = TrafficStats.getTotalRxBytes().takeUnless { it == TrafficStats.UNSUPPORTED.toLong() },
        txBytes = TrafficStats.getTotalTxBytes().takeUnless { it == TrafficStats.UNSUPPORTED.toLong() },
        timestampMillis = SystemClock.elapsedRealtime(),
    )

    private fun readBatteryStats(): BatteryStats {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val currentMicroAmps = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMilliAmps = if (currentMicroAmps == Int.MIN_VALUE) null else currentMicroAmps / 1000
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = calculateBatteryPercent(level = level, scale = scale)
        val chargingStatus = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = chargingStatus == BatteryManager.BATTERY_STATUS_CHARGING || chargingStatus == BatteryManager.BATTERY_STATUS_FULL
        return BatteryStats(
            currentMilliAmps = currentMilliAmps,
            batteryPercent = batteryPercent,
            isCharging = isCharging,
        )
    }
}
