package com.mcgo.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mcgo.app.R
import com.mcgo.app.ui.model.McGoPage
import com.mcgo.app.ui.theme.LocalMcGoVisualTokens

internal enum class McGoDestination(
    val page: McGoPage,
    val labelRes: Int,
    val icon: ImageVector,
) {
    Status(McGoPage.Status, R.string.nav_status, Icons.Outlined.Speed),
    Servers(McGoPage.Servers, R.string.nav_servers, Icons.Outlined.Dns),
    Tunnels(McGoPage.Tunnels, R.string.nav_tunnels, Icons.Outlined.SwapHoriz),
    Settings(McGoPage.Settings, R.string.nav_settings, Icons.Outlined.Settings),
}

@Composable
internal fun FloatingGlassBottomMenu(
    destination: McGoDestination,
    bottomBarAlpha: Float,
    transparentCards: Boolean,
    onDestinationSelected: (McGoDestination) -> Unit,
) {
    val visuals = LocalMcGoVisualTokens.current
    val selectedContentColor = MaterialTheme.colorScheme.primary
    val unselectedContentColor = visuals.primaryTextColor.copy(alpha = 0.6f)
    val containerColor = if (transparentCards) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f * bottomBarAlpha)
    } else {
        visuals.cardContainerColor
    }
    val backdropBaseColor = if (transparentCards) {
        visuals.cardContainerColor
    } else {
        MaterialTheme.colorScheme.surface
    }
    val menuBackdropGradient = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            backdropBaseColor.copy(alpha = 0.04f * bottomBarAlpha),
            backdropBaseColor.copy(alpha = 0.18f * bottomBarAlpha),
            backdropBaseColor.copy(alpha = 0.32f * bottomBarAlpha),
        ),
        startY = 0f,
        endY = Float.POSITIVE_INFINITY,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(menuBackdropGradient),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            color = containerColor,
            contentColor = unselectedContentColor,
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, visuals.cardStrokeColor.copy(alpha = 0.58f)),
            tonalElevation = 0.dp,
            shadowElevation = 24.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                McGoDestination.entries.forEach { item ->
                    val selected = destination == item
                    val contentColor = if (selected) selectedContentColor else unselectedContentColor
                    val interactionSource = remember(item) { MutableInteractionSource() }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .selectable(
                                selected = selected,
                                onClick = { onDestinationSelected(item) },
                                role = Role.Tab,
                                interactionSource = interactionSource,
                                indication = null,
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .indication(
                                    interactionSource = interactionSource,
                                    indication = ripple(
                                        bounded = true,
                                        radius = 28.dp,
                                    ),
                                )
                                .background(
                                    color = if (selected) selectedContentColor.copy(alpha = 0.14f) else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = contentColor,
                            )
                        }
                        Text(
                            text = stringResource(item.labelRes),
                            color = contentColor,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
