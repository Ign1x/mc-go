package com.mcgo.app.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcgo.app.R
import com.mcgo.app.status.buildMetricChartAxis
import com.mcgo.app.status.clampMetricChartAxisYBounds
import com.mcgo.app.status.formatElapsedAxisLabel
import com.mcgo.app.status.DevicePerformanceMonitor
import com.mcgo.app.status.rememberStatusDashboardState
import com.mcgo.app.ui.components.GlassCard
import com.mcgo.app.ui.model.DashboardMetric
import com.mcgo.app.ui.model.MetricAccent
import com.mcgo.app.ui.model.MetricTrendSample
import com.mcgo.app.ui.theme.LocalMcGoVisualTokens
import com.mcgo.app.ui.theme.screenTextColors
import java.util.Locale

@Composable
fun StatusScreen(
    modifier: Modifier = Modifier,
    appEntryElapsedRealtimeMillis: Long,
    statusMonitor: DevicePerformanceMonitor,
    bottomContentPadding: Dp = 0.dp,
) {
    val dashboardState = rememberStatusDashboardState(
        appEntryElapsedRealtimeMillis = appEntryElapsedRealtimeMillis,
        statusMonitor = statusMonitor,
    )

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item {
            SectionTitle(
                title = stringResource(R.string.status_section_title),
                subtitle = stringResource(R.string.status_section_subtitle),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            MetricGrid(
                metrics = dashboardState.metrics,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item { Spacer(modifier = Modifier.height(24.dp + bottomContentPadding)) }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val colors = screenTextColors(LocalMcGoVisualTokens.current)
    Column(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = colors.primary)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondary,
        )
    }
}

@Composable
private fun MetricGrid(metrics: List<DashboardMetric>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        metrics.chunked(2).forEach { rowMetrics ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                rowMetrics.forEach { metric ->
                    MetricCard(
                        metric = metric,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowMetrics.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private val MetricCardMinHeight = 176.dp
private val ChartAxisLabelTextSize = 7.sp
private val ChartElapsedLabelTextSize = 7.sp
private val ChartLeftInset = 18.dp
private val MetricSparklineStartShift = (-6).dp
private val ChartLabelGap = 3.dp

@Composable
private fun MetricCard(metric: DashboardMetric, modifier: Modifier = Modifier) {
    val accent = metricAccentColor(metric.accent)
    val colors = screenTextColors(LocalMcGoVisualTokens.current)
    GlassCard(
        modifier = modifier.aspectRatio(1f),
        contentModifier = Modifier
            .fillMaxHeight()
            .heightIn(min = MetricCardMinHeight),
    ) {
        Text(
            text = metric.title,
            style = MaterialTheme.typography.labelLarge,
            color = colors.secondary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = metric.valueLabel,
            style = MaterialTheme.typography.titleLarge,
            color = colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = metric.detailLabel,
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondary,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.weight(1f))
        MetricSparkline(
            points = metric.trendSamples,
            accent = accent,
            valueLabel = metric.valueLabel,
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = MetricSparklineStartShift)
                .height(72.dp),
        )
    }
}

@Composable
private fun MetricSparkline(
    points: List<MetricTrendSample>,
    accent: Color,
    valueLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = screenTextColors(LocalMcGoVisualTokens.current)
    val gridColor = colors.secondary.copy(alpha = 0.18f)
    val axisColor = colors.secondary.copy(alpha = 0.72f)
    Canvas(modifier = modifier) {
        var axis = buildMetricChartAxis(points)
        if (valueLabel.contains("%")) {
            axis = clampMetricChartAxisYBounds(axis, minimum = 0f, maximum = 100f)
        }
        val xAxisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = axisColor.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = ChartElapsedLabelTextSize.toPx()
        }
        val yAxisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = axisColor.toArgb()
            textAlign = Paint.Align.RIGHT
            textSize = ChartAxisLabelTextSize.toPx()
        }
        val labelOffsetY = ((xAxisLabelPaint.descent() - xAxisLabelPaint.ascent()) / 2f) - xAxisLabelPaint.descent()
        val chartLeft = ChartLeftInset.toPx()
        val chartTop = 4.dp.toPx()
        val chartRight = size.width - 4.dp.toPx()
        val chartBottom = size.height - (ChartElapsedLabelTextSize.toPx() + 8.dp.toPx())
        val chartWidth = (chartRight - chartLeft).coerceAtLeast(1f)
        val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)
        val minValue = axis.yTicks.firstOrNull() ?: 0f
        val maxValue = axis.yTicks.lastOrNull() ?: 1f
        val valueRange = (maxValue - minValue).takeIf { it > 0f } ?: 1f
        val timeRange = (axis.windowEndMillis - axis.windowStartMillis).coerceAtLeast(1L).toFloat()
        val nativeCanvas = drawContext.canvas.nativeCanvas

        fun xForTick(elapsedMillis: Long): Float {
            val ratio = ((elapsedMillis - axis.windowStartMillis).toFloat() / timeRange).coerceIn(0f, 1f)
            return chartLeft + ratio * chartWidth
        }

        fun yForValue(value: Float): Float {
            val ratio = ((value - minValue) / valueRange).coerceIn(0f, 1f)
            return chartBottom - ratio * chartHeight
        }

        axis.yTicks.forEach { tick ->
            val y = yForValue(tick)
            drawLine(
                color = gridColor,
                start = Offset(chartLeft, y),
                end = Offset(chartRight, y),
                strokeWidth = 1f,
            )
            nativeCanvas.drawText(
                formatValueAxisLabel(tick, valueLabel),
                chartLeft - ChartLabelGap.toPx(),
                y + labelOffsetY,
                yAxisLabelPaint,
            )
        }
        axis.xTicks.forEach { tick ->
            val x = xForTick(tick)
            drawLine(
                color = gridColor.copy(alpha = 0.82f),
                start = Offset(x, chartTop),
                end = Offset(x, chartBottom),
                strokeWidth = 1f,
            )
            nativeCanvas.drawText(
                formatElapsedAxisLabel(tick),
                x.coerceIn(chartLeft, chartRight),
                size.height - 2.dp.toPx(),
                xAxisLabelPaint,
            )
        }

        val visiblePoints = points.filter { it.elapsedMillis in axis.windowStartMillis..axis.windowEndMillis }
        if (visiblePoints.none { it.value != null }) return@Canvas
        val segments = mutableListOf<List<Offset>>()
        var currentSegment = mutableListOf<Offset>()
        visiblePoints.forEach { point ->
            val pointValue = point.value
            if (pointValue == null) {
                if (currentSegment.isNotEmpty()) {
                    segments += currentSegment.toList()
                    currentSegment = mutableListOf()
                }
            } else {
                currentSegment += Offset(xForTick(point.elapsedMillis), yForValue(pointValue))
            }
        }
        if (currentSegment.isNotEmpty()) {
            segments += currentSegment.toList()
        }
        if (segments.isEmpty()) return@Canvas
        val lastPoint = segments.last().last()

        segments.forEach { segment ->
            if (segment.isEmpty()) return@forEach
            val linePath = Path()
            val fillPath = Path()
            segment.forEachIndexed { index, point ->
                if (index == 0) {
                    linePath.moveTo(point.x, point.y)
                    fillPath.moveTo(point.x, chartBottom)
                    fillPath.lineTo(point.x, point.y)
                } else {
                    linePath.lineTo(point.x, point.y)
                    fillPath.lineTo(point.x, point.y)
                }
            }
            val segmentLastPoint = segment.last()
            fillPath.lineTo(segmentLastPoint.x, chartBottom)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(accent.copy(alpha = 0.24f), Color.Transparent),
                    startY = chartTop,
                    endY = chartBottom,
                ),
            )
            drawPath(
                path = linePath,
                color = accent,
                style = Stroke(
                    width = 4f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }

        drawCircle(
            color = accent,
            radius = 5.5f,
            center = lastPoint,
        )
    }
}

private fun formatValueAxisLabel(value: Float, valueLabel: String): String = when {
    valueLabel.contains("%") -> String.format(Locale.US, "%.0f%%", value)
    valueLabel.contains("°C") -> String.format(Locale.US, "%.1f", value)
    kotlin.math.abs(value) >= 100f -> value.toInt().toString()
    kotlin.math.abs(value) >= 10f -> String.format(Locale.US, "%.0f", value)
    else -> String.format(Locale.US, "%.1f", value)
}

@Composable
private fun metricAccentColor(accent: MetricAccent): Color = when (accent) {
    MetricAccent.Blue -> MaterialTheme.colorScheme.primary
    MetricAccent.Green -> MaterialTheme.colorScheme.secondary
    MetricAccent.Gold -> MaterialTheme.colorScheme.tertiary
    MetricAccent.Violet -> MaterialTheme.colorScheme.error
    MetricAccent.Coral -> Color(0xFFE76F51)
    MetricAccent.Teal -> Color(0xFF2A9D8F)
}
