package com.mcgo.app.ui.screens

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mcgo.app.R
import com.mcgo.app.status.rememberStatusDashboardState
import com.mcgo.app.ui.components.GlassCard
import com.mcgo.app.ui.model.DashboardMetric
import com.mcgo.app.ui.model.MetricAccent
import com.mcgo.app.ui.theme.LocalMcGoVisualTokens
import com.mcgo.app.ui.theme.screenTextColors

@Composable
fun StatusScreen(modifier: Modifier = Modifier) {
    val dashboardState = rememberStatusDashboardState()

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
        item { Spacer(modifier = Modifier.height(24.dp)) }
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
            points = metric.trendValues,
            accent = accent,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
        )
    }
}

@Composable
private fun MetricSparkline(points: List<Float>, accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas
        val maxValue = points.maxOrNull() ?: 0f
        val minValue = points.minOrNull() ?: 0f
        val range = (maxValue - minValue).takeIf { it > 0f } ?: 1f
        val stepX = size.width / points.lastIndex.coerceAtLeast(1)

        val linePath = Path()
        val fillPath = Path().apply { moveTo(0f, size.height) }
        var lastPoint = Offset.Zero

        points.forEachIndexed { index, point ->
            val x = stepX * index
            val normalized = (point - minValue) / range
            val y = size.height - (normalized * size.height)
            lastPoint = Offset(x, y)
            if (index == 0) {
                linePath.moveTo(x, y)
            } else {
                linePath.lineTo(x, y)
            }
            fillPath.lineTo(x, y)
        }

        fillPath.lineTo(size.width, size.height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(accent.copy(alpha = 0.28f), Color.Transparent),
                endY = size.height,
            ),
        )
        drawPath(
            path = linePath,
            color = accent,
            style = Stroke(
                width = 5f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        drawCircle(
            color = accent,
            radius = 7f,
            center = lastPoint,
        )
    }
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
