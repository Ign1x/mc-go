package com.mcgo.app.status

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.HardwarePropertiesManager
import android.os.SystemClock
import com.mcgo.app.ui.model.DashboardMetric
import com.mcgo.app.ui.model.MetricAccent
import com.mcgo.app.ui.model.MetricTrendSample
import kotlin.math.abs
import kotlin.math.roundToInt
import java.io.File

data class StatusDashboardState(
    val metrics: List<DashboardMetric>,
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
    val batteryTemperatureCelsius: Float?,
    val isCharging: Boolean,
)

class DevicePerformanceMonitor(
    private val context: Context,
    private val appEntryElapsedRealtimeMillis: Long,
) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val hardwarePropertiesManager = context.getSystemService(Context.HARDWARE_PROPERTIES_SERVICE) as? HardwarePropertiesManager

    private var previousCpuSnapshot: CpuStatSnapshot? = readCpuSnapshot()
    private var previousCpuTimestampMillis: Long = SystemClock.elapsedRealtime()
    private var previousNetworkSnapshot: NetworkSnapshot = readNetworkSnapshot()

    private var ramHistory: List<MetricTrendSample> = emptyList()
    private var networkHistory: List<MetricTrendSample> = emptyList()
    private var cpuTemperatureHistory: List<MetricTrendSample> = emptyList()
    private var gpuTemperatureHistory: List<MetricTrendSample> = emptyList()
    private var batteryTemperatureHistory: List<MetricTrendSample> = emptyList()
    private var batteryCurrentHistory: List<MetricTrendSample> = emptyList()

    fun resetSamplingBaselines() {
        previousCpuSnapshot = readCpuSnapshot()
        previousCpuTimestampMillis = SystemClock.elapsedRealtime()
        previousNetworkSnapshot = readNetworkSnapshot()
    }

    fun readDashboardState(): StatusDashboardState {
        val cpuReading = readCpuReading()
        val ramStats = readRamStats()
        val networkReading = readNetworkReading()
        val batteryStats = readBatteryStats()
        val cpuTemperature = readHardwareTemperature(ThermalSensorKind.Cpu)
        val gpuTemperature = readHardwareTemperature(ThermalSensorKind.Gpu)

        val elapsedSinceAppEntry = SystemClock.elapsedRealtime() - appEntryElapsedRealtimeMillis
        ramHistory = appendTimedHistorySample(ramHistory, ramStats.usedBytes / GIGABYTE_BYTES.toFloat(), elapsedSinceAppEntry)
        networkReading.stats?.let {
            val combinedMbps = (it.uploadBytesPerSecond + it.downloadBytesPerSecond) / 125_000f
            networkHistory = appendTimedHistorySample(networkHistory, combinedMbps, elapsedSinceAppEntry)
        }
        cpuTemperature?.let { cpuTemperatureHistory = appendTimedHistorySample(cpuTemperatureHistory, it, elapsedSinceAppEntry) }
        gpuTemperature?.let { gpuTemperatureHistory = appendTimedHistorySample(gpuTemperatureHistory, it, elapsedSinceAppEntry) }
        batteryStats.batteryTemperatureCelsius?.let {
            batteryTemperatureHistory = appendTimedHistorySample(batteryTemperatureHistory, it, elapsedSinceAppEntry)
        }
        batteryStats.currentMilliAmps?.let {
            val normalizedCurrent = if (batteryStats.isCharging) abs(it) else -abs(it)
            batteryCurrentHistory = appendTimedHistorySample(batteryCurrentHistory, normalizedCurrent.toFloat() / 1000f, elapsedSinceAppEntry)
        }

        val ramMetric = buildRamMetric(ramStats)
        val networkMetric = buildNetworkMetric(networkReading)
        val cpuTemperatureMetric = buildCpuTemperatureMetric(cpuTemperature, cpuReading)
        val gpuTemperatureMetric = buildGpuTemperatureMetric(gpuTemperature)
        val batteryTemperatureMetric = buildBatteryTemperatureMetric(batteryStats)
        val batteryCurrentMetric = buildBatteryCurrentMetric(batteryStats)

        return StatusDashboardState(
            metrics = listOf(
                ramMetric,
                networkMetric,
                cpuTemperatureMetric,
                gpuTemperatureMetric,
                batteryTemperatureMetric,
                batteryCurrentMetric,
            ),
        )
    }

    private fun buildCpuTemperatureMetric(cpuTemperature: Float?, cpuReading: CpuReading): DashboardMetric {
        val formatted = formatTemperatureMetric(
            temperatureCelsius = cpuTemperature,
            detailLabel = when (cpuReading.state) {
                SampleState.Ready -> "CPU 负载 ${cpuReading.usagePercent?.roundToInt() ?: 0}% · 最近 2 秒"
                SampleState.WarmingUp -> "CPU 负载采集中 · 等待下一次采样"
                SampleState.Unavailable -> "仅显示热区温度，CPU 占用暂不可用"
            },
            unavailableDetailLabel = "当前设备未公开 CPU 温度传感器",
        )
        return DashboardMetric(
            title = "CPU 温度",
            valueLabel = formatted.valueLabel,
            detailLabel = formatted.detailLabel,
            trendValues = cpuTemperatureHistory.map { it.value },
            accent = MetricAccent.Coral,
            trendSamples = cpuTemperatureHistory,
        )
    }

    private fun buildGpuTemperatureMetric(gpuTemperature: Float?): DashboardMetric {
        val formatted = formatTemperatureMetric(
            temperatureCelsius = gpuTemperature,
            detailLabel = "图形核心热区",
            unavailableDetailLabel = "当前设备未公开 GPU 温度传感器",
        )
        return DashboardMetric(
            title = "GPU 温度",
            valueLabel = formatted.valueLabel,
            detailLabel = formatted.detailLabel,
            trendValues = gpuTemperatureHistory.map { it.value },
            accent = MetricAccent.Violet,
            trendSamples = gpuTemperatureHistory,
        )
    }

    private fun buildBatteryTemperatureMetric(batteryStats: BatteryStats): DashboardMetric {
        val formatted = formatTemperatureMetric(
            temperatureCelsius = batteryStats.batteryTemperatureCelsius,
            detailLabel = batteryStats.batteryPercent?.let { "当前电量 $it%" } ?: "当前电量未知",
            unavailableDetailLabel = "当前设备未返回电池温度",
        )
        return DashboardMetric(
            title = "电池温度",
            valueLabel = formatted.valueLabel,
            detailLabel = formatted.detailLabel,
            trendValues = batteryTemperatureHistory.map { it.value },
            accent = MetricAccent.Gold,
            trendSamples = batteryTemperatureHistory,
        )
    }

    private fun buildBatteryCurrentMetric(batteryStats: BatteryStats): DashboardMetric {
        val formatted = formatBatteryMetric(
            currentMilliAmps = batteryStats.currentMilliAmps,
            batteryPercent = batteryStats.batteryPercent,
            isCharging = batteryStats.isCharging,
        )
        return DashboardMetric(
            title = "电池电流",
            valueLabel = formatted.valueLabel,
            detailLabel = formatted.detailLabel,
            trendValues = batteryCurrentHistory.map { it.value },
            accent = MetricAccent.Teal,
            trendSamples = batteryCurrentHistory,
        )
    }

    private fun buildRamMetric(ramStats: RamStats): DashboardMetric {
        val formatted = formatRamMetric(ramStats.usedBytes, ramStats.totalBytes)
        return DashboardMetric(
            title = "RAM",
            valueLabel = formatted.valueLabel,
            detailLabel = formatted.detailLabel,
            trendValues = ramHistory.map { it.value },
            accent = MetricAccent.Green,
            trendSamples = ramHistory,
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
                SampleState.WarmingUp -> if (networkHistory.isEmpty()) "采集中" else "${networkHistory.last().value.roundToInt()} Mbps"
                SampleState.Unavailable -> "不可用"
            },
            detailLabel = detailLabel,
            trendValues = networkHistory.map { it.value },
            accent = MetricAccent.Blue,
            trendSamples = networkHistory,
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
        val batteryTemperatureRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val batteryTemperatureCelsius = batteryTemperatureRaw.takeUnless { it == Int.MIN_VALUE }
            ?.let(::normalizeThermalCelsius)
        return BatteryStats(
            currentMilliAmps = currentMilliAmps,
            batteryPercent = batteryPercent,
            batteryTemperatureCelsius = batteryTemperatureCelsius,
            isCharging = isCharging,
        )
    }

    private fun readHardwareTemperature(kind: ThermalSensorKind): Float? {
        val frameworkValue = readFrameworkHardwareTemperature(kind)
        return frameworkValue ?: readSysfsHardwareTemperature(kind)
    }

    private fun readFrameworkHardwareTemperature(kind: ThermalSensorKind): Float? {
        val manager = hardwarePropertiesManager ?: return null
        val type = when (kind) {
            ThermalSensorKind.Cpu -> HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU
            ThermalSensorKind.Gpu -> HardwarePropertiesManager.DEVICE_TEMPERATURE_GPU
        }
        return runCatching {
            manager.getDeviceTemperatures(type, HardwarePropertiesManager.TEMPERATURE_CURRENT)
                .filter { it != HardwarePropertiesManager.UNDEFINED_TEMPERATURE }
                .maxOrNull()
        }.getOrNull()
    }

    private fun readSysfsHardwareTemperature(kind: ThermalSensorKind): Float? {
        val thermalRoot = File("/sys/class/thermal")
        val zones = thermalRoot.listFiles()?.filter { it.isDirectory && it.name.startsWith("thermal_zone") }.orEmpty()
        val readings = zones.mapNotNull { zone ->
            val type = runCatching { zone.resolve("type").readText().trim() }.getOrNull() ?: return@mapNotNull null
            val rawValue = runCatching { zone.resolve("temp").readText().trim().toInt() }.getOrNull() ?: return@mapNotNull null
            ThermalSensorReading(type = type, celsius = normalizeThermalCelsius(rawValue))
        }
        return selectThermalCelsius(readings, kind)
    }
}
