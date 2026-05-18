package com.mcgo.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mcgo.app.ui.model.ServerCardState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface PendingServerIconChange {
    data object Unchanged : PendingServerIconChange
    data object Remove : PendingServerIconChange
    data class Replace(val pngBytes: ByteArray) : PendingServerIconChange
}

@Composable
internal fun ServerIconEditorCard(
    server: ServerCardState,
    pendingServerIconChange: PendingServerIconChange,
    onPickIcon: () -> Unit,
    onRemoveIcon: () -> Unit,
    showRemoveAction: Boolean = true,
    pickButtonLabel: String = "选择图标",
    preferSingleRowActions: Boolean = false,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val containerModifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
        val pickAction: @Composable () -> Unit = {
            OutlinedButton(
                onClick = onPickIcon,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(pickButtonLabel)
            }
        }
        val removeAction: @Composable () -> Unit = {
            OutlinedButton(
                onClick = onRemoveIcon,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("移除图标")
            }
        }
        val actionColumn: @Composable () -> Unit = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                pickAction()
                if (showRemoveAction) {
                    removeAction()
                }
            }
        }
        val actionRow: @Composable () -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onPickIcon,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(pickButtonLabel)
                }
                if (showRemoveAction) {
                    OutlinedButton(
                        onClick = onRemoveIcon,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("移除图标")
                    }
                }
            }
        }
        if (preferSingleRowActions) {
            Row(
                modifier = containerModifier,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ServerAvatar(
                    server = server,
                    pendingServerIconChange = pendingServerIconChange,
                    modifier = Modifier.size(72.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "服务器图标",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "使用手机图片，裁剪成 1:1 后自动转为 64×64 PNG",
                        style = MaterialTheme.typography.bodySmall,
                        color = editPageColors().secondaryText,
                    )
                    actionRow()
                }
            }
        } else if (maxWidth < 360.dp) {
            Column(
                modifier = containerModifier,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ServerAvatar(
                        server = server,
                        pendingServerIconChange = pendingServerIconChange,
                        modifier = Modifier.size(64.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "服务器图标",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "使用手机图片，裁剪成 1:1 后自动转为 64×64 PNG",
                            style = MaterialTheme.typography.bodySmall,
                            color = editPageColors().secondaryText,
                        )
                    }
                }
                actionColumn()
            }
        } else {
            Row(
                modifier = containerModifier,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ServerAvatar(
                    server = server,
                    pendingServerIconChange = pendingServerIconChange,
                    modifier = Modifier.size(72.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "服务器图标",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "使用手机图片，裁剪成 1:1 后自动转为 64×64 PNG",
                        style = MaterialTheme.typography.bodySmall,
                        color = editPageColors().secondaryText,
                    )
                    actionColumn()
                }
            }
        }
    }
}

