package com.mcgo.app.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
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
                color = visuals.cardContainerColor,
                contentColor = visuals.cardContentColor,
                border = BorderStroke(1.dp, visuals.cardStrokeColor),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
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
