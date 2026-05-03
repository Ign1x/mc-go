package com.mcgo.app.status

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class PerformanceMathTest {

    @Test
    fun calculateCpuUsagePercent_usesBusyDeltaBetweenSnapshots() {
        val previous = CpuStatSnapshot(totalJiffies = 1_000L, idleJiffies = 300L)
        val current = CpuStatSnapshot(totalJiffies = 1_400L, idleJiffies = 380L)

        assertThat(calculateCpuUsagePercent(previous, current)).isWithin(0.001f).of(80f)
    }

    @Test
    fun appendHistorySample_keepsOnlyTheLatestEightSamples() {
        val original = (1..8).map(Int::toFloat)

        assertThat(appendHistorySample(original, nextValue = 9f, maxPoints = 8))
            .containsExactly(2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f)
            .inOrder()
    }

    @Test
    fun formatRamMetric_prefersCompactPercentPlusUsedCapacityDetail() {
        val formatted = formatRamMetric(usedBytes = 3L * GIGABYTE_BYTES, totalBytes = 8L * GIGABYTE_BYTES)

        assertThat(formatted.valueLabel).isEqualTo("38%")
        assertThat(formatted.detailLabel).isEqualTo("3.0 / 8.0 GB")
    }

    @Test
    fun formatNetworkMetric_formatsCombinedThroughputAndDirectionalDetail() {
        val formatted = formatNetworkMetric(uploadBytesPerSecond = 500_000L, downloadBytesPerSecond = 1_000_000L)

        assertThat(formatted.valueLabel).isEqualTo("12.0 Mbps")
        assertThat(formatted.detailLabel).isEqualTo("上传 4.0 · 下载 8.0")
    }

    @Test
    fun formatBatteryMetric_usesExplicitCurrentAndChargingDetail() {
        val formatted = formatBatteryMetric(currentMilliAmps = 1240, batteryPercent = 78, isCharging = true)

        assertThat(formatted.valueLabel).isEqualTo("+1240 mA")
        assertThat(formatted.detailLabel).isEqualTo("充电中 · 78%")
    }


    @Test
    fun formatBatteryMetric_normalizesPositiveCurrentAsDischargeWhenNotCharging() {
        val formatted = formatBatteryMetric(currentMilliAmps = 820, batteryPercent = 54, isCharging = false)

        assertThat(formatted.valueLabel).isEqualTo("-820 mA")
        assertThat(formatted.detailLabel).isEqualTo("放电中 · 54%")
    }

    @Test
    fun calculateBatteryPercent_scalesLevelAgainstBatteryScale() {
        assertThat(calculateBatteryPercent(level = 37, scale = 50)).isEqualTo(74)
    }

    @Test
    fun normalizeThermalCelsius_supportsMilliDeciAndDirectCelsiusValues() {
        assertThat(normalizeThermalCelsius(42_500)).isWithin(0.001f).of(42.5f)
        assertThat(normalizeThermalCelsius(435)).isWithin(0.001f).of(43.5f)
        assertThat(normalizeThermalCelsius(44)).isWithin(0.001f).of(44f)
    }

    @Test
    fun selectThermalCelsius_prefersMatchingSensorFamilyAndReturnsHottestReading() {
        val readings = listOf(
            ThermalSensorReading(type = "cpu-0-0", celsius = 41.2f),
            ThermalSensorReading(type = "soc", celsius = 44.8f),
            ThermalSensorReading(type = "gpu", celsius = 39.6f),
            ThermalSensorReading(type = "gpuss-1", celsius = 42.3f),
        )

        assertThat(selectThermalCelsius(readings, ThermalSensorKind.Cpu)).isWithin(0.001f).of(44.8f)
        assertThat(selectThermalCelsius(readings, ThermalSensorKind.Gpu)).isWithin(0.001f).of(42.3f)
    }

    @Test
    fun formatTemperatureMetric_usesReadableDegreeLabelAndContextDetail() {
        val formatted = formatTemperatureMetric(temperatureCelsius = 41.75f, detailLabel = "SoC 热区")

        assertThat(formatted.valueLabel).isEqualTo("41.8°C")
        assertThat(formatted.detailLabel).isEqualTo("SoC 热区")
    }
}
