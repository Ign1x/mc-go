package com.mcgo.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mcgo.app.ui.model.SettingsBackActionPlacement
import com.mcgo.app.ui.model.SettingsDetailChromeState
import com.mcgo.app.ui.theme.LocalMcGoVisualTokens
import com.mcgo.app.ui.theme.screenTextColors

@Composable
internal fun SettingsDetailHeader(
    title: String,
    subtitle: String,
    chrome: SettingsDetailChromeState,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = screenTextColors(LocalMcGoVisualTokens.current)
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 56.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.primary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondary,
            )
        }
        if (chrome.backActionPlacement == SettingsBackActionPlacement.TopRight && chrome.usesCompactActionButton) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(40.dp)
                    .clickable(onClick = onNavigateBack),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回上一级",
                    )
                }
            }
        }
    }
}
