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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mcgo.app.BuildConfig
import com.mcgo.app.ui.components.GlassCard
import com.mcgo.app.ui.model.AppearancePreferences
import com.mcgo.app.ui.model.JavaManagementState
import com.mcgo.app.ui.model.JavaRuntimeOption
import com.mcgo.app.ui.model.RuntimePermissionItem
import com.mcgo.app.ui.model.RuntimePermissionState
import com.mcgo.app.ui.model.RuntimePermissionStatus
import com.mcgo.app.ui.model.SettingsCategoryIcon
import com.mcgo.app.ui.model.SettingsDestination
import com.mcgo.app.ui.model.SettingsDetailChrome
import com.mcgo.app.ui.model.SettingsNavigationState
import com.mcgo.app.ui.model.SettingsSectionState
import com.mcgo.app.ui.model.defaultJavaManagementState
import com.mcgo.app.ui.model.defaultRuntimePermissionState
import com.mcgo.app.ui.sample.McGoSampleRepository
import com.mcgo.app.ui.theme.LocalMcGoVisualTokens
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
    onExportLogs: () -> Unit = {},
    recentLogPreview: String = "",
    onRefreshRecentLogs: () -> Unit = {},
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
    val serverDirectorySection = settingsSections.first { it.icon == SettingsCategoryIcon.Storage }
    val runtimePermissionSection = settingsSections.first { it.icon == SettingsCategoryIcon.RuntimePermissions }
    val helpAndDebugSection = settingsSections.first { it.icon == SettingsCategoryIcon.Diagnostics }
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
        serverDirectorySelected = serverDirectoryUri != null && context.contentResolver.persistedUriPermissions.any { permission ->
            permission.uri.toString() == serverDirectoryUri && permission.isReadPermission && permission.isWritePermission
        },
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
            onOpenServerDirectory = { onSettingsDestinationChange(navigationState.openServerDirectory().destination) },
            onOpenRuntimePermissions = { onSettingsDestinationChange(navigationState.openRuntimePermissions().destination) },
            onOpenHelpAndDebug = { onSettingsDestinationChange(navigationState.openHelpAndDebug().destination) },
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
        SettingsDestination.ServerDirectory -> ServerDirectoryDetailScreen(
            modifier = modifier,
            bottomContentPadding = bottomContentPadding,
            section = serverDirectorySection,
            serverDirectoryUri = serverDirectoryUri,
            onNavigateBack = { onSettingsDestinationChange(navigationState.navigateBack().destination) },
            onRequestServerDirectory = onRequestServerDirectory,
        )
        SettingsDestination.RuntimePermissions -> RuntimePermissionDetailScreen(
            modifier = modifier,
            bottomContentPadding = bottomContentPadding,
            section = runtimePermissionSection,
            state = runtimePermissionState,
            onNavigateBack = { onSettingsDestinationChange(navigationState.navigateBack().destination) },
            onPermissionAction = onRuntimePermissionAction,
        )
        SettingsDestination.HelpAndDebug -> HelpAndDebugDetailScreen(
            modifier = modifier,
            bottomContentPadding = bottomContentPadding,
            section = helpAndDebugSection,
            onNavigateBack = { onSettingsDestinationChange(navigationState.navigateBack().destination) },
            onExportLogs = onExportLogs,
            recentLogPreview = recentLogPreview,
            onRefreshRecentLogs = onRefreshRecentLogs,
        )
    }
}

