package com.mcgo.app.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mcgo.app.server.deleteManagedServerIcon
import com.mcgo.app.server.deleteManagedServerIconFromAuthorizedDirectory
import com.mcgo.app.server.managedPaperServerIconFile
import com.mcgo.app.server.syncManagedServerIconToAuthorizedDirectory
import com.mcgo.app.server.writeManagedServerIcon
import com.mcgo.app.ui.model.JavaSelectionMode
import com.mcgo.app.ui.model.MinecraftServerType
import com.mcgo.app.ui.model.PaperDifficulty
import com.mcgo.app.ui.model.PaperGameMode
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.applyPaperServerEdits
import com.mcgo.app.ui.model.buildPaperServerPropertiesEditorText
import com.mcgo.app.ui.model.isRuntimeBusy
import com.mcgo.app.ui.model.parsePaperServerPropertiesEditorText
import com.mcgo.app.ui.model.recommendedJavaMajorVersion
import com.mcgo.app.ui.model.sanitizeAdvancedServerPropertiesOverride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PendingServerIconCrop(
    val sourceUri: Uri,
    val previewBitmap: Bitmap,
)

@Composable
internal fun EditPaperServerDialog(
    server: ServerCardState,
    vanillaVersions: List<String>,
    paperVersions: List<String>,
    purpurVersions: List<String>,
    fabricVersions: List<String>,
    forgeVersions: List<String>,
    neoForgeVersions: List<String>,
    quiltVersions: List<String>,
    supportedProvisionableJavaVersions: Set<Int>,
    dynamicBackground: Boolean,
    serverDirectoryUri: String?,
    onDismiss: () -> Unit,
    onSave: (ServerCardState) -> Unit,
) {
    val baseVersionOptions: List<String> = remember(server.serverType, vanillaVersions, paperVersions, purpurVersions, fabricVersions, forgeVersions, neoForgeVersions, quiltVersions, supportedProvisionableJavaVersions) {
        when (server.serverType) {
            MinecraftServerType.Vanilla -> vanillaVersions.filter { recommendedJavaMajorVersion(it) in supportedProvisionableJavaVersions }
            MinecraftServerType.Paper -> com.mcgo.app.server.resolveProvisionablePaperVersionOptions(
                versions = paperVersions,
                supportedProvisionableJavaVersions = supportedProvisionableJavaVersions,
            )
            MinecraftServerType.Purpur -> purpurVersions.filter { recommendedJavaMajorVersion(it) in supportedProvisionableJavaVersions }
            MinecraftServerType.Fabric -> fabricVersions.filter { recommendedJavaMajorVersion(it) in supportedProvisionableJavaVersions }
            MinecraftServerType.Forge -> forgeVersions.filter { recommendedJavaMajorVersion(it) in supportedProvisionableJavaVersions }
            MinecraftServerType.NeoForge -> neoForgeVersions.filter { recommendedJavaMajorVersion(it) in supportedProvisionableJavaVersions }
            MinecraftServerType.Quilt -> quiltVersions.filter { recommendedJavaMajorVersion(it) in supportedProvisionableJavaVersions }
        }
    }
    val versionOptions: List<String> = remember(baseVersionOptions, server.minecraftVersion) {
        if (baseVersionOptions.contains(server.minecraftVersion)) baseVersionOptions else baseVersionOptions + server.minecraftVersion
    }
    var name by remember(server.id) { mutableStateOf(server.name) }
    var minecraftVersion by remember(server.id) { mutableStateOf(server.minecraftVersion) }
    var javaSelectionMode by remember(server.id) { mutableStateOf(server.javaSelectionMode) }
    var manualJavaMajorVersion by remember(server.id) { mutableStateOf(server.javaMajorVersion) }
    var maxPlayers by remember(server.id) { mutableStateOf(server.maxPlayers.toString()) }
    var memoryMb by remember(server.id) { mutableStateOf(server.memoryMb.toString()) }
    var port by remember(server.id) { mutableStateOf(server.defaultPort.toString()) }
    var worldName by remember(server.id) { mutableStateOf(server.worldName) }
    var gameMode by remember(server.id) { mutableStateOf(server.gameMode) }
    var difficulty by remember(server.id) { mutableStateOf(server.difficulty) }
    var onlineMode by remember(server.id) { mutableStateOf(server.onlineMode) }
    var pvpEnabled by remember(server.id) { mutableStateOf(server.pvpEnabled) }
    var serverPropertiesOverride by remember(server.id) { mutableStateOf(server.serverPropertiesOverride) }
    var overlayDestination by remember(server.id) { mutableStateOf(EditServerOverlayDestination.Form) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingServerIconChange by remember(server.id) { mutableStateOf<PendingServerIconChange>(PendingServerIconChange.Unchanged) }
    var pendingServerIconCrop by remember(server.id) { mutableStateOf<PendingServerIconCrop?>(null) }
    val iconPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val preparedCrop = withContext(Dispatchers.IO) {
                runCatching {
                    val previewBitmap = decodeServerIconPreviewBitmap(context, uri)
                    PendingServerIconCrop(
                        sourceUri = uri,
                        previewBitmap = previewBitmap,
                    )
                }
            }
            preparedCrop.onSuccess { cropState ->
                pendingServerIconCrop = cropState
                overlayDestination = EditServerOverlayDestination.IconCrop
            }.onFailure { error ->
                Toast.makeText(
                    context,
                    "服务器图标读取失败：${error.message ?: "请换一张图片再试"}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    val recommendedJava = remember(minecraftVersion) { recommendedJavaMajorVersion(minecraftVersion) }
    LaunchedEffect(minecraftVersion, javaSelectionMode) {
        if (javaSelectionMode == JavaSelectionMode.Recommended) {
            manualJavaMajorVersion = recommendedJava
        }
    }

    val javaVersionOptions = remember(
        supportedProvisionableJavaVersions,
        server.javaMajorVersion,
        manualJavaMajorVersion,
        recommendedJava,
    ) {
        buildList {
            add(recommendedJava)
            add(server.javaMajorVersion)
            add(manualJavaMajorVersion)
            addAll(supportedProvisionableJavaVersions)
        }.distinct().sorted()
    }
    val resolvedMaxPlayers = maxPlayers.toIntOrNull()?.coerceIn(1, 200) ?: server.maxPlayers
    val resolvedMemoryMb = memoryMb.toIntOrNull()?.coerceAtLeast(512) ?: server.memoryMb
    val resolvedPort = port.toIntOrNull()?.coerceIn(1, 65535) ?: server.defaultPort
    val resolvedJavaMajorVersion = if (javaSelectionMode == JavaSelectionMode.Recommended) recommendedJava else manualJavaMajorVersion
    val canSave = name.isNotBlank() && minecraftVersion.isNotBlank()

    fun buildDraftServer(): ServerCardState = applyPaperServerEdits(
        server = server,
        name = name,
        minecraftVersion = minecraftVersion,
        maxPlayers = resolvedMaxPlayers,
        memoryMb = resolvedMemoryMb,
        port = resolvedPort,
        worldName = worldName,
        javaMajorVersion = resolvedJavaMajorVersion,
        javaSelectionMode = javaSelectionMode,
        gameMode = gameMode,
        difficulty = difficulty,
        onlineMode = onlineMode,
        pvpEnabled = pvpEnabled,
        serverPropertiesOverride = sanitizeAdvancedServerPropertiesOverride(serverPropertiesOverride),
    ).copy(
        serverIconVersion = when (pendingServerIconChange) {
            PendingServerIconChange.Unchanged -> server.serverIconVersion
            PendingServerIconChange.Remove -> 0L
            is PendingServerIconChange.Replace -> System.currentTimeMillis()
        },
    )

    fun applyDraftToForm(editedServer: ServerCardState) {
        name = editedServer.name
        minecraftVersion = editedServer.minecraftVersion
        javaSelectionMode = editedServer.javaSelectionMode
        manualJavaMajorVersion = editedServer.javaMajorVersion
        maxPlayers = editedServer.maxPlayers.toString()
        memoryMb = editedServer.memoryMb.toString()
        port = editedServer.defaultPort.toString()
        worldName = editedServer.worldName
        gameMode = editedServer.gameMode
        difficulty = editedServer.difficulty
        onlineMode = editedServer.onlineMode
        pvpEnabled = editedServer.pvpEnabled
        serverPropertiesOverride = editedServer.serverPropertiesOverride
    }

    BackHandler(enabled = true, onBack = onDismiss)

    when (overlayDestination) {
        EditServerOverlayDestination.Properties -> {
            val draftServer = buildDraftServer()
            PaperServerPropertiesEditorDialog(
                server = draftServer,
                initialText = buildPaperServerPropertiesEditorText(draftServer),
                dynamicBackground = dynamicBackground,
                onDismiss = { overlayDestination = EditServerOverlayDestination.Form },
                onApply = { editedText ->
                    applyDraftToForm(parsePaperServerPropertiesEditorText(draftServer, editedText))
                    overlayDestination = EditServerOverlayDestination.Form
                },
            )
        }

        EditServerOverlayDestination.IconCrop -> {
            pendingServerIconCrop?.let { cropState ->
                ServerIconCropDialog(
                    previewBitmap = cropState.previewBitmap,
                    dynamicBackground = dynamicBackground,
                    onDismiss = {
                        pendingServerIconCrop = null
                        overlayDestination = EditServerOverlayDestination.Form
                    },
                    onApply = { pngBytes ->
                        pendingServerIconChange = PendingServerIconChange.Replace(pngBytes)
                        pendingServerIconCrop = null
                        overlayDestination = EditServerOverlayDestination.Form
                    },
                )
            }
        }

        EditServerOverlayDestination.Form -> {
            EditFullScreenScaffold(
                title = "编辑 ${server.name}",
                subtitle = "",
                leadingIcon = Icons.Outlined.Tune,
                dynamicBackground = dynamicBackground,
                layoutMode = EditFullScreenScaffoldLayoutMode.ScrollableChrome,
                onDismiss = onDismiss,
                footer = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { overlayDestination = EditServerOverlayDestination.Properties },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("编辑 server.properties")
                        }
                        Button(
                            onClick = {
                                val editedServer = buildDraftServer()
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        when (val change = pendingServerIconChange) {
                                            PendingServerIconChange.Unchanged -> Unit
                                            PendingServerIconChange.Remove -> {
                                                deleteManagedServerIcon(context.filesDir.toPath(), editedServer.id)
                                                if (serverDirectoryUri != null) {
                                                    deleteManagedServerIconFromAuthorizedDirectory(
                                                        context = context,
                                                        authorizedDirectoryUri = serverDirectoryUri,
                                                        serverId = editedServer.id,
                                                        fileName = "server-icon.png",
                                                    )
                                                }
                                            }
                                            is PendingServerIconChange.Replace -> {
                                                writeManagedServerIcon(context.filesDir.toPath(), editedServer.id, change.pngBytes)
                                                if (serverDirectoryUri != null) {
                                                    syncManagedServerIconToAuthorizedDirectory(
                                                        context = context,
                                                        authorizedDirectoryUri = serverDirectoryUri,
                                                        serverId = editedServer.id,
                                                        iconPath = managedPaperServerIconFile(context.filesDir.toPath(), editedServer.id),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    onSave(editedServer)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canSave,
                        ) {
                            Text("保存配置")
                        }
                    }
                },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (server.isRuntimeBusy()) {
                        EditSettingsInfoCard(
                            icon = Icons.Outlined.Warning,
                            title = "当前运行中，只更新配置资料",
                            body = "服务器当前正在启动或运行；本次保存仅更新配置资料，不会强制改动当前运行中的端口与日志状态。",
                        )
                    }

                    EditSettingsSectionCard(title = "图标与基础设置") {
                        ServerIconEditorCard(
                            server = server,
                            pendingServerIconChange = pendingServerIconChange,
                            onPickIcon = {
                                iconPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            onRemoveIcon = { pendingServerIconChange = PendingServerIconChange.Remove },
                            showRemoveAction = true,
                            pickButtonLabel = "更换图标",
                            preferSingleRowActions = true,
                        )
                        EditSettingsDivider()
                        EditTextSettingRow(
                            icon = Icons.Outlined.Edit,
                            label = "服务器名称",
                            value = name,
                            placeholder = "请输入名称",
                            onValueChange = { name = it },
                        )
                        EditSettingsDivider()
                        EditMenuSettingRow(
                            icon = Icons.Outlined.Dns,
                            label = "Minecraft 版本",
                            valueLabel = minecraftVersion,
                            options = versionOptions.asReversed(),
                            optionLabel = { it },
                            onSelect = { minecraftVersion = it },
                        )
                    }

                    EditSettingsSectionCard(title = "核心与性能") {
                        EditMenuSettingRow(
                            icon = Icons.Outlined.Settings,
                            label = "Java",
                            valueLabel = if (javaSelectionMode == JavaSelectionMode.Recommended) {
                                "自动"
                            } else {
                                "Java $manualJavaMajorVersion"
                            },
                            options = listOf<String>("自动") + javaVersionOptions.map { "Java $it" },
                            optionLabel = { it },
                            onSelect = { selected ->
                                if (selected == "自动") {
                                    javaSelectionMode = JavaSelectionMode.Recommended
                                    manualJavaMajorVersion = recommendedJava
                                } else {
                                    javaSelectionMode = JavaSelectionMode.Manual
                                    manualJavaMajorVersion = selected.removePrefix("Java ").toIntOrNull() ?: manualJavaMajorVersion
                                }
                            },
                        )
                        EditSettingsDivider()
                        EditTextSettingRow(
                            icon = Icons.Outlined.Speed,
                            label = "分配内存 MB",
                            value = memoryMb,
                            placeholder = server.memoryMb.toString(),
                            keyboardType = KeyboardType.Number,
                            onValueChange = { memoryMb = it.filter(Char::isDigit) },
                        )
                    }

                    EditSettingsSectionCard(title = "常用游戏规则") {
                        EditTextSettingRow(
                            icon = Icons.Outlined.Public,
                            label = "世界名称",
                            value = worldName,
                            placeholder = "world",
                            onValueChange = { worldName = it },
                        )
                        EditSettingsDivider()
                        EditMenuSettingRow(
                            icon = Icons.Outlined.Tune,
                            label = "游戏模式",
                            valueLabel = gameMode.displayLabel(),
                            options = PaperGameMode.entries,
                            optionLabel = { it.displayLabel() },
                            onSelect = { gameMode = it },
                        )
                        EditSettingsDivider()
                        EditMenuSettingRow(
                            icon = Icons.Outlined.Tune,
                            label = "难度",
                            valueLabel = difficulty.displayLabel(),
                            options = PaperDifficulty.entries,
                            optionLabel = { it.displayLabel() },
                            onSelect = { difficulty = it },
                        )
                        EditSettingsDivider()
                        EditSwitchSettingRow(
                            icon = Icons.Outlined.Public,
                            label = "正版验证",
                            supportingText = "关闭后可允许离线/外网玩家，安全风险更高，请谨慎使用",
                            supportingTextColor = MaterialTheme.colorScheme.error,
                            checked = onlineMode,
                            onCheckedChange = { onlineMode = it },
                        )
                        EditSettingsDivider()
                        EditSwitchSettingRow(
                            icon = Icons.Outlined.Tune,
                            label = "PvP",
                            checked = pvpEnabled,
                            onCheckedChange = { pvpEnabled = it },
                        )
                    }

                    EditSettingsSectionCard(title = "网络与高级") {
                        EditTextSettingRow(
                            icon = Icons.Outlined.Dns,
                            label = "最大玩家数",
                            value = maxPlayers,
                            placeholder = server.maxPlayers.toString(),
                            keyboardType = KeyboardType.Number,
                            onValueChange = { maxPlayers = it.filter(Char::isDigit) },
                        )
                        EditSettingsDivider()
                        EditTextSettingRow(
                            icon = Icons.Outlined.SwapHoriz,
                            label = "默认端口",
                            value = port,
                            placeholder = server.defaultPort.toString(),
                            keyboardType = KeyboardType.Number,
                            onValueChange = { port = it.filter(Char::isDigit) },
                        )
                    }
                }
            }
        }
    }
}


private enum class EditServerOverlayDestination {
    Form,
    Properties,
    IconCrop,
}

@Composable
private fun PaperServerPropertiesEditorDialog(
    server: ServerCardState,
    initialText: String,
    dynamicBackground: Boolean,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    var editorText by remember(server.id, initialText) { mutableStateOf(initialText) }
    val (propertiesBringIntoViewRequester, onPropertiesFocusChanged) = rememberImeBringIntoViewRequester()

    BackHandler(enabled = true, onBack = onDismiss)
    val colors = editPageColors()
    EditFullScreenScaffold(
        title = "编辑 server.properties",
        subtitle = "",
        leadingIcon = Icons.Outlined.Edit,
        dynamicBackground = dynamicBackground,
        layoutMode = EditFullScreenScaffoldLayoutMode.PinnedChrome,
        onDismiss = onDismiss,
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("取消")
                }
                Button(
                    onClick = { onApply(editorText) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("应用并返回")
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                color = colors.editorContainerColor,
                contentColor = colors.primaryText,
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.dp, colors.cardStrokeColor),
            ) {
                BasicTextField(
                    value = editorText,
                    onValueChange = { editorText = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .bringIntoViewRequester(propertiesBringIntoViewRequester)
                        .onFocusEvent { onPropertiesFocusChanged(it.isFocused) }
                        .padding(18.dp)
                        .verticalScroll(rememberScrollState()),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.Transparent,
                        fontFamily = FontFamily.Monospace,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primaryText),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (editorText.isBlank()) {
                                Text(
                                    text = "server.properties",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                    color = colors.secondaryText,
                                )
                            } else {
                                BasicText(
                                    text = buildServerPropertiesAnnotatedText(
                                        editorText,
                                        colors.primaryText,
                                        colors.secondaryText,
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }
        }
    }
}
