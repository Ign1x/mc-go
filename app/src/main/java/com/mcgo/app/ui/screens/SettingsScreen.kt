package com.mcgo.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mcgo.app.ui.components.GlassCard
import com.mcgo.app.ui.model.AccentPreset
import com.mcgo.app.ui.model.AppearancePreferences
import com.mcgo.app.ui.model.AppearanceSettingsState
import com.mcgo.app.ui.model.JavaManagementState
import com.mcgo.app.ui.model.JavaRuntimeOption
import com.mcgo.app.ui.model.RuntimePermissionItem
import com.mcgo.app.ui.model.RuntimePermissionState
import com.mcgo.app.ui.model.RuntimePermissionStatus
import com.mcgo.app.ui.model.SettingsBackActionPlacement
import com.mcgo.app.ui.model.SettingsCategoryIcon
import com.mcgo.app.ui.model.SettingsDestination
import com.mcgo.app.ui.model.SettingsDetailChrome
import com.mcgo.app.ui.model.SettingsDetailChromeState
import com.mcgo.app.ui.model.SettingsNavigationState
import com.mcgo.app.ui.model.SettingsSectionState
import com.mcgo.app.ui.model.ThemeModePreference
import com.mcgo.app.ui.model.defaultJavaManagementState
import com.mcgo.app.ui.model.defaultRuntimePermissionState
import com.mcgo.app.ui.sample.McGoSampleRepository
import com.mcgo.app.ui.theme.LocalMcGoVisualTokens
import com.mcgo.app.ui.theme.resolveAccentColors
import com.mcgo.app.ui.theme.screenTextColors


private fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager.isIgnoringBatteryOptimizations(packageName)
    } else {
        true
    }
}

