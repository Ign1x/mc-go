package com.mcgo.app.ui.screens

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class StatusScreenChartContractTest {
    private val statusScreenSource: String = readSource("app/src/main/java/com/mcgo/app/ui/screens/StatusScreen.kt")
    private val appSource: String = readSource("app/src/main/java/com/mcgo/app/ui/MCGoApp.kt")
    private val stateSource: String = readSource("app/src/main/java/com/mcgo/app/status/StatusScreenState.kt")
    private val monitorSource: String = readSource("app/src/main/java/com/mcgo/app/status/DevicePerformanceMonitor.kt")

    @Test
    fun metricSparklineDrawsGridValueTicksAndElapsedTimelineAxis() {
        val sparkline = statusScreenSource.substringBetween(
            start = "private fun MetricSparkline(",
            end = "@Composable\nprivate fun metricAccentColor",
        )

        assertThat(sparkline).contains("buildMetricChartAxis(points)")
        assertThat(sparkline).contains("axis.yTicks.forEach")
        assertThat(sparkline).contains("axis.xTicks.forEach")
        assertThat(sparkline).contains("drawLine(")
        assertThat(sparkline).contains("nativeCanvas.drawText")
        assertThat(sparkline).contains("formatElapsedAxisLabel")
        assertThat(sparkline).contains("MetricTrendSample")
    }

    @Test
    fun metricSparklineUsesCompactLeftShiftedAxisTypography() {
        val metricCard = statusScreenSource.substringBetween(
            start = "private fun MetricCard(",
            end = "@Composable\nprivate fun MetricSparkline(",
        )
        val sparkline = statusScreenSource.substringBetween(
            start = "private fun MetricSparkline(",
            end = "@Composable\nprivate fun metricAccentColor",
        )

        assertThat(statusScreenSource).contains("private val ChartAxisLabelTextSize = 7.sp")
        assertThat(statusScreenSource).contains("private val ChartElapsedLabelTextSize = 7.sp")
        assertThat(statusScreenSource).contains("private val ChartLeftInset = 18.dp")
        assertThat(statusScreenSource).contains("private val MetricSparklineStartShift = (-6).dp")
        assertThat(statusScreenSource).contains("private val ChartLabelGap = 3.dp")
        assertThat(metricCard).contains("valueLabel = metric.valueLabel")
        assertThat(metricCard).contains(".offset(x = MetricSparklineStartShift)")
        assertThat(sparkline).contains("chartLeft = ChartLeftInset.toPx()")
        assertThat(sparkline).contains("ChartAxisLabelTextSize.toPx()")
        assertThat(sparkline).contains("ChartElapsedLabelTextSize.toPx()")
        assertThat(sparkline).contains("formatValueAxisLabel(tick, valueLabel)")
        assertThat(sparkline).doesNotContain("textSize = 9.sp.toPx()")
        assertThat(sparkline).doesNotContain("chartLeft = 30.dp.toPx()")
        assertThat(sparkline).doesNotContain("private val ChartLeftInset = 12.dp")
    }

    @Test
    fun metricSparklineFormatsRamAsPercentAndTemperatureWithDecimals() {
        assertThat(statusScreenSource).contains("private fun formatValueAxisLabel(value: Float, valueLabel: String): String")
        assertThat(statusScreenSource).contains("valueLabel.contains(\"%\") -> String.format(Locale.US, \"%.0f%%\", value)")
        assertThat(statusScreenSource).contains("valueLabel.contains(\"°C\") -> String.format(Locale.US, \"%.1f\", value)")
        assertThat(monitorSource).contains("usedMemoryPercent(ramStats.usedBytes, ramStats.totalBytes).toFloat()")
        assertThat(monitorSource).doesNotContain("ramStats.usedBytes / GIGABYTE_BYTES.toFloat()")
    }

    @Test
    fun statusTimelineUsesAppEntryTimestampThatSurvivesTabSwitches() {
        val topLevelApp = appSource.substringBetween(
            start = "fun MCGoApp() {",
            end = "@Composable\nprivate fun MCGoAppScaffold(",
        )
        val statusDestination = appSource.substringBetween(
            start = "McGoDestination.Status ->",
            end = "McGoDestination.Tunnels ->",
        )

        assertThat(appSource).contains("import android.os.SystemClock")
        assertThat(topLevelApp).contains("val appEntryElapsedRealtimeMillis = remember { SystemClock.elapsedRealtime() }")
        assertThat(topLevelApp).contains("appEntryElapsedRealtimeMillis = appEntryElapsedRealtimeMillis")
        assertThat(statusDestination).contains("StatusScreen(")
        assertThat(statusDestination).contains("appEntryElapsedRealtimeMillis = appEntryElapsedRealtimeMillis")
        assertThat(statusScreenSource).contains("appEntryElapsedRealtimeMillis: Long")
        assertThat(statusScreenSource).contains("rememberStatusDashboardState(appEntryElapsedRealtimeMillis = appEntryElapsedRealtimeMillis)")
        assertThat(stateSource).contains("fun rememberStatusDashboardState(appEntryElapsedRealtimeMillis: Long)")
        assertThat(stateSource).contains("DevicePerformanceMonitor(appContext, appEntryElapsedRealtimeMillis)")
        assertThat(stateSource).contains("remember(appContext, appEntryElapsedRealtimeMillis)")
        assertThat(monitorSource).contains("class DevicePerformanceMonitor(")
        assertThat(monitorSource).contains("private val appEntryElapsedRealtimeMillis: Long")
        assertThat(monitorSource).doesNotContain("private val appEntryElapsedRealtimeMillis = SystemClock.elapsedRealtime()")
    }

    private fun String.substringBetween(start: String, end: String): String {
        val startIndex = indexOf(start)
        val endIndex = indexOf(end, startIndex.coerceAtLeast(0))
        require(startIndex >= 0) { "Missing start marker: $start" }
        require(endIndex > startIndex) { "Missing end marker after $start: $end" }
        return substring(startIndex, endIndex)
    }

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)))

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}
