package com.mcgo.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.ui.geometry.Offset
import com.mcgo.app.server.managedPaperServerIconFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.math.roundToInt

internal fun decodeServerIconPreviewBitmap(context: Context, uri: Uri): Bitmap {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                calculateImageDecoderTargetSize(
                    sourceWidth = info.size.width,
                    sourceHeight = info.size.height,
                    maxSize = 2048,
                )?.let { target ->
                    decoder.setTargetSize(target.width, target.height)
                }
                decoder.isMutableRequired = false
            }
        }.getOrNull()?.let { return it }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    } ?: error("无法读取所选图片")
    val sampled = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 2048)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, sampled)
    }
        ?: error("无法读取所选图片")
}

data class ImageDecoderTargetSize(
    val width: Int,
    val height: Int,
)

fun calculateImageDecoderTargetSize(sourceWidth: Int, sourceHeight: Int, maxSize: Int): ImageDecoderTargetSize? {
    if (sourceWidth <= 0 || sourceHeight <= 0 || maxSize <= 0) return null
    val longestSide = maxOf(sourceWidth, sourceHeight)
    if (longestSide <= maxSize) return null
    val scale = maxSize.toDouble() / longestSide.toDouble()
    val targetWidth = (sourceWidth * scale).toInt().coerceIn(1, maxSize)
    val targetHeight = (sourceHeight * scale).toInt().coerceIn(1, maxSize)
    return ImageDecoderTargetSize(width = targetWidth, height = targetHeight)
}

private fun calculateInSampleSize(width: Int, height: Int, maxSize: Int): Int {
    var sample = 1
    var currentWidth = width
    var currentHeight = height
    while (currentWidth > maxSize || currentHeight > maxSize) {
        sample *= 2
        currentWidth /= 2
        currentHeight /= 2
    }
    return sample.coerceAtLeast(1)
}

data class ServerIconCropWindow(
    val startX: Int,
    val startY: Int,
    val size: Int,
)

internal fun resolveServerIconCropWindow(
    sourceWidth: Int,
    sourceHeight: Int,
    viewportSizePx: Int,
    scale: Float,
    offset: Offset,
): ServerIconCropWindow {
    val minSide = minOf(sourceWidth, sourceHeight).toFloat().coerceAtLeast(1f)
    val baseDisplayScale = viewportSizePx / minSide
    val sourcePixelsPerViewportPx = 1f / (baseDisplayScale * scale)
    val cropSize = (viewportSizePx * sourcePixelsPerViewportPx)
        .roundToInt()
        .coerceIn(1, minOf(sourceWidth, sourceHeight))
    val centeredLeft = (sourceWidth - cropSize) / 2f
    val centeredTop = (sourceHeight - cropSize) / 2f
    val translatedX = centeredLeft - (offset.x * sourcePixelsPerViewportPx)
    val translatedY = centeredTop - (offset.y * sourcePixelsPerViewportPx)
    return ServerIconCropWindow(
        startX = translatedX.roundToInt().coerceIn(0, sourceWidth - cropSize),
        startY = translatedY.roundToInt().coerceIn(0, sourceHeight - cropSize),
        size = cropSize,
    )
}

internal fun clampServerIconCropOffset(
    sourceWidth: Int,
    sourceHeight: Int,
    viewportSizePx: Int,
    scale: Float,
    offset: Offset,
): Offset {
    val minSide = minOf(sourceWidth, sourceHeight).toFloat().coerceAtLeast(1f)
    val baseDisplayScale = viewportSizePx / minSide
    val displayWidth = sourceWidth * baseDisplayScale * scale
    val displayHeight = sourceHeight * baseDisplayScale * scale
    val maxOffsetX = ((displayWidth - viewportSizePx) / 2f).coerceAtLeast(0f)
    val maxOffsetY = ((displayHeight - viewportSizePx) / 2f).coerceAtLeast(0f)
    return Offset(
        x = offset.x.coerceIn(-maxOffsetX, maxOffsetX),
        y = offset.y.coerceIn(-maxOffsetY, maxOffsetY),
    )
}

internal fun formatServerIconCropZoomLabel(scale: Float): String =
    String.format(Locale.US, "%.0f%%", scale.coerceIn(1f, 6f) * 100f)

internal fun cropServerIconToSquarePng(
    source: Bitmap,
    viewportSizePx: Int,
    scale: Float,
    offset: Offset,
): ByteArray {
    val cropWindow = resolveServerIconCropWindow(
        sourceWidth = source.width,
        sourceHeight = source.height,
        viewportSizePx = viewportSizePx,
        scale = scale,
        offset = offset,
    )
    val cropped = Bitmap.createBitmap(source, cropWindow.startX, cropWindow.startY, cropWindow.size, cropWindow.size)
    val resized = Bitmap.createScaledBitmap(cropped, 64, 64, true)
    return java.io.ByteArrayOutputStream().use { output ->
        resized.compress(Bitmap.CompressFormat.PNG, 100, output)
        output.toByteArray()
    }
}

internal fun loadManagedServerIcon(filesDir: Path, serverId: String): Bitmap? = runCatching {
    val iconFile = managedPaperServerIconFile(filesDir, serverId)
    if (!Files.isRegularFile(iconFile)) return@runCatching null
    BitmapFactory.decodeFile(iconFile.toString())
}.getOrNull()
