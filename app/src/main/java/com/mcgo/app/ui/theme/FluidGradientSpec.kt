package com.mcgo.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class FluidGradientBlobSpec(
    val colorHex: Long,
    val startXFraction: Float,
    val startYFraction: Float,
    val endXFraction: Float,
    val endYFraction: Float,
    val startRadiusFraction: Float,
    val endRadiusFraction: Float,
    val durationMillis: Int,
    val delayMillis: Int = 0,
    val alpha: Float,
) {
    fun color(): Color = Color(colorHex)
}

@Immutable
data class FluidGradientNoiseOverlaySpec(
    val opacity: Float,
    val tileSizePx: Int,
    val seed: Int,
    val blendModeName: String,
)

@Immutable
data class FluidGradientSpec(
    val backdropHexes: List<Long>,
    val overlayHex: Long,
    val overlayAlpha: Float,
    val blurRadiusDp: Float,
    val noiseOverlay: FluidGradientNoiseOverlaySpec,
    val blobs: List<FluidGradientBlobSpec>,
) {
    fun backdropColors(): List<Color> = backdropHexes.map(::Color)

    fun overlayColor(): Color = Color(overlayHex).copy(alpha = overlayAlpha)
}

fun fluidGradientSpec(darkTheme: Boolean): FluidGradientSpec = if (darkTheme) {
    DarkFluidGradientSpec
} else {
    LightFluidGradientSpec
}

private val DitherNoiseOverlaySpec = FluidGradientNoiseOverlaySpec(
    opacity = 0.04f,
    tileSizePx = 64,
    seed = 20_260_429,
    blendModeName = "Overlay",
)

private val LightFluidGradientSpec = FluidGradientSpec(
    backdropHexes = listOf(
        0xFFF8F4EE,
        0xFFEEF5FF,
        0xFFF2EEFF,
    ),
    overlayHex = 0xFFFFFFFF,
    overlayAlpha = 0.12f,
    blurRadiusDp = 220f,
    noiseOverlay = DitherNoiseOverlaySpec,
    blobs = listOf(
        FluidGradientBlobSpec(
            colorHex = 0xFFD8E8FF,
            startXFraction = 0.16f,
            startYFraction = 0.22f,
            endXFraction = 0.82f,
            endYFraction = 0.18f,
            startRadiusFraction = 0.48f,
            endRadiusFraction = 0.58f,
            durationMillis = 12_000,
            alpha = 0.52f,
        ),
        FluidGradientBlobSpec(
            colorHex = 0xFFE2DBFF,
            startXFraction = 0.84f,
            startYFraction = 0.34f,
            endXFraction = 0.26f,
            endYFraction = 0.82f,
            startRadiusFraction = 0.42f,
            endRadiusFraction = 0.56f,
            durationMillis = 14_000,
            delayMillis = 900,
            alpha = 0.48f,
        ),
        FluidGradientBlobSpec(
            colorHex = 0xFFF7EEDD,
            startXFraction = 0.28f,
            startYFraction = 0.86f,
            endXFraction = 0.74f,
            endYFraction = 0.44f,
            startRadiusFraction = 0.44f,
            endRadiusFraction = 0.62f,
            durationMillis = 15_000,
            delayMillis = 1600,
            alpha = 0.36f,
        ),
    ),
)

private val DarkFluidGradientSpec = FluidGradientSpec(
    backdropHexes = listOf(
        0xFF1C1C1E,
        0xFF1E1E1E,
        0xFF252525,
    ),
    overlayHex = 0xFF1A1A1C,
    overlayAlpha = 0.18f,
    blurRadiusDp = 220f,
    noiseOverlay = DitherNoiseOverlaySpec,
    blobs = listOf(
        FluidGradientBlobSpec(
            colorHex = 0xFF7887A0,
            startXFraction = 0.16f,
            startYFraction = 0.20f,
            endXFraction = 0.82f,
            endYFraction = 0.18f,
            startRadiusFraction = 0.46f,
            endRadiusFraction = 0.56f,
            durationMillis = 12_000,
            alpha = 0.20f,
        ),
        FluidGradientBlobSpec(
            colorHex = 0xFF877EA1,
            startXFraction = 0.84f,
            startYFraction = 0.34f,
            endXFraction = 0.24f,
            endYFraction = 0.80f,
            startRadiusFraction = 0.40f,
            endRadiusFraction = 0.54f,
            durationMillis = 14_000,
            delayMillis = 900,
            alpha = 0.18f,
        ),
        FluidGradientBlobSpec(
            colorHex = 0xFFB2A796,
            startXFraction = 0.30f,
            startYFraction = 0.84f,
            endXFraction = 0.74f,
            endYFraction = 0.42f,
            startRadiusFraction = 0.42f,
            endRadiusFraction = 0.58f,
            durationMillis = 15_000,
            delayMillis = 1600,
            alpha = 0.14f,
        ),
    ),
)
