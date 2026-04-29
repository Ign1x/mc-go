package com.mcgo.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcgo.app.ui.components.GlassCard
import com.mcgo.app.ui.model.AccentPreset
import com.mcgo.app.ui.model.AppearancePreferences
import com.mcgo.app.ui.model.AppearanceSettingsState
import com.mcgo.app.ui.model.FontScalePreference
import com.mcgo.app.ui.model.JavaManagementState
import com.mcgo.app.ui.model.JavaPermissionItem
import com.mcgo.app.ui.model.JavaRuntimeOption
import com.mcgo.app.ui.model.SettingsBackActionPlacement
import com.mcgo.app.ui.model.SettingsCategoryIcon
import com.mcgo.app.ui.model.SettingsDestination
import com.mcgo.app.ui.model.SettingsDetailChrome
import com.mcgo.app.ui.model.SettingsDetailChromeState
import com.mcgo.app.ui.model.SettingsNavigationState
import com.mcgo.app.ui.model.SettingsSectionState
import com.mcgo.app.ui.model.ThemeModePreference
import com.mcgo.app.ui.model.defaultJavaManagementState
import com.mcgo.app.ui.sample.McGoSampleRepository
import com.mcgo.app.ui.theme.LocalMcGoVisualTokens
import com.mcgo.app.ui.theme.resolveAccentColors
import com.mcgo.app.ui.theme.screenTextColors

