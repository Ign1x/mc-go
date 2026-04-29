package com.mcgo.app.ui.components

import com.google.common.truth.Truth.assertThat
import com.mcgo.app.ui.theme.FluidGradientNoiseOverlaySpec
import kotlin.test.Test

class NoiseOverlayTileTest {

    @Test
    fun generateNoiseTile_isDeterministicAndUsesVeryLowAlpha() {
        val spec = FluidGradientNoiseOverlaySpec(
            opacity = 0.04f,
            tileSizePx = 8,
            seed = 42,
            blendModeName = "Overlay",
        )

        val first = generateNoiseTilePixels(spec)
        val second = generateNoiseTilePixels(spec)
        val alphaValues = first.map { pixel -> pixel ushr 24 }

        assertThat(first).isEqualTo(second)
        assertThat(first.toSet().size).isGreaterThan(1)
        assertThat(alphaValues.max()).isAtMost((255 * 0.05f).toInt() + 1)
        assertThat(alphaValues.min()).isAtLeast(0)
    }
}
