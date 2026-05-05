package com.mcgo.app.ui.theme

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class FluidGradientSpecTest {

    @Test
    fun lightFluidGradient_usesLaterColorfulLiquidRecipe() {
        val spec = fluidGradientSpec(darkTheme = false)

        assertThat(spec.backdropHexes).containsExactly(
            0xFFF8FAFF,
            0xFFFFF7FB,
            0xFFF3FBFF,
            0xFFFFFCF1,
            0xFFF6F4FF,
        ).inOrder()
        assertThat(spec.blobs.map { it.colorHex }).containsExactly(
            0xFFFF7AB8,
            0xFF60D7FF,
            0xFF75F0BE,
            0xFFFFD166,
            0xFF9B8CFF,
        ).inOrder()
        assertThat(spec.blobs.map { it.durationMillis }).containsExactly(
            28_000,
            24_000,
            31_000,
            26_000,
            33_000,
        ).inOrder()
        assertThat(spec.blobs.map { it.alpha }).containsExactly(
            0.18f,
            0.20f,
            0.16f,
            0.14f,
            0.13f,
        ).inOrder()
        assertThat(spec.overlayAlpha).isEqualTo(0.06f)
        assertThat(spec.blurRadiusDp).isEqualTo(280f)
        assertThat(spec.noiseOverlay.opacity).isEqualTo(0.010f)
        assertThat(spec.noiseOverlay.blendModeName).isEqualTo("Overlay")
        assertThat(spec.noiseOverlay.tileSizePx).isEqualTo(128)
    }

    @Test
    fun darkFluidGradient_usesLaterColorfulReadableRecipe() {
        val spec = fluidGradientSpec(darkTheme = true)

        assertThat(spec.backdropHexes).containsExactly(
            0xFF0B0D12,
            0xFF111827,
            0xFF17111F,
            0xFF0E1B1E,
        ).inOrder()
        assertThat(spec.blobs.map { it.colorHex }).containsExactly(
            0xFF2E7CFF,
            0xFF13D8C8,
            0xFF8A5CFF,
            0xFFFF7A90,
        ).inOrder()
        assertThat(spec.blobs.map { it.durationMillis }).containsExactly(
            30_000,
            27_000,
            34_000,
            36_000,
        ).inOrder()
        assertThat(spec.blobs.map { it.alpha }).containsExactly(
            0.16f,
            0.14f,
            0.13f,
            0.10f,
        ).inOrder()
        assertThat(spec.overlayAlpha).isEqualTo(0.16f)
        assertThat(spec.blurRadiusDp).isEqualTo(300f)
        assertThat(spec.noiseOverlay.opacity).isEqualTo(0.010f)
        assertThat(spec.noiseOverlay.blendModeName).isEqualTo("Overlay")
        assertThat(spec.noiseOverlay.tileSizePx).isEqualTo(128)
    }
}
