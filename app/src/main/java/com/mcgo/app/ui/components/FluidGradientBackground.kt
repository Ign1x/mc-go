package com.mcgo.app.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.mcgo.app.ui.theme.FluidGradientSpec

@Composable
fun FluidGradientBackground(
    spec: FluidGradientSpec,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val blurPx = with(LocalDensity.current) { spec.blurRadiusDp.dp.toPx() }
    val backdropColors = remember(spec) { spec.backdropColors() }
    val overlayColor = remember(spec) { spec.overlayColor() }
    val blobProgress = if (animate) {
        val transition = rememberInfiniteTransition(label = "fluid-gradient")
        spec.blobs.mapIndexed { index, blob ->
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = blob.durationMillis,
                        delayMillis = blob.delayMillis,
                        easing = LinearEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "fluid-gradient-blob-$index",
            )
        }
    } else {
        emptyList()
    }

    Box(modifier = modifier.background(Brush.linearGradient(colors = backdropColors))) {
        if (animate) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawIntoCanvas { canvas ->
                    val nativeCanvas = canvas.nativeCanvas
                    spec.blobs.forEachIndexed { index, blob ->
                        val progress = blobProgress[index].value
                        val centerX = lerp(blob.startXFraction, blob.endXFraction, progress) * size.width
                        val centerY = lerp(blob.startYFraction, blob.endYFraction, progress) * size.height
                        val radius = lerp(blob.startRadiusFraction, blob.endRadiusFraction, progress) * size.minDimension
                        val paint = Paint().apply {
                            isAntiAlias = true
                            color = blob.color().copy(alpha = blob.alpha).toArgb()
                            maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
                        }
                        nativeCanvas.drawCircle(centerX, centerY, radius, paint)
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(overlayColor),
        )
    }
}
