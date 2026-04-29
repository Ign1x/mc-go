package com.mcgo.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mcgo.app.R
import com.mcgo.app.status.rememberStatusDashboardState
import com.mcgo.app.ui.components.GlassCard
import com.mcgo.app.ui.model.DashboardMetric
import com.mcgo.app.ui.model.HeroStatus
import com.mcgo.app.ui.model.MetricAccent
import com.mcgo.app.ui.model.formatPlayerCapacity
import com.mcgo.app.ui.model.formatRuntime
import com.mcgo.app.ui.theme.Blue500
import com.mcgo.app.ui.theme.Gold500
import com.mcgo.app.ui.theme.Green500
import com.mcgo.app.ui.theme.Ink600
import com.mcgo.app.ui.theme.Violet500

@Composable
fun StatusScreen(modifier: Modifier = Modifier) {
    val dashboardState = rememberStatusDashboardState()

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            HeroStatusCard(
                hero = dashboardState.hero,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
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
        item {
            EventCard(
                events = dashboardState.events,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeroStatusCard(hero: HeroStatus, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatusBadge(text = hero.statusLabel)
            Text(
                text = formatPlayerCapacity(hero.onlinePlayers, hero.maxPlayers),
                style = MaterialTheme.typography.labelMedium,
                color = Ink600,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = hero.activeServerName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.status_hero_sentence, formatRuntime(hero.uptimeMinutes)),
            style = MaterialTheme.typography.bodyMedium,
            color = Ink600,
        )
        Spacer(modifier = Modifier.height(18.dp))
        LinearProgressIndicator(
            progress = { hero.onlinePlayers / hero.maxPlayers.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = Green500,
            trackColor = Green500.copy(alpha = 0.16f),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatKpi(
                title = stringResource(R.string.status_kpi_online_players),
                value = hero.onlinePlayers.toString(),
                modifier = Modifier.weight(1f),
            )
            StatKpi(
                title = stringResource(R.string.status_kpi_capacity),
                value = hero.maxPlayers.toString(),
                modifier = Modifier.weight(1f),
            )
            StatKpi(
                title = stringResource(R.string.status_kpi_health),
                value = stringResource(R.string.status_kpi_health_value),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Ink600,
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

private val MetricCardHeight = 214.dp

@Composable
private fun MetricCard(metric: DashboardMetric, modifier: Modifier = Modifier) {
    val accent = metricAccentColor(metric.accent)
    GlassCard(
        modifier = modifier.height(MetricCardHeight),
        contentModifier = Modifier.fillMaxHeight(),
    ) {
        Text(
            text = metric.title,
            style = MaterialTheme.typography.labelLarge,
            color = Ink600,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = metric.valueLabel,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = metric.detailLabel,
            style = MaterialTheme.typography.bodySmall,
            color = Ink600,
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
        val fillPath = Path().apply {
            moveTo(0f, size.height)
        }
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
private fun EventCard(events: List<String>, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Text(text = stringResource(R.string.recent_events_title), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(10.dp))
        events.forEach { event ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Green500, CircleShape),
                )
                Text(
                    text = event,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink600,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun StatusBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Green500.copy(alpha = 0.15f),
        contentColor = Green500,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun StatKpi(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.labelMedium, color = Ink600)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

private fun metricAccentColor(accent: MetricAccent): Color = when (accent) {
    MetricAccent.Blue -> Blue500
    MetricAccent.Green -> Green500
    MetricAccent.Gold -> Gold500
    MetricAccent.Violet -> Violet500
}