@Composable
fun SettingsScreen(
    appearancePreferences: AppearancePreferences,
    onAppearancePreferencesChange: (AppearancePreferences) -> Unit,
    javaManagementState: JavaManagementState = defaultJavaManagementState(),
    onDownloadJava: (Int) -> Unit = {},
    onInstallJavaArchive: (Int, Uri) -> Unit = { _, _ -> },
    onDeleteJava: (Int) -> Unit = {},
    serverDirectoryUri: String? = null,
    onServerDirectorySelected: (Uri?) -> Unit = {},
    onRequestServerDirectory: () -> Unit = {},
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 0.dp,
    settingsDestination: SettingsDestination = SettingsDestination.Overview,
    onSettingsDestinationChange: (SettingsDestination) -> Unit = {},
) {
    val settingsSections = remember(appearancePreferences.themeMode, appearancePreferences.accentPreset, appearancePreferences.cardTransparencyPercent, appearancePreferences.transparentCards, appearancePreferences.dynamicBackground, javaManagementState.summaryLabel) {
        McGoSampleRepository.settingsSections().map { section ->
            when (section.icon) {
                SettingsCategoryIcon.Appearance -> section.copy(highlight = appearancePreferences.summaryLabel())
                SettingsCategoryIcon.JavaRuntime -> section.copy(
                    subtitle = javaManagementState.summaryLabel.replace(" / ", "/") + " 托管",
                    highlight = javaManagementState.summaryLabel,
                )
                else -> section
            }
        }
    }
    val appearanceSection = settingsSections.first { it.icon == SettingsCategoryIcon.Appearance }
    val javaManagementSection = settingsSections.first { it.icon == SettingsCategoryIcon.JavaRuntime }
    val runtimePermissionSection = settingsSections.first { it.icon == SettingsCategoryIcon.RuntimePermissions }
    val context = LocalContext.current
    var postNotificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var batteryOptimizationIgnored by remember { mutableStateOf(context.isIgnoringBatteryOptimizations()) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        postNotificationsGranted = granted
    }
    @Suppress("UNUSED_VARIABLE")
    val keepServerDirectoryCallback = onServerDirectorySelected
    var pendingJavaInstallVersion by rememberSaveable { mutableStateOf<Int?>(null) }
    val javaArchivePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val version = pendingJavaInstallVersion
        pendingJavaInstallVersion = null
        if (uri != null && version != null) {
            onInstallJavaArchive(version, uri)
        }
    }
    val requestJavaArchive: (Int) -> Unit = { version ->
        pendingJavaInstallVersion = version
        javaArchivePickerLauncher.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "application/x-xz", "application/x-tar", "application/gzip", "*/*"))
    }
    val runtimePermissionState = defaultRuntimePermissionState(
        postNotificationsGranted = postNotificationsGranted,
        wakeLockGranted = context.checkSelfPermission(Manifest.permission.WAKE_LOCK) == PackageManager.PERMISSION_GRANTED,
        foregroundServiceGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.checkSelfPermission(Manifest.permission.FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        },
        serverDirectorySelected = serverDirectoryUri != null,
        batteryOptimizationIgnored = batteryOptimizationIgnored,
        serverDirectoryUri = serverDirectoryUri,
    )
    val onRuntimePermissionAction: (RuntimePermissionItem) -> Unit = { item ->
        when (item.id) {
            "post-notifications" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            "server-directory" -> onRequestServerDirectory()
            "battery-optimization" -> {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}"),
                    )
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                }
                context.startActivity(intent)
                batteryOptimizationIgnored = context.isIgnoringBatteryOptimizations()
            }
            else -> context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
            )
        }
    }
    val appearanceOptions = remember { McGoSampleRepository.appearanceSettings() }
    val navigationState = remember(settingsDestination) { SettingsNavigationState(destination = settingsDestination) }

    BackHandler(enabled = navigationState.canNavigateBack) {
        onSettingsDestinationChange(navigationState.navigateBack().destination)
    }

    when (navigationState.destination) {
        SettingsDestination.Overview -> SettingsOverview(
            modifier = modifier,
            bottomContentPadding = bottomContentPadding,
            sections = settingsSections,
            onOpenAppearance = { onSettingsDestinationChange(navigationState.openAppearance().destination) },
            onOpenJavaManagement = { onSettingsDestinationChange(navigationState.openJavaManagement().destination) },
            onOpenRuntimePermissions = { onSettingsDestinationChange(navigationState.openRuntimePermissions().destination) },
        )
        SettingsDestination.Appearance -> AppearanceDetailScreen(
            modifier = modifier,
            bottomContentPadding = bottomContentPadding,
            section = appearanceSection,
            appearancePreferences = appearancePreferences,
            appearanceOptions = appearanceOptions,
            onNavigateBack = { onSettingsDestinationChange(navigationState.navigateBack().destination) },
            onAppearancePreferencesChange = onAppearancePreferencesChange,
        )
        SettingsDestination.JavaManagement -> JavaManagementDetailScreen(
            modifier = modifier,
            bottomContentPadding = bottomContentPadding,
            section = javaManagementSection,
            state = javaManagementState,
            onNavigateBack = { onSettingsDestinationChange(navigationState.navigateBack().destination) },
            onDownloadJava = onDownloadJava,
            onImportJava = requestJavaArchive,
            onDeleteJava = onDeleteJava,
        )
        SettingsDestination.RuntimePermissions -> RuntimePermissionDetailScreen(
            modifier = modifier,
            bottomContentPadding = bottomContentPadding,
            section = runtimePermissionSection,
            state = runtimePermissionState,
            onNavigateBack = { onSettingsDestinationChange(navigationState.navigateBack().destination) },
            onPermissionAction = onRuntimePermissionAction,
        )
    }
}

