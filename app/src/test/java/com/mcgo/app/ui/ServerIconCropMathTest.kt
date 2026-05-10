package com.mcgo.app.ui

import androidx.compose.ui.geometry.Offset
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class ServerIconCropMathTest {
    @Test
    fun resolveServerIconCropWindow_usesViewportScaleWhenConvertingPanToSourcePixels() {
        val window = resolveServerIconCropWindow(
            sourceWidth = 400,
            sourceHeight = 200,
            viewportSizePx = 300,
            scale = 1f,
            offset = Offset(30f, 0f),
        )

        assertThat(window.size).isEqualTo(200)
        assertThat(window.startX).isEqualTo(80)
        assertThat(window.startY).isEqualTo(0)
    }

    @Test
    fun resolveServerIconCropWindow_zoomInShrinksCropAreaAroundCenter() {
        val window = resolveServerIconCropWindow(
            sourceWidth = 400,
            sourceHeight = 200,
            viewportSizePx = 300,
            scale = 2f,
            offset = Offset.Zero,
        )

        assertThat(window.size).isEqualTo(100)
        assertThat(window.startX).isEqualTo(150)
        assertThat(window.startY).isEqualTo(50)
    }

    @Test
    fun clampServerIconCropOffset_limitsPanSoPreviewCannotExposeBlankSpace() {
        val clamped = clampServerIconCropOffset(
            sourceWidth = 400,
            sourceHeight = 200,
            viewportSizePx = 300,
            scale = 1f,
            offset = Offset(200f, 80f),
        )

        assertThat(clamped.x).isEqualTo(150f)
        assertThat(clamped.y).isEqualTo(0f)
    }

    @Test
    fun formatServerIconCropZoomLabel_returnsReadablePercent() {
        assertThat(formatServerIconCropZoomLabel(1f)).isEqualTo("100%")
        assertThat(formatServerIconCropZoomLabel(1.5f)).isEqualTo("150%")
        assertThat(formatServerIconCropZoomLabel(2.25f)).isEqualTo("225%")
    }
}