@Composable
fun SettingsScreen(
    appearancePreferences: AppearancePreferences,
    onAppearancePreferencesChange: (AppearancePreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsSections = remember(appearancePreferences.themeMode, appearancePreferences.accentPreset, appearancePreferences.fontScale, appearancePreferences.cardTransparencyPercent, appearancePreferences.transparentCards, appearancePreferences.dynamicBackground) {
        McGoSampleRepository.settingsSections().map { section ->
            if (section.icon == SettingsCategoryIcon.Appearance) {
                section.copy(highlight = appearancePreferences.summaryLabel())
            } else {
                section
            }
        }
    }
    val appearanceSection = settingsSections.first { it.icon == SettingsCategoryIcon.Appearance }
    val javaManagementSection = settingsSections.first { it.icon == SettingsCategoryIcon.JavaRuntime }
    val javaManagementState = remember { defaultJavaManagementState() }
    val appearanceOptions = remember { McGoSampleRepository.appearanceSettings() }
    var destination by rememberSaveable { mutableStateOf(SettingsDestination.Overview) }
    val navigationState = remember(destination) { SettingsNavigationState(destination = destination) }

    BackHandler(enabled = navigationState.canNavigateBack) {
        destination = navigationState.navigateBack().destination
    }

    when (navigationState.destination) {
        SettingsDestination.Overview -> SettingsOverview(
            modifier = modifier,
            sections = settingsSections,
            onOpenAppearance = { destination = navigationState.openAppearance().destination },
            onOpenJavaManagement = { destination = navigationState.openJavaManagement().destination },
        )
        SettingsDestination.Appearance -> AppearanceDetailScreen(
            modifier = modifier,
            section = appearanceSection,
            appearancePreferences = appearancePreferences,
            appearanceOptions = appearanceOptions,
            onNavigateBack = { destination = navigationState.navigateBack().destination },
            onAppearancePreferencesChange = onAppearancePreferencesChange,
        )
        SettingsDestination.JavaManagement -> JavaManagementDetailScreen(
            modifier = modifier,
            section = javaManagementSection,
            state = javaManagementState,
            onNavigateBack = { destination = navigationState.navigateBack().destination },
        )
    }
}

@Composable
private fun SettingsOverview(
    sections: List<SettingsSectionState>,
    onOpenAppearance: () -> Unit,
    onOpenJavaManagement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        sections.forEach { section ->
            item {
                SettingsCard(
                    section = section,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    onSectionClick = when (section.icon) {
                        SettingsCategoryIcon.Appearance -> onOpenAppearance
                        SettingsCategoryIcon.JavaRuntime -> onOpenJavaManagement
                        else -> onOpenAppearance
                    },
                )
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun AppearanceDetailScreen(
    section: SettingsSectionState,
    appearancePreferences: AppearancePreferences,
    appearanceOptions: AppearanceSettingsState,
    onNavigateBack: () -> Unit,
    onAppearancePreferencesChange: (AppearancePreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detailChrome = SettingsDetailChrome.forDestination(SettingsDestination.Appearance)

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item {
            AppearanceDetailHeader(
                title = section.title,
                subtitle = appearancePreferences.summaryLabel(),
                chrome = detailChrome,
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
                preferDarkPreview = appearancePreferences.themeMode.resolvesToDark(isSystemInDarkTheme()),
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
                    onAppearancePreferencesChange(
                        appearancePreferences.copy(fontScale = FontScalePreference.fromLabel(it)),
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
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            TransparencySliderCard(
                value = appearancePreferences.cardTransparencyPercent,
                enabled = appearancePreferences.transparentCards,
                onValueChange = {
                    onAppearancePreferencesChange(
                        appearancePreferences.copy(cardTransparencyPercent = it),
                    )
                },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun JavaManagementDetailScreen(
    section: SettingsSectionState,
    state: JavaManagementState,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detailChrome = SettingsDetailChrome.forDestination(SettingsDestination.JavaManagement)

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item {
            AppearanceDetailHeader(
                title = section.title,
                subtitle = state.summaryLabel,
                chrome = detailChrome,
                onNavigateBack = onNavigateBack,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            JavaRuntimeOptionsCard(
                options = state.runtimeOptions,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            JavaPermissionCard(
                permissions = state.permissionItems,
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

@Composable
private fun AppearancePreviewCard(
    appearancePreferences: AppearancePreferences,
    modifier: Modifier = Modifier,
) {
    val visuals = LocalMcGoVisualTokens.current
    val accentColor = MaterialTheme.colorScheme.primary
    val previewBackgroundSpec = visuals.fluidBackgroundSpec
    val previewBackgroundBrush = remember(previewBackgroundSpec) {
        Brush.linearGradient(colors = previewBackgroundSpec.backdropColors())
    }
    val previewOverlayColor = previewBackgroundSpec.overlayColor()
    val previewTextColor = visuals.primaryTextColor
    val previewSubtleColor = visuals.secondaryTextColor
    val previewCardColor = if (appearancePreferences.transparentCards) {
        visuals.cardContainerColor
    } else {
        MaterialTheme.colorScheme.surface
    }

    GlassCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "当前预览",
                    style = MaterialTheme.typography.titleMedium,
                    color = previewTextColor,
                )
                Text(
                    text = appearancePreferences.summaryLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = previewSubtleColor,
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = accentColor.copy(alpha = 0.16f),
                contentColor = accentColor,
            ) {
                Text(
                    text = appearancePreferences.fontScale.label,
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
            color = Color.Transparent,
            contentColor = previewTextColor,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(previewBackgroundBrush)
                    .background(previewOverlayColor)
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
                        subtitle = "深色下保持清晰可读",
                        accentColor = accentColor,
                        textColor = previewTextColor,
                        subtleColor = previewSubtleColor,
                        cardColor = previewCardColor,
                        modifier = Modifier.weight(1f),
                    )
                    PreviewMiniCard(
                        title = "界面与外观",
                        subtitle = "主题色更柔和",
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
private fun JavaRuntimeOptionsCard(
    options: List<JavaRuntimeOption>,
    modifier: Modifier = Modifier,
) {
    val colors = screenTextColors(LocalMcGoVisualTokens.current)
    GlassCard(modifier = modifier) {
        Text(
            text = "Runtime 策略",
            style = MaterialTheme.typography.titleMedium,
            color = colors.primary,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            options.forEach { option ->
                JavaRuntimeOptionRow(option = option)
            }
        }
    }
}

@Composable
private fun JavaRuntimeOptionRow(option: JavaRuntimeOption) {
    val colors = screenTextColors(LocalMcGoVisualTokens.current)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Text(
                text = option.statusLabel,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = option.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = option.description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondary,
            )
        }
    }
}

@Composable
private fun JavaPermissionCard(
    permissions: List<JavaPermissionItem>,
    modifier: Modifier = Modifier,
) {
    val colors = screenTextColors(LocalMcGoVisualTokens.current)
    GlassCard(modifier = modifier) {
        Text(
            text = "运行权限",
            style = MaterialTheme.typography.titleMedium,
            color = colors.primary,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            permissions.forEach { item ->
                JavaPermissionRow(item = item)
            }
        }
    }
}

@Composable
private fun JavaPermissionRow(item: JavaPermissionItem) {
    val colors = screenTextColors(LocalMcGoVisualTokens.current)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondary,
            )
            item.androidPermission?.let { permission ->
                Text(
                    text = permission,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.disabled,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (item.required) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (item.required) MaterialTheme.colorScheme.primary else colors.secondary,
        ) {
            Text(
                text = if (item.required) "必要" else "引导",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
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
    val colors = screenTextColors(LocalMcGoVisualTokens.current)
    GlassCard(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.primary)
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
                    colors = themedSettingsChipColors(),
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
    preferDarkPreview: Boolean,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = screenTextColors(LocalMcGoVisualTokens.current)
    GlassCard(modifier = modifier) {
        Text(text = "主题色彩", style = MaterialTheme.typography.titleMedium, color = colors.primary)
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            options.forEach { option ->
                val color = accentColorForOption(option = option, preferDarkPreview = preferDarkPreview)
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
                    colors = themedSettingsChipColors(),
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
        }
    }
}

@Composable
private fun TransparencySliderCard(
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = screenTextColors(LocalMcGoVisualTokens.current)
    GlassCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "卡片透明度", style = MaterialTheme.typography.titleMedium, color = colors.primary)
            Text(
                text = "$value%",
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.primary else colors.secondary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..100f,
            enabled = enabled,
        )
    }
}

@Composable
private fun AppearanceTogglesCard(
    transparentCardsEnabled: Boolean,
    onTransparentCardsChange: (Boolean) -> Unit,
    dynamicBackgroundEnabled: Boolean,
    onDynamicBackgroundChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = screenTextColors(LocalMcGoVisualTokens.current)
    GlassCard(modifier = modifier) {
        Text(text = "细节开关", style = MaterialTheme.typography.titleMedium, color = colors.primary)
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
                subtitle = "柔和流体渐变，慢速呼吸感",
                checked = dynamicBackgroundEnabled,
                onCheckedChange = onDynamicBackgroundChange,
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
    val colors = screenTextColors(LocalMcGoVisualTokens.current)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = colors.primary)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondary,
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
    val colors = screenTextColors(LocalMcGoVisualTokens.current)
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
                    color = MaterialTheme.colorScheme.surfaceVariant,
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
                    Text(text = section.title, style = MaterialTheme.typography.titleMedium, color = colors.primary)
                    Text(
                        text = section.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun settingsIcon(icon: SettingsCategoryIcon) = when (icon) {
    SettingsCategoryIcon.Appearance -> Icons.Outlined.Tune
    SettingsCategoryIcon.JavaRuntime -> Icons.Outlined.Science
    SettingsCategoryIcon.Notifications -> Icons.Outlined.Notifications
    SettingsCategoryIcon.Storage -> Icons.Outlined.Folder
    SettingsCategoryIcon.Diagnostics -> Icons.AutoMirrored.Outlined.Article
    SettingsCategoryIcon.Labs -> Icons.Outlined.Science
}

@Composable
private fun themedSettingsChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
    selectedLabelColor = MaterialTheme.colorScheme.primary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
)

@Composable
private fun accentColorForOption(option: String, preferDarkPreview: Boolean): Color {
    val context = LocalContext.current
    val preset = AccentPreset.fromLabel(option)
    return resolveAccentColors(
        context = context,
        preset = preset,
        darkTheme = preferDarkPreview,
    ).primary
}
