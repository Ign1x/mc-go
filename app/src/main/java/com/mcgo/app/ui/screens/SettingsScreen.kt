package com.mcgo.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcgo.app.ui.components.GlassCard
import com.mcgo.app.ui.model.AccentPreset
import com.mcgo.app.ui.model.AppearancePreferences
import com.mcgo.app.ui.model.FontScalePreference
import com.mcgo.app.ui.model.MotionPreference
import com.mcgo.app.ui.model.SettingsCategoryIcon
import com.mcgo.app.ui.model.SettingsDestination
import com.mcgo.app.ui.model.SettingsNavigationState
import com.mcgo.app.ui.model.SettingsSectionState
import com.mcgo.app.ui.model.ThemeModePreference
import com.mcgo.app.ui.sample.McGoSampleRepository
import com.mcgo.app.ui.theme.Blue500
import com.mcgo.app.ui.theme.Gold500
import com.mcgo.app.ui.theme.Green500
import com.mcgo.app.ui.theme.Ink600
import com.mcgo.app.ui.theme.Ink900
import com.mcgo.app.ui.theme.MistBackground
import com.mcgo.app.ui.theme.SurfaceSoft
import com.mcgo.app.ui.theme.SurfaceSoftAlt
import com.mcgo.app.ui.theme.Violet500