@Composable
private fun SettingsOverview(
    sections: List<SettingsSectionState>,
    onOpenAppearance: () -> Unit,
    onOpenJavaManagement: () -> Unit,
    onOpenRuntimePermissions: () -> Unit,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 0.dp,
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
                        SettingsCategoryIcon.RuntimePermissions -> onOpenRuntimePermissions
                        else -> onOpenAppearance
                    },
                )
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp + bottomContentPadding)) }
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
    bottomContentPadding: Dp = 0.dp,
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
        item { Spacer(modifier = Modifier.height(24.dp + bottomContentPadding)) }
    }
}

@Composable
private fun JavaManagementDetailScreen(
    section: SettingsSectionState,
    state: JavaManagementState,
    onNavigateBack: () -> Unit,
    onDownloadJava: (Int) -> Unit,
    onImportJava: (Int) -> Unit,
    onDeleteJava: (Int) -> Unit,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 0.dp,
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
                title = state.sectionTitle,
                options = state.runtimeOptions,
                onDownload = onDownloadJava,
                onImport = onImportJava,
                onDelete = onDeleteJava,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item { Spacer(modifier = Modifier.height(24.dp + bottomContentPadding)) }
    }
}

@Composable
private fun RuntimePermissionDetailScreen(
    section: SettingsSectionState,
    state: RuntimePermissionState,
    onNavigateBack: () -> Unit,
    onPermissionAction: (RuntimePermissionItem) -> Unit,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 0.dp,
) {
    val detailChrome = SettingsDetailChrome.forDestination(SettingsDestination.RuntimePermissions)

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
            RuntimePermissionCard(
                permissions = state.permissionItems,
                onPermissionAction = onPermissionAction,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item { Spacer(modifier = Modifier.height(24.dp + bottomContentPadding)) }
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
                    text = appearancePreferences.accentPreset.label,
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
                            text = "${appearancePreferences.accentPreset.label} · ${appearancePreferences.cardTransparencyPercent}%",
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
    title: String,
    options: List<JavaRuntimeOption>,
    onDownload: (Int) -> Unit,
    onImport: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = screenTextColors(LocalMcGoVisualTokens.current)
    GlassCard(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.primary,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            options.forEach { option ->
                JavaRuntimeOptionRow(option = option, onDownload = onDownload, onImport = onImport, onDelete = onDelete)
            }
        }
    }
}

@Composable
private fun JavaRuntimeOptionRow(
    option: JavaRuntimeOption,
    onDownload: (Int) -> Unit,
    onImport: (Int) -> Unit,
    onDelete: (Int) -> Unit,
) {
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
        Spacer(modifier = Modifier.width(8.dp))
        option.primaryActionLabel?.let { action ->
            TextButton(
                onClick = {
                    if (option.onlineInstallAvailable) onDownload(option.majorVersion) else onImport(option.majorVersion)
                },
            ) {
                Text(action)
            }
        }
        option.deleteActionLabel?.let { action ->
            TextButton(onClick = { onDelete(option.majorVersion) }) {
                Text(action, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun RuntimePermissionCard(
    permissions: List<RuntimePermissionItem>,
    onPermissionAction: (RuntimePermissionItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = screenTextColors(LocalMcGoVisualTokens.current)
    GlassCard(modifier = modifier) {
        Text(
            text = "权限状态",
            style = MaterialTheme.typography.titleMedium,
            color = colors.primary,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            permissions.forEach { item ->
                RuntimePermissionRow(item = item, onPermissionAction = onPermissionAction)
            }
        }
    }
}

@Composable
private fun RuntimePermissionRow(
    item: RuntimePermissionItem,
    onPermissionAction: (RuntimePermissionItem) -> Unit,
) {
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (item.status == RuntimePermissionStatus.Granted) MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                contentColor = if (item.status == RuntimePermissionStatus.Granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            ) {
                Text(
                    text = item.statusLabel,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            item.actionLabel?.let { action ->
                TextButton(onClick = { onPermissionAction(item) }) {
                    Text(action)
                }
            }
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
    SettingsCategoryIcon.RuntimePermissions -> Icons.Outlined.Notifications
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
