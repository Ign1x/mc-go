package com.mcgo.app.ui.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.mcgo.app.ui.theme.LocalMcGoVisualTokens

@Composable
fun McGoCardDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    text: @Composable ColumnScope.() -> Unit,
    dismissButton: (@Composable RowScope.() -> Unit)? = null,
    confirmButton: (@Composable RowScope.() -> Unit)? = null,
) {
    val visuals = LocalMcGoVisualTokens.current
    val configuration = LocalConfiguration.current
    val dialogContainerColor = frostedDialogContainerColor(visuals.cardContainerColor)
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        EnableDialogWindowFrostedBlur()
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = modifier
                    .padding(horizontal = 20.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = configuration.screenHeightDp.dp * 0.92f),
                shape = RoundedCornerShape(28.dp),
                color = dialogContainerColor,
                contentColor = visuals.cardContentColor,
                border = BorderStroke(1.dp, visuals.cardStrokeColor),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Box(modifier = Modifier.frostedDialogBackdrop()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        title?.let { titleContent ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                titleContent()
                            }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            content = text,
                        )
                        if (dismissButton != null || confirmButton != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                dismissButton?.invoke(this)
                                if (dismissButton != null && confirmButton != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                confirmButton?.invoke(this)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun frostedDialogContainerColor(base: Color): Color =
    base.copy(alpha = base.alpha.coerceAtLeast(0.90f))

@Composable
private fun EnableDialogWindowFrostedBlur() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val previousWindowFlags = window.attributes.flags
            val previousBlurBehindRadius = window.attributes.blurBehindRadius
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = WindowManager.LayoutParams().apply {
                copyFrom(window.attributes)
                blurBehindRadius = 24
            }
            onDispose {
                window.attributes = WindowManager.LayoutParams().apply {
                    copyFrom(window.attributes)
                    flags = previousWindowFlags
                    blurBehindRadius = previousBlurBehindRadius
                }
            }
        } else {
            onDispose { }
        }
    }
}

private fun Modifier.frostedDialogBackdrop(): Modifier = drawWithContent {
    val cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx())
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.22f),
                Color.White.copy(alpha = 0.10f),
                Color.Transparent,
            ),
        ),
        cornerRadius = cornerRadius,
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.08f),
        cornerRadius = cornerRadius,
    )
    drawContent()
}
