package com.mcgo.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mcgo.app.R
import com.mcgo.app.ui.components.FluidGradientBackground
import com.mcgo.app.ui.model.AppearancePreferences
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.TunnelProfile
import com.mcgo.app.ui.theme.LocalMcGoVisualTokens
import com.mcgo.app.ui.theme.McGoTheme

internal sealed interface StartupUiState {
    data object Loading : StartupUiState

    data class Ready(
        val appearancePreferences: AppearancePreferences,
        val persistedServers: List<ServerCardState>,
        val reconciledPersistedServers: List<ServerCardState>,
        val persistedTunnels: List<TunnelProfile>,
        val activeRuntimeSlotsOnLaunch: Set<Int>,
        val persistedServerDirectoryUri: String?,
    ) : StartupUiState
}

@Composable
internal fun MCGoStartupLoadingScreen(appearancePreferences: AppearancePreferences) {
    McGoTheme(appearancePreferences = appearancePreferences) {
        val visuals = LocalMcGoVisualTokens.current
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = visuals.primaryTextColor,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                FluidGradientBackground(
                    spec = visuals.fluidBackgroundSpec,
                    animate = appearancePreferences.dynamicBackground,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.52f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.padding(horizontal = 28.dp),
                        color = visuals.cardContainerColor,
                        contentColor = visuals.primaryTextColor,
                        shape = RoundedCornerShape(28.dp),
                        border = BorderStroke(1.dp, visuals.cardStrokeColor),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.startup_loading_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = stringResource(R.string.startup_loading_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = visuals.secondaryTextColor,
                            )
                        }
                    }
                }
            }
        }
    }
}