@Composable
fun ServerAvatar(
    server: ServerCardState,
    pendingServerIconChange: PendingServerIconChange = PendingServerIconChange.Unchanged,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pendingBitmap = remember(pendingServerIconChange) {
        when (pendingServerIconChange) {
            is PendingServerIconChange.Replace -> BitmapFactory.decodeByteArray(
                pendingServerIconChange.pngBytes,
                0,
                pendingServerIconChange.pngBytes.size,
            )
            else -> null
        }
    }
    val persistedBitmap by produceState<Bitmap?>(initialValue = null, server.id, server.serverIconVersion) {
        value = withContext(Dispatchers.IO) {
            loadManagedServerIcon(context.filesDir.toPath(), server.id)
        }
    }
    val resolvedBitmap = when (pendingServerIconChange) {
        PendingServerIconChange.Remove -> null
        is PendingServerIconChange.Replace -> pendingBitmap
        PendingServerIconChange.Unchanged -> persistedBitmap
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        if (resolvedBitmap != null) {
            Image(
                bitmap = resolvedBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = server.name.firstOrNull()?.uppercase() ?: "M",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
internal fun ServerIconCropDialog(
    previewBitmap: Bitmap,
    dynamicBackground: Boolean,
    onDismiss: () -> Unit,
    onApply: (ByteArray) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val cropViewportDp = configuration.screenWidthDp.dp - 40.dp
    val density = LocalDensity.current
    val cropViewportPx = remember(cropViewportDp, density) { with(density) { cropViewportDp.roundToPx() } }
    var cropScale by remember(previewBitmap) { mutableStateOf(1f) }
    var cropOffset by remember(previewBitmap, cropViewportPx) {
        mutableStateOf(
            clampServerIconCropOffset(
                sourceWidth = previewBitmap.width,
                sourceHeight = previewBitmap.height,
                viewportSizePx = cropViewportPx,
                scale = cropScale,
                offset = Offset.Zero,
            ),
        )
    }
    fun updateCropScale(nextScale: Float) {
        val resolvedScale = nextScale.coerceIn(1f, 6f)
        cropScale = resolvedScale
        cropOffset = clampServerIconCropOffset(
            sourceWidth = previewBitmap.width,
            sourceHeight = previewBitmap.height,
            viewportSizePx = cropViewportPx,
            scale = resolvedScale,
            offset = cropOffset,
        )
    }
    val imageBitmap = remember(previewBitmap) { previewBitmap.asImageBitmap() }
    val baseDisplayScale = remember(previewBitmap, cropViewportPx) {
        cropViewportPx / minOf(previewBitmap.width, previewBitmap.height).toFloat().coerceAtLeast(1f)
    }
    val imageDisplayWidthDp = with(density) { (previewBitmap.width * baseDisplayScale).toDp() }
    val imageDisplayHeightDp = with(density) { (previewBitmap.height * baseDisplayScale).toDp() }
    BackHandler(enabled = true, onBack = onDismiss)
    EditFullScreenScaffold(
        title = "裁剪服务器图标",
        subtitle = "拖动和缩放，导出为 1:1 64×64 PNG",
        leadingIcon = Icons.Outlined.Edit,
        dynamicBackground = dynamicBackground,
        layoutMode = EditFullScreenScaffoldLayoutMode.ScrollableChrome,
        onDismiss = onDismiss,
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("取消") }
                Button(
                    onClick = {
                        onApply(
                            cropServerIconToSquarePng(
                                source = previewBitmap,
                                viewportSizePx = cropViewportPx,
                                scale = cropScale,
                                offset = cropOffset,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = cropViewportPx > 0,
                ) { Text("应用图标") }
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(cropViewportDp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.Black.copy(alpha = 0.16f))
                        .border(
                            2.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                            RoundedCornerShape(28.dp),
                        )
                        .pointerInput(previewBitmap, cropViewportPx) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (cropScale * zoom).coerceIn(1f, 6f)
                                val unclampedOffset = cropOffset + pan
                                updateCropScale(nextScale)
                                cropOffset = clampServerIconCropOffset(
                                    sourceWidth = previewBitmap.width,
                                    sourceHeight = previewBitmap.height,
                                    viewportSizePx = cropViewportPx,
                                    scale = cropScale,
                                    offset = unclampedOffset,
                                )
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(
                                width = imageDisplayWidthDp,
                                height = imageDisplayHeightDp,
                            )
                            .graphicsLayer(
                                scaleX = cropScale,
                                scaleY = cropScale,
                                translationX = cropOffset.x,
                                translationY = cropOffset.y,
                            ),
                        contentScale = ContentScale.Fit,
                    )
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = editPageColors().cardContainerColor,
                    contentColor = editPageColors().primaryText,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, editPageColors().cardStrokeColor),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("缩放", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = formatServerIconCropZoomLabel(cropScale),
                                style = MaterialTheme.typography.labelLarge,
                                color = editPageColors().secondaryText,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            IconButton(onClick = { updateCropScale(cropScale - 0.25f) }) {
                                Icon(Icons.Outlined.Remove, contentDescription = "缩小")
                            }
                            Slider(
                                value = cropScale,
                                onValueChange = { updateCropScale(it) },
                                valueRange = 1f..6f,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { updateCropScale(cropScale + 0.25f) }) {
                                Icon(Icons.Outlined.Add, contentDescription = "放大")
                            }
                        }
                        TextButton(onClick = {
                                updateCropScale(1f)
                                cropOffset = Offset.Zero
                            }) {
                            Text("重置位置与缩放")
                        }
                    }
                }
            }
        }
    }
}
