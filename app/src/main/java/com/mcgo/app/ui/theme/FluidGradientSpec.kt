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

private val LiquidNoiseOverlaySpec = FluidGradientNoiseOverlaySpec(
    opacity = 0.010f,
    tileSizePx = 128,
    seed = 20_260_504,
    blendModeName = "Overlay",
)

private val LightFluidGradientSpec = FluidGradientSpec(
    backdropHexes = listOf(
        0xFFF8FAFF,
        0xFFFFF7FB,
        0xFFF3FBFF,
        0xFFFFFCF1,
        0xFFF6F4FF,
    ),
    overlayHex = 0xFFFFFFFF,
    overlayAlpha = 0.06f,
    blurRadiusDp = 280f,
    noiseOverlay = LiquidNoiseOverlaySpec,
    blobs = listOf(
        FluidGradientBlobSpec(
            colorHex = 0xFFFF7AB8,
            startXFraction = 0.08f,
            startYFraction = 0.12f,
            endXFraction = 0.72f,
            endYFraction = 0.18f,
            startRadiusFraction = 0.36f,
            endRadiusFraction = 0.48f,
            durationMillis = 28_000,
            alpha = 0.18f,
        ),
        FluidGradientBlobSpec(
            colorHex = 0xFF60D7FF,
            startXFraction = 0.90f,
            startYFraction = 0.20f,
            endXFraction = 0.36f,
            endYFraction = 0.36f,
            startRadiusFraction = 0.32f,
            endRadiusFraction = 0.44f,
            durationMillis = 24_000,
            delayMillis = 1_400,
            alpha = 0.20f,
        ),
        FluidGradientBlobSpec(
            colorHex = 0xFF75F0BE,
            startXFraction = 0.18f,
            startYFraction = 0.84f,
            endXFraction = 0.72f,
            endYFraction = 0.74f,
            startRadiusFraction = 0.38f,
            endRadiusFraction = 0.50f,
            durationMillis = 31_000,
            delayMillis = 2_200,
            alpha = 0.16f,
        ),
        FluidGradientBlobSpec(
            colorHex = 0xFFFFD166,
            startXFraction = 0.88f,
            startYFraction = 0.90f,
            endXFraction = 0.24f,
            endYFraction = 0.68f,
            startRadiusFraction = 0.28f,
            endRadiusFraction = 0.40f,
            durationMillis = 26_000,
            delayMillis = 3_200,
            alpha = 0.14f,
        ),
        FluidGradientBlobSpec(
            colorHex = 0xFF9B8CFF,
            startXFraction = 0.50f,
            startYFraction = 0.44f,
            endXFraction = 0.18f,
            endYFraction = 0.32f,
            startRadiusFraction = 0.26f,
            endRadiusFraction = 0.38f,
            durationMillis = 33_000,
            delayMillis = 4_800,
            alpha = 0.13f,
        ),
    ),
)

private val DarkFluidGradientSpec = FluidGradientSpec(
    backdropHexes = listOf(
        0xFF0B0D12,
        0xFF111827,
        0xFF17111F,
        0xFF0E1B1E,
    ),
    overlayHex = 0xFF05070B,
    overlayAlpha = 0.16f,
    blurRadiusDp = 300f,
    noiseOverlay = LiquidNoiseOverlaySpec,
    blobs = listOf(
        FluidGradientBlobSpec(
            colorHex = 0xFF2E7CFF,
            startXFraction = 0.08f,
            startYFraction = 0.16f,
            endXFraction = 0.76f,
            endYFraction = 0.12f,
            startRadiusFraction = 0.40f,
            endRadiusFraction = 0.52f,
            durationMillis = 30_000,
            alpha = 0.16f,
        ),
        FluidGradientBlobSpec(
            colorHex = 0xFF13D8C8,
            startXFraction = 0.92f,
            startYFraction = 0.82f,
            endXFraction = 0.26f,
            endYFraction = 0.72f,
            startRadiusFraction = 0.34f,
            endRadiusFraction = 0.44f,
            durationMillis = 27_000,
            delayMillis = 1_800,
            alpha = 0.14f,
        ),
        FluidGradientBlobSpec(
            colorHex = 0xFF8A5CFF,
            startXFraction = 0.58f,
            startYFraction = 0.34f,
            endXFraction = 0.22f,
            endYFraction = 0.46f,
            startRadiusFraction = 0.30f,
            endRadiusFraction = 0.42f,
            durationMillis = 34_000,
            delayMillis = 3_000,
            alpha = 0.13f,
        ),
        FluidGradientBlobSpec(
            colorHex = 0xFFFF7A90,
            startXFraction = 0.84f,
            startYFraction = 0.30f,
            endXFraction = 0.48f,
            endYFraction = 0.90f,
            startRadiusFraction = 0.24f,
            endRadiusFraction = 0.36f,
            durationMillis = 36_000,
            delayMillis = 4_200,
            alpha = 0.10f,
        ),
    ),
)
