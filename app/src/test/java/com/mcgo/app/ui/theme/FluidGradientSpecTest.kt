package com.mcgo.app.ui.theme

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class FluidGradientSpecTest {

    @Test
    fun lightFluidGradient_usesMutedThreeColorPaletteAndSlowLoop() {
        val spec = fluidGradientSpec(darkTheme = false)

        assertThat(spec.backdropHexes).containsExactly(
            0xFFF8F4EE,
            0xFFEEF5FF,
            0xFFF2EEFF,
        ).inOrder()
        assertThat(spec.blobs.map { it.colorHex }.distinct()).containsExactly(
            0xFFD8E8FF,
            0xFFE2DBFF,
            0xFFF7EEDD,
        ).inOrder()
        assertThat(spec.blobs).hasSize(3)
        spec.blobs.forEach { blob ->
            assertThat(blob.durationMillis).isAtLeast(10_000)
            assertThat(blob.durationMillis).isAtMost(15_000)
            assertThat(blob.alpha).isAtMost(0.55f)
        }
        assertThat(spec.blurRadiusDp).isAtLeast(160f)
        assertThat(spec.overlayAlpha).isEqualTo(0.12f)
    }

    @Test
    fun darkFluidGradient_preservesReadabilityWithCalmPalette() {
        val spec = fluidGradientSpec(darkTheme = true)

        assertThat(spec.backdropHexes).containsExactly(
            0xFF1C1C1E,
            0xFF1E1E1E,
            0xFF252525,
        ).inOrder()
        assertThat(spec.blobs.map { it.colorHex }.distinct()).containsExactly(
            0xFF7887A0,
            0xFF877EA1,
            0xFFB2A796,
        ).inOrder()
        assertThat(spec.blobs.map { it.durationMillis }).containsExactly(12_000, 14_000, 15_000).inOrder()
        assertThat(spec.overlayAlpha).isEqualTo(0.18f)
    }
}