@Composable
fun SettingsScreen(
    appearancePreferences: AppearancePreferences,
    onAppearancePreferencesChange: (AppearancePreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appearanceSection = remember(appearancePreferences.themeMode, appearancePreferences.accentPreset) {
        McGoSampleRepository.settingsSections().first().copy(highlight = appearancePreferences.summaryLabel())
    }
    val appearanceOptions = remember { McGoSampleRepository.appearanceSettings() }
    var destination by rememberSaveable { mutableStateOf(SettingsDestination.Overview) }
    val navigationState = remember(destination) { SettingsNavigationState(destination = destination) }

    BackHandler(enabled = navigationState.canNavigateBack) {
        destination = navigationState.navigateBack().destination
    }

    when (navigationState.destination) {
        SettingsDestination.Overview -> SettingsOverview(
            modifier = modifier,
            section = appearanceSection,
            onOpenAppearance = { destination = navigationState.openAppearance().destination },
        )
        SettingsDestination.Appearance -> AppearanceDetailScreen(
            modifier = modifier,
            section = appearanceSection,
            appearancePreferences = appearancePreferences,
            appearanceOptions = appearanceOptions,
            onNavigateBack = { destination = navigationState.navigateBack().destination },
            onAppearancePreferencesChange = onAppearancePreferencesChange,
        )
    }
}

@Composable
private fun SettingsOverview(
    section: SettingsSectionState,
    onOpenAppearance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item {
            SettingsCard(
                section = section,
                modifier = Modifier.padding(horizontal = 20.dp),
                onSectionClick = onOpenAppearance,
            )
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun AppearanceDetailScreen(
    section: SettingsSectionState,
    appearancePreferences: AppearancePreferences,
    appearanceOptions: com.mcgo.app.ui.model.AppearanceSettingsState,
    onNavigateBack: () -> Unit,
    onAppearancePreferencesChange: (AppearancePreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item {
            AppearanceDetailHeader(
                title = section.title,
                subtitle = appearancePreferences.summaryLabel(),
                onNavigateBack = onNavigateBack,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            AppearancePreviewCard(
                appearancePreferences = appearancePreferences,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            ChoiceChipCard(
                title = "主题模式",
                options = appearanceOptions.themeModes,
                selectedOption = appearancePreferences.themeMode.label,
                onOptionSelected = {
                    onAppearancePreferencesChange(
                        appearancePreferences.copy(themeMode = ThemeModePreference.fromLabel(it)),
                    )
                },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            AccentChoiceCard(
                options = appearanceOptions.accentOptions,
                selectedOption = appearancePreferences.accentPreset.label,
                onOptionSelected = {
                    onAppearancePreferencesChange(
                        appearancePreferences.copy(accentPreset = AccentPreset.fromLabel(it)),
                    )
                },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            ChoiceChipCard(
                title = "字体密度",
                options = appearanceOptions.fontScaleOptions,
                selectedOption = appearancePreferences.fontScale.label,
                onOptionSelected = {
                    val selectedFontScale = FontScalePreference.fromLabel(it)
                    onAppearancePreferencesChange(
                        appearancePreferences.copy(
                            fontScale = selectedFontScale,
                            compactTypography = selectedFontScale == FontScalePreference.Compact,
                        ),
                    )
                },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            ChoiceChipCard(
                title = "动效强度",
                options = appearanceOptions.motionOptions,
                selectedOption = appearancePreferences.motionPreference.label,
                onOptionSelected = {
                    onAppearancePreferencesChange(
                        appearancePreferences.copy(motionPreference = MotionPreference.fromLabel(it)),
                    )
                },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            TransparencySliderCard(
                value = appearancePreferences.cardTransparencyPercent,
                onValueChange = {
                    onAppearancePreferencesChange(
                        appearancePreferences.copy(cardTransparencyPercent = it),
                    )
                },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            AppearanceTogglesCard(
                transparentCardsEnabled = appearancePreferences.transparentCards,
                onTransparentCardsChange = {
                    onAppearancePreferencesChange(appearancePreferences.copy(transparentCards = it))
                },
                dynamicBackgroundEnabled = appearancePreferences.dynamicBackground,
                onDynamicBackgroundChange = {
                    onAppearancePreferencesChange(appearancePreferences.copy(dynamicBackground = it))
                },
                compactTypographyEnabled = appearancePreferences.compactTypography,
                onCompactTypographyChange = { enabled ->
                    onAppearancePreferencesChange(
                        appearancePreferences.copy(
                            compactTypography = enabled,
                            fontScale = if (enabled) FontScalePreference.Compact else FontScalePreference.Standard,
                        ),
                    )
                },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun AppearanceDetailHeader(
    title: String,
    subtitle: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onNavigateBack),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = null,
                )
                Text(
                    text = "返回",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Ink600,
            )
        }
    }
}

@Composable
private fun AppearancePreviewCard(
    appearancePreferences: AppearancePreferences,
    modifier: Modifier = Modifier,
) {
    val accentColor = accentColor(appearancePreferences.accentPreset.label)
    val isDarkPreview = appearancePreferences.themeMode == ThemeModePreference.Dark
    val previewBackground = when (appearancePreferences.themeMode) {
        ThemeModePreference.Dark -> Ink900.copy(alpha = 0.92f)
        ThemeModePreference.FollowSystem -> SurfaceSoftAlt
        ThemeModePreference.Light -> MistBackground
    }
    val previewTextColor = if (isDarkPreview) Color.White else Ink900
    val previewSubtleColor = if (isDarkPreview) Color.White.copy(alpha = 0.7f) else Ink600
    val previewCardColor = if (appearancePreferences.transparentCards) {
        Color.White.copy(alpha = appearancePreferences.cardContainerAlpha())
    } else {
        Color.White
    }

    GlassCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "当前预览", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = appearancePreferences.summaryLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink600,
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = accentColor.copy(alpha = 0.14f),
                contentColor = accentColor,
            ) {
                Text(
                    text = appearancePreferences.motionPreference.label,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = previewBackground,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (appearancePreferences.dynamicBackground) accentColor.copy(alpha = appearancePreferences.backgroundAuraAlpha())
                        else Color.Transparent,
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "MC-GO",
                            style = MaterialTheme.typography.titleSmall,
                            color = previewTextColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${appearancePreferences.fontScale.label} · ${appearancePreferences.cardTransparencyPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = previewSubtleColor,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(accentColor, CircleShape),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PreviewMiniCard(
                        title = "实时性能",
                        subtitle = "浅色背景更清爽",
                        accentColor = accentColor,
                        textColor = previewTextColor,
                        subtleColor = previewSubtleColor,
                        cardColor = previewCardColor,
                        modifier = Modifier.weight(1f),
                    )
                    PreviewMiniCard(
                        title = "界面与外观",
                        subtitle = "小字体 · 透明卡",
                        accentColor = accentColor,
                        textColor = previewTextColor,
                        subtleColor = previewSubtleColor,
                        cardColor = previewCardColor,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewMiniCard(
    title: String,
    subtitle: String,
    accentColor: Color,
    textColor: Color,
    subtleColor: Color,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = cardColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(4.dp)
                    .background(accentColor, RoundedCornerShape(999.dp)),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtleColor,
            )
        }
    }
}

@Composable
private fun ChoiceChipCard(
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selectedOption,
                    onClick = { onOptionSelected(option) },
                    label = { Text(option) },
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
        }
    }
}

@Composable
private fun AccentChoiceCard(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier) {
        Text(text = "主题色彩", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            options.forEach { option ->
                val color = accentColor(option)
                FilterChip(
                    selected = option == selectedOption,
                    onClick = { onOptionSelected(option) },
                    label = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(color, CircleShape),
                            )
                            Text(option)
                        }
                    },
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
        }
    }
}

@Composable
private fun TransparencySliderCard(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "卡片透明度", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "$value%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 50f..96f,
        )
    }
}

@Composable
private fun AppearanceTogglesCard(
    transparentCardsEnabled: Boolean,
    onTransparentCardsChange: (Boolean) -> Unit,
    dynamicBackgroundEnabled: Boolean,
    onDynamicBackgroundChange: (Boolean) -> Unit,
    compactTypographyEnabled: Boolean,
    onCompactTypographyChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier) {
        Text(text = "细节开关", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            AppearanceToggleRow(
                title = "透明卡片",
                subtitle = "控制卡片的通透感",
                checked = transparentCardsEnabled,
                onCheckedChange = onTransparentCardsChange,
            )
            AppearanceToggleRow(
                title = "动态背景",
                subtitle = "控制背景氛围光",
                checked = dynamicBackgroundEnabled,
                onCheckedChange = onDynamicBackgroundChange,
            )
            AppearanceToggleRow(
                title = "紧凑字体",
                subtitle = "整体字体更小一些",
                checked = compactTypographyEnabled,
                onCheckedChange = onCompactTypographyChange,
            )
        }
    }
}

@Composable
private fun AppearanceToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Ink600,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
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
    SettingsCategoryIcon.Appearance -> Icons.Outlined.Tune
    SettingsCategoryIcon.Notifications -> Icons.Outlined.Notifications
    SettingsCategoryIcon.Storage -> Icons.Outlined.Folder
    SettingsCategoryIcon.Diagnostics -> Icons.AutoMirrored.Outlined.Article
    SettingsCategoryIcon.Labs -> Icons.Outlined.Science
}

private fun accentColor(option: String): Color = when (option) {
    AccentPreset.Ocean.label -> Blue500
    AccentPreset.Forest.label -> Green500
    AccentPreset.Amethyst.label -> Violet500
    AccentPreset.Sunset.label -> Gold500
    else -> Blue500
}
