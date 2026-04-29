package com.mcgo.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mcgo.app.R
import com.mcgo.app.ui.components.FluidGradientBackground
import com.mcgo.app.ui.model.AppearancePreferences
import com.mcgo.app.ui.model.AppearancePreferencesSaver
import com.mcgo.app.ui.model.McGoPage
import com.mcgo.app.ui.model.McGoPageChrome
import com.mcgo.app.ui.screens.ServersScreen
import com.mcgo.app.ui.screens.SettingsScreen
import com.mcgo.app.ui.screens.StatusScreen
import com.mcgo.app.ui.theme.LocalMcGoVisualTokens
import com.mcgo.app.ui.theme.McGoTheme
import kotlinx.coroutines.launch

private enum class McGoDestination(
    val page: McGoPage,
    val labelRes: Int,
    val icon: ImageVector,
) {
    Status(McGoPage.Status, R.string.nav_status, Icons.Outlined.Speed),
    Servers(McGoPage.Servers, R.string.nav_servers, Icons.Outlined.Dns),
    Settings(McGoPage.Settings, R.string.nav_settings, Icons.Outlined.Settings),
}

@Composable
fun MCGoApp() {
    var appearancePreferences by rememberSaveable(stateSaver = AppearancePreferencesSaver) {
        mutableStateOf(AppearancePreferences())
    }

    McGoTheme(appearancePreferences = appearancePreferences) {
        MCGoAppScaffold(
            appearancePreferences = appearancePreferences,
            onAppearancePreferencesChange = { appearancePreferences = it },
        )
    }
}

@Composable
private fun MCGoAppScaffold(
    appearancePreferences: AppearancePreferences,
    onAppearancePreferencesChange: (AppearancePreferences) -> Unit,
) {
    var destination by rememberSaveable { mutableStateOf(McGoDestination.Status) }
    val chrome = McGoPageChrome.forPage(destination.page)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val featureMessage = stringResource(R.string.snackbar_demo_action)
    val notifyUnavailableFeature: () -> Unit = remember(scope, snackbarHostState, featureMessage) {
        {
            scope.launch {
                snackbarHostState.showSnackbar(featureMessage)
            }
            Unit
        }
    }
    val visuals = LocalMcGoVisualTokens.current
    val fluidBackgroundSpec = visuals.fluidBackgroundSpec
    val bottomBarAlpha = if (appearancePreferences.transparentCards) {
        appearancePreferences.cardContainerAlpha().coerceIn(0.78f, 0.96f)
    } else {
        1f
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FluidGradientBackground(
            spec = fluidBackgroundSpec,
            animate = appearancePreferences.dynamicBackground,
            modifier = Modifier.fillMaxSize(),
        )
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(chrome.titleRes),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(chrome.subtitleRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = bottomBarAlpha),
                    tonalElevation = 0.dp,
                ) {
                    McGoDestination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = { destination = item },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = stringResource(item.labelRes),
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(item.labelRes),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
            },
            floatingActionButton = {
                if (destination == McGoDestination.Servers) {
                    ExtendedFloatingActionButton(
                        onClick = notifyUnavailableFeature,
                        text = { Text(stringResource(R.string.action_create_server)) },
                        icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            },
            floatingActionButtonPosition = FabPosition.End,
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (destination) {
                    McGoDestination.Status -> StatusScreen(modifier = Modifier.fillMaxSize())
                    McGoDestination.Servers -> ServersScreen(
                        modifier = Modifier.fillMaxSize(),
                        showLeadCard = chrome.showLeadCard,
                        onActionClick = notifyUnavailableFeature,
                    )
                    McGoDestination.Settings -> SettingsScreen(
                        modifier = Modifier.fillMaxSize(),
                        appearancePreferences = appearancePreferences,
                        onAppearancePreferencesChange = onAppearancePreferencesChange,
                    )
                }
            }
        }
    }
}
