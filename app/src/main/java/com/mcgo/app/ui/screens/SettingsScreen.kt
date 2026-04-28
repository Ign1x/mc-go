package com.mcgo.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mcgo.app.R
import com.mcgo.app.ui.components.GlassCard
import com.mcgo.app.ui.model.SettingsCategoryIcon
import com.mcgo.app.ui.model.SettingsSectionState
import com.mcgo.app.ui.sample.McGoSampleRepository
import com.mcgo.app.ui.theme.Ink600
import com.mcgo.app.ui.theme.SurfaceSoft

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onSectionClick: () -> Unit,
) {
    val sections = McGoSampleRepository.settingsSections()

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item {
            GlassCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(text = stringResource(R.string.settings_center_title), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_center_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink600,
                )
            }
        }
        items(items = sections, key = { it.title }) { section ->
            SettingsCard(
                section = section,
                modifier = Modifier.padding(horizontal = 20.dp),
                onSectionClick = onSectionClick,
            )
        }
        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

@Composable
private fun SettingsCard(
    section: SettingsSectionState,
    modifier: Modifier = Modifier,
    onSectionClick: () -> Unit,
) {
    GlassCard(modifier = modifier.clickable(onClick = onSectionClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = SurfaceSoft,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Icon(
                        imageVector = settingsIcon(section.icon),
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column {
                    Text(text = section.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = section.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink600,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            text = section.highlight,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = Ink600,
            )
        }
    }
}

private fun settingsIcon(icon: SettingsCategoryIcon) = when (icon) {
    SettingsCategoryIcon.Server -> Icons.Outlined.Dns
    SettingsCategoryIcon.GameRule -> Icons.Outlined.SportsEsports
    SettingsCategoryIcon.Edition -> Icons.Outlined.Layers
    SettingsCategoryIcon.App -> Icons.Outlined.Apps
    SettingsCategoryIcon.Safety -> Icons.Outlined.Shield
}
