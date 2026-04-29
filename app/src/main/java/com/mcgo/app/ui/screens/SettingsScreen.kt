package com.mcgo.app.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.mcgo.app.ui.model.SettingsCategoryIcon
import com.mcgo.app.ui.model.SettingsSectionState
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
    modifier: Modifier = Modifier,
    showLeadCard: Boolean = false,
    onSectionClick: () -> Unit,
) {
    val sections = remember { McGoSampleRepository.settingsSections() }
    val appearanceSection = sections.first()
    val otherSections = sections.drop(1)
    val appearance = remember { McGoSampleRepository.appearanceSettings() }
    val togglesByTitle = remember(appearance) { appearance.toggles.associateBy { it.title } }

    var selectedThemeMode by rememberSaveable { mutableStateOf(appearance.selectedThemeMode) }
    var selectedAccent by rememberSaveable { mutableStateOf(appearance.selectedAccent) }
    var selectedFontScale by rememberSaveable { mutableStateOf(appearance.selectedFontScale) }
    var selectedMotionMode by rememberSaveable { mutableStateOf(appearance.selectedMotionMode) }
    var cardTransparencyPercent by rememberSaveable { mutableIntStateOf(appearance.cardTransparencyPercent) }
    var transparentCardsEnabled by rememberSaveable { mutableStateOf(togglesByTitle["透明卡片"]?.enabled == true) }
    var dynamicBackgroundEnabled by rememberSaveable { mutableStateOf(togglesByTitle["动态背景"]?.enabled == true) }
    var compactTypographyEnabled by rememberSaveable { mutableStateOf(togglesByTitle["紧凑字体"]?.enabled == true) }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(modifier = Modifier.height(6.dp)) }
        item {
            SettingsSectionHeader(
                title = appearanceSection.title,
                subtitle = appearanceSection.subtitle,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            AppearancePreviewCard(
                selectedThemeMode = selectedThemeMode,
                selectedAccent = selectedAccent,
                selectedFontScale = selectedFontScale,
                selectedMotionMode = selectedMotionMode,
                cardTransparencyPercent = cardTransparencyPercent,
                transparentCardsEnabled = transparentCardsEnabled,
                dynamicBackgroundEnabled = dynamicBackgroundEnabled,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            ChoiceChipCard(
                title = "主题模式",
                subtitle = "先把主题做成清爽稳定，默认保持浅色观感。",
                options = appearance.themeModes,
                selectedOption = selectedThemeMode,
                onOptionSelected = { selectedThemeMode = it },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            AccentChoiceCard(
                options = appearance.accentOptions,
                selectedOption = selectedAccent,
                onOptionSelected = { selectedAccent = it },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            ChoiceChipCard(
                title = "字体密度",
                subtitle = "你偏好的小一点字体也先做进来，信息密度更高。",
                options = appearance.fontScaleOptions,
                selectedOption = selectedFontScale,
                onOptionSelected = {
                    selectedFontScale = it
                    compactTypographyEnabled = it == "紧凑"
                },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            ChoiceChipCard(
                title = "动效强度",
                subtitle = "控制切页、背景氛围和微交互的存在感。",
                options = appearance.motionOptions,
                selectedOption = selectedMotionMode,
                onOptionSelected = { selectedMotionMode = it },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            TransparencySliderCard(
                value = cardTransparencyPercent,
                onValueChange = { cardTransparencyPercent = it },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            AppearanceTogglesCard(
                transparentCardsEnabled = transparentCardsEnabled,
                onTransparentCardsChange = { transparentCardsEnabled = it },
                dynamicBackgroundEnabled = dynamicBackgroundEnabled,
                onDynamicBackgroundChange = { dynamicBackgroundEnabled = it },
                compactTypographyEnabled = compactTypographyEnabled,
                onCompactTypographyChange = {
                    compactTypographyEnabled = it
                    selectedFontScale = if (it) "紧凑" else "标准"
                },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            SettingsSectionHeader(
                title = "更多偏好",
                subtitle = if (showLeadCard) "更多工具项后面再接真实逻辑。" else "通知、存储、诊断和 Labs 继续保留入口。",
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        items(items = otherSections, key = { it.title }) { section ->
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
private fun SettingsSectionHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Ink600,
        )
    }
}

@Composable
private fun AppearancePreviewCard(
    selectedThemeMode: String,
    selectedAccent: String,
    selectedFontScale: String,
    selectedMotionMode: String,
    cardTransparencyPercent: Int,
    transparentCardsEnabled: Boolean,
    dynamicBackgroundEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val accentColor = accentColor(selectedAccent)
    val isDarkPreview = selectedThemeMode == "深色"
    val previewBackground = when (selectedThemeMode) {
        "深色" -> Ink900.copy(alpha = 0.92f)
        "跟随系统" -> SurfaceSoftAlt
        else -> MistBackground
    }
    val previewTextColor = if (isDarkPreview) Color.White else Ink900
    val previewSubtleColor = if (isDarkPreview) Color.White.copy(alpha = 0.7f) else Ink600
    val previewCardColor = if (transparentCardsEnabled) {
        Color.White.copy(alpha = (cardTransparencyPercent.coerceIn(50, 96) / 100f))
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
                    text = "$selectedThemeMode · $selectedAccent · $selectedFontScale · 透明度 $cardTransparencyPercent%",
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
                    text = selectedMotionMode,
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
                    .background(if (dynamicBackgroundEnabled) accentColor.copy(alpha = 0.08f) else Color.Transparent)
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
                            text = "Status · Servers · Settings",
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
                        subtitle = "固定卡片高度",
                        accentColor = accentColor,
                        textColor = previewTextColor,
                        subtleColor = previewSubtleColor,
                        cardColor = previewCardColor,
                        modifier = Modifier.weight(1f),
                    )
                    PreviewMiniCard(
                        title = "界面与外观",
                        subtitle = "小字体 + 透明卡",
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
    subtitle: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Ink600,
        )
        Spacer(modifier = Modifier.height(14.dp))
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
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "做得更有颜色一点，但仍然保持轻量、干净、好读。",
            style = MaterialTheme.typography.bodySmall,
            color = Ink600,
        )
        Spacer(modifier = Modifier.height(14.dp))
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "卡片透明度", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "把四层卡片的通透感固定下来，避免界面太闷。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink600,
                )
            }
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
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "先把你刚提到的观感偏好落成真正可调的界面。",
            style = MaterialTheme.typography.bodySmall,
            color = Ink600,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            AppearanceToggleRow(
                title = "透明卡片",
                subtitle = "保留浅色玻璃质感，弱化纯白大底。",
                checked = transparentCardsEnabled,
                onCheckedChange = onTransparentCardsChange,
            )
            AppearanceToggleRow(
                title = "动态背景",
                subtitle = "让背景有轻微流动氛围，但不过度抢眼。",
                checked = dynamicBackgroundEnabled,
                onCheckedChange = onDynamicBackgroundChange,
            )
            AppearanceToggleRow(
                title = "紧凑字体",
                subtitle = "整体字体更小一些，页面信息密度更高。",
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
    "科技蓝" -> Blue500
    "森林绿" -> Green500
    "紫晶" -> Violet500
    "暖阳橙" -> Gold500
    else -> Blue500
}