@Composable
private fun SettingsOverview(
    sections: List<SettingsSectionState>,
    onOpenAppearance: () -> Unit,
    onOpenJavaManagement: () -> Unit,
    onOpenServerDirectory: () -> Unit,
    onOpenRuntimePermissions: () -> Unit,
    onOpenHelpAndDebug: () -> Unit,
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
                        SettingsCategoryIcon.Storage -> onOpenServerDirectory
                        SettingsCategoryIcon.RuntimePermissions -> onOpenRuntimePermissions
                        SettingsCategoryIcon.Diagnostics -> onOpenHelpAndDebug
                        else -> onOpenAppearance
                    },
                )
            }
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
            SettingsDetailHeader(
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
private fun ServerDirectoryDetailScreen(
    section: SettingsSectionState,
    serverDirectoryUri: String?,
    onNavigateBack: () -> Unit,
    onRequestServerDirectory: () -> Unit,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 0.dp,
) {
    val detailChrome = SettingsDetailChrome.forDestination(SettingsDestination.ServerDirectory)
    val colors = screenTextColors(LocalMcGoVisualTokens.current)

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item {
            SettingsDetailHeader(
                title = section.title,
                subtitle = section.subtitle,
                chrome = detailChrome,
                onNavigateBack = onNavigateBack,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            GlassCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = "服务器目录",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.primary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (serverDirectoryUri != null) {
                        "当前已连接外部服务器目录。清除应用数据后，也可以从这里直接重新选择同一目录，把以前的服务器数据接回来。"
                    } else {
                        "这里可以直接设置服务器目录，不需要先新建服务器。清除应用数据后，也可以从这里重新选择之前的目录。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "当前目录",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.secondary,
                        )
                        Text(
                            text = serverDirectoryUri ?: "未选择",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.primary,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onRequestServerDirectory) {
                    Icon(Icons.Outlined.Folder, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("选择目录")
                }
                Text(
                    text = "重新授权同一目录后，MC-GO 会尝试恢复之前同步到该目录的服务器资料与数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondary,
                )
            }
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
            SettingsDetailHeader(
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
private fun HelpAndDebugDetailScreen(
    section: SettingsSectionState,
    onNavigateBack: () -> Unit,
    onExportLogs: () -> Unit,
    recentLogPreview: String,
    onRefreshRecentLogs: () -> Unit,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 0.dp,
) {
    val detailChrome = SettingsDetailChrome.forDestination(SettingsDestination.HelpAndDebug)

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item {
            SettingsDetailHeader(
                title = "帮助与调试",
                subtitle = section.subtitle,
                chrome = detailChrome,
                onNavigateBack = onNavigateBack,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            HelpAndDebugCard(
                onExportLogs = onExportLogs,
                recentLogPreview = recentLogPreview,
                onRefreshRecentLogs = onRefreshRecentLogs,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item { Spacer(modifier = Modifier.height(24.dp + bottomContentPadding)) }
    }
}

@Composable
private fun HelpAndDebugCard(
    onExportLogs: () -> Unit,
    recentLogPreview: String,
    onRefreshRecentLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = screenTextColors(LocalMcGoVisualTokens.current)
    GlassCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "帮助与调试",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.primary,
                )
                Text(
                    text = "遇到问题时，可先提取日志；问题反馈时建议附上日志，能更快定位开服、隧道、Java 或目录授权问题。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondary,
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "关于",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.primary,
                )
                Text(
                    text = "MC-GO 是一个安卓端 Minecraft Java 版手机开服工具，当前页面提供版本信息、日志导出和基础排障说明。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondary,
                )
                Text(
                    text = "当前版本",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.secondary,
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "提取内容会包含：服务器列表配置、隧道配置、运行时权限配置、外观配置，以及各实例当前日志文件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondary,
                )
                Text(
                    text = "默认会自动隐藏隧道凭据、原始隧道配置和目录授权 URI；如仍涉及服务器名、连接地址或异常栈，请分享前自行再检查一遍。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondary,
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "最近日志",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.primary,
                    )
                    TextButton(onClick = onRefreshRecentLogs) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("刷新日志")
                    }
                }
                Text(
                    text = recentLogPreview.ifBlank { "最近还没有日志，启动/停止服务器或导出日志后会在这里显示。" },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondary,
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        TextButton(onClick = onExportLogs) {
            Icon(Icons.Outlined.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("提取日志")
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
