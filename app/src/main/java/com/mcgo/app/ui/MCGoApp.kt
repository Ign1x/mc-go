package com.mcgo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mcgo.app.R
import com.mcgo.app.ui.screens.ServersScreen
import com.mcgo.app.ui.screens.SettingsScreen
import com.mcgo.app.ui.screens.StatusScreen
import com.mcgo.app.ui.theme.Blue500
import com.mcgo.app.ui.theme.CloudBackground
import com.mcgo.app.ui.theme.Green500
import com.mcgo.app.ui.theme.MistBackground
import com.mcgo.app.ui.theme.SurfaceSoftAlt
import kotlinx.coroutines.launch

private enum class McGoDestination(
    val labelRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
) {
    Status(R.string.nav_status, R.string.nav_status_subtitle, Icons.Outlined.Speed),
    Servers(R.string.nav_servers, R.string.nav_servers_subtitle, Icons.Outlined.Dns),
    Settings(R.string.nav_settings, R.string.nav_settings_subtitle, Icons.Outlined.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCGoApp() {
    var destination by rememberSaveable { mutableStateOf(McGoDestination.Status) }
    val appBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val demoMessage = stringResource(R.string.snackbar_demo_action)
    val notifyPendingFeature: () -> Unit = remember(scope, snackbarHostState, demoMessage) {
        {
            scope.launch {
                snackbarHostState.showSnackbar(demoMessage)
            }
            Unit
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                colors = appBarColors,
                title = {
                    Column {
                        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = stringResource(destination.subtitleRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White.copy(alpha = 0.86f),
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
                    onClick = notifyPendingFeature,
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
                .background(
                    Brush.verticalGradient(
                        colors = listOf(MistBackground, CloudBackground, SurfaceSoftAlt),
                    ),
                )
                .padding(innerPadding),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Blue500.copy(alpha = 0.12f),
                                Green500.copy(alpha = 0.08f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            when (destination) {
                McGoDestination.Status -> StatusScreen(modifier = Modifier.fillMaxSize())
                McGoDestination.Servers -> ServersScreen(
                    modifier = Modifier.fillMaxSize(),
                    onActionClick = notifyPendingFeature,
                )
                McGoDestination.Settings -> SettingsScreen(
                    modifier = Modifier.fillMaxSize(),
                    onSectionClick = notifyPendingFeature,
                )
            }
        }
    }
}
