package com.mcgo.app.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mcgo.app.ui.model.DashboardMetric
import com.mcgo.app.ui.model.HeroStatus
import com.mcgo.app.ui.model.MetricAccent
import com.mcgo.app.ui.sample.McGoSampleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun rememberStatusDashboardState(): StatusDashboardState {
    val appContext = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val monitor = remember(appContext) { DevicePerformanceMonitor(appContext) }
    val heroTemplate = remember { McGoSampleRepository.heroStatus() }
    var isStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    DisposableEffect(lifecycleOwner, monitor) {
        val observer = LifecycleEventObserver { source, _ ->
            isStarted = source.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (!isStarted) {
                monitor.resetSamplingBaselines()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val initialState = remember(heroTemplate) { placeholderDashboardState(heroTemplate) }
    return produceState(initialValue = initialState, monitor, heroTemplate, isStarted) {
        while (true) {
            if (isStarted) {
                value = withContext(Dispatchers.IO) { monitor.readDashboardState(heroTemplate) }
            }
            delay(2_000L)
        }
    }.value
}

private fun placeholderDashboardState(heroTemplate: HeroStatus) = StatusDashboardState(
    hero = heroTemplate,
    metrics = listOf(
        placeholderMetric(title = "CPU", accent = MetricAccent.Blue),
        placeholderMetric(title = "RAM", accent = MetricAccent.Green),
        placeholderMetric(title = "Network I/O", accent = MetricAccent.Violet),
        placeholderMetric(title = "Battery Current", accent = MetricAccent.Gold),
    ),
    events = listOf(
        "正在读取 CPU 与内存状态",
        "正在等待网络吞吐采样",
        "正在读取电池状态",
    ),
)

private fun placeholderMetric(title: String, accent: MetricAccent) = DashboardMetric(
    title = title,
    valueLabel = "采集中",
    detailLabel = "准备首次实时采样",
    trendValues = emptyList(),
    accent = accent,
)
