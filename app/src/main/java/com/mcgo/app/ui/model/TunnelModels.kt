package com.mcgo.app.ui.model

import kotlin.random.Random

private const val DefaultManualLatency = 46

enum class TunnelKind(val label: String) {
    Frp("FRP"),
    Nps("NPS"),
    Playit("Playit"),
    Tailscale("Tailscale"),
    Custom("自定义");
}

enum class TunnelSource(val label: String) {
    ManualServer("服务器参数"),
    PastedConfig("单隧道");
}

enum class TunnelConfigFormat(val label: String) {
    Toml("TOML"),
    Yaml("YAML"),
    Json("JSON"),
    Unknown("纯文本");
}

data class TunnelManualFieldSpec(
    val addressLabel: String,
    val addressHint: String,
    val credentialLabel: String,
    val credentialHint: String,
    val portRangeLabel: String,
    val portRangeHint: String,
)

fun manualTunnelFieldSpec(kind: TunnelKind): TunnelManualFieldSpec = when (kind) {
    TunnelKind.Frp -> TunnelManualFieldSpec(
        addressLabel = "FRP 服务地址",
        addressHint = "例如 frp.example.com",
        credentialLabel = "Token",
        credentialHint = "填写服务端分配的 token",
        portRangeLabel = "可分配端口范围",
        portRangeHint = "例如 38000-38100",
    )
    TunnelKind.Nps -> TunnelManualFieldSpec(
        addressLabel = "NPS 服务地址",
        addressHint = "例如 nps.example.com",
        credentialLabel = "VKey",
        credentialHint = "填写客户端对应的 vkey",
        portRangeLabel = "映射端口范围",
        portRangeHint = "例如 39000-39100",
    )
    TunnelKind.Playit -> TunnelManualFieldSpec(
        addressLabel = "Playit 节点 / 区域",
        addressHint = "例如 hk.playit.gg",
        credentialLabel = "Agent Key",
        credentialHint = "填写 Playit agent key",
        portRangeLabel = "预留端口范围",
        portRangeHint = "例如 25565-25585",
    )
    TunnelKind.Tailscale -> TunnelManualFieldSpec(
        addressLabel = "Tailnet / 出口节点",
        addressHint = "例如 home-tailnet / exit-node",
        credentialLabel = "Auth Key",
        credentialHint = "填写 tailscale auth key 或设备授权信息",
        portRangeLabel = "可用端口范围",
        portRangeHint = "例如 25565-25600",
    )
    TunnelKind.Custom -> TunnelManualFieldSpec(
        addressLabel = "服务器地址",
        addressHint = "填写隧道服务地址或节点名",
        credentialLabel = "凭据 / 备注",
        credentialHint = "可填写 token、key 或简单备注",
        portRangeLabel = "端口范围",
        portRangeHint = "例如 25565-25590",
    )
}

data class TunnelProfile(
    val id: String,
    val name: String,
    val kind: TunnelKind,
    val source: TunnelSource,
    val format: TunnelConfigFormat? = null,
    val serverAddress: String,
    val remotePort: Int? = null,
    val localPort: Int? = null,
    val credentialValue: String? = null,
    val portRange: String? = null,
    val baseLatencyMs: Int,
    val currentLatencyMs: Int,
    val healthLabel: String,
    val rawConfigPreview: String? = null,
    val rawConfigText: String? = null,
    val detail: String? = null,
) {
    fun supportsCustomPortOnStart(): Boolean = source == TunnelSource.ManualServer

    fun resolveStartupPort(serverPort: Int, customPort: Int?): Int = when {
        supportsCustomPortOnStart() -> customPort ?: localPort ?: serverPort
        localPort != null -> localPort
        else -> serverPort
    }

    fun startupModeLabel(): String = source.label

    fun latencyLabel(): String = "${currentLatencyMs} ms"

    fun connectionSummary(): String {
        val secondaryLabel = when {
            remotePort != null -> "远端 $remotePort"
            !portRange.isNullOrBlank() -> "端口范围 $portRange"
            else -> null
        }
        return listOfNotNull(serverAddress.takeIf { it.isNotBlank() }, secondaryLabel).joinToString(" · ")
            .ifBlank { "待补充连接信息" }
    }

    fun detailSummary(): String = detail ?: when (source) {
        TunnelSource.ManualServer -> {
            val spec = manualTunnelFieldSpec(kind)
            buildList {
                add("${kind.label} 参数模板")
                credentialValue?.takeIf { it.isNotBlank() }?.let { add("${spec.credentialLabel} 已保存") }
                if (!portRange.isNullOrBlank()) add("端口范围可编辑")
                add("开服时可改端口")
            }.joinToString(" · ")
        }
        TunnelSource.PastedConfig -> "${kind.label} ${format?.label ?: TunnelConfigFormat.Unknown.label} 配置 · 可再次编辑粘贴内容"
    }

    fun formatLabel(): String? = format?.label

    fun withLatency(latencyMs: Int): TunnelProfile = copy(
        currentLatencyMs = latencyMs,
        healthLabel = latencyHealthLabel(latencyMs),
    )

    companion object {
        fun manualServer(
            name: String,
            kind: TunnelKind,
            serverAddress: String,
            credentialValue: String,
            portRange: String,
            baseLatencyMs: Int = defaultLatencyForKind(kind),
        ): TunnelProfile {
            val spec = manualTunnelFieldSpec(kind)
            val resolvedAddress = serverAddress.ifBlank { "未填写地址" }
            return TunnelProfile(
                id = createTunnelId(name, TunnelSource.ManualServer),
                name = name,
                kind = kind,
                source = TunnelSource.ManualServer,
                serverAddress = resolvedAddress,
                remotePort = null,
                localPort = null,
                credentialValue = credentialValue,
                portRange = portRange,
                baseLatencyMs = baseLatencyMs,
                currentLatencyMs = baseLatencyMs,
                healthLabel = latencyHealthLabel(baseLatencyMs),
                detail = buildList {
                    add("${kind.label} 参数模板")
                    if (credentialValue.isNotBlank()) add("${spec.credentialLabel} 已保存")
                    if (portRange.isNotBlank()) add("端口范围 $portRange")
                    add("启动时可改端口")
                }.joinToString(" · "),
            )
        }
    }
}

fun ServerCardState.startWithTunnel(
    tunnel: TunnelProfile?,
    startupPort: Int?,
): ServerCardState {
    val resolvedPort = tunnel?.resolveStartupPort(defaultPort, startupPort) ?: defaultPort
    return copy(
        isOnline = true,
        port = resolvedPort,
        selectedTunnelId = tunnel?.id,
        activeTunnelLabel = tunnel?.let { "${it.name} · ${it.currentLatencyMs} ms" },
    )
}

fun ServerCardState.stopServer(): ServerCardState = copy(
    isOnline = false,
    port = defaultPort,
    activeTunnelLabel = null,
)

fun upsertTunnelProfile(
    profiles: List<TunnelProfile>,
    profile: TunnelProfile,
): List<TunnelProfile> {
    val index = profiles.indexOfFirst { it.id == profile.id }
    return if (index == -1) {
        profiles + profile
    } else {
        profiles.mapIndexed { currentIndex, current -> if (currentIndex == index) profile else current }
    }
}

fun removeTunnelProfile(
    profiles: List<TunnelProfile>,
    tunnelId: String,
): List<TunnelProfile> = profiles.filterNot { it.id == tunnelId }

fun detachDeletedTunnel(
    servers: List<ServerCardState>,
    tunnelId: String,
): List<ServerCardState> = servers.map { server ->
    if (server.selectedTunnelId == tunnelId) {
        server.copy(
            selectedTunnelId = null,
            activeTunnelLabel = null,
        )
    } else {
        server
    }
}

fun importTunnelProfile(
    rawConfig: String,
    fallbackName: String,
): TunnelProfile {
    val cleanedConfig = rawConfig.trim()
    val format = detectTunnelConfigFormat(cleanedConfig)
    val kind = detectTunnelKind(cleanedConfig)
    val resolvedName = extractString(cleanedConfig, listOf("name", "proxy_name", "tunnel_name"))
        ?.takeIf { it.isNotBlank() }
        ?: fallbackName.ifBlank { "导入隧道" }
    val serverAddress = extractString(
        cleanedConfig,
        listOf("serverAddr", "server_addr", "server_address", "host", "server"),
    )?.takeIf { it.isNotBlank() } ?: "待解析地址"
    val remotePort = extractInt(cleanedConfig, listOf("remotePort", "remote_port", "remote", "bind_port"))
    val localPort = extractInt(cleanedConfig, listOf("localPort", "local_port", "local", "listen_port"))
    val baseLatencyMs = (defaultLatencyForKind(kind) - 12).coerceAtLeast(18)
    return TunnelProfile(
        id = createTunnelId(resolvedName, TunnelSource.PastedConfig),
        name = resolvedName,
        kind = kind,
        source = TunnelSource.PastedConfig,
        format = format,
        serverAddress = serverAddress,
        remotePort = remotePort,
        localPort = localPort,
        credentialValue = extractString(cleanedConfig, listOf("token", "auth_token", "vkey", "secret_key")),
        portRange = extractPortRange(cleanedConfig),
        baseLatencyMs = baseLatencyMs,
        currentLatencyMs = baseLatencyMs,
        healthLabel = latencyHealthLabel(baseLatencyMs),
        rawConfigPreview = cleanedConfig.lineSequence().take(4).joinToString("\n"),
        rawConfigText = cleanedConfig,
        detail = "${kind.label} ${format.label} 配置 · 启动时作为单隧道使用",
    )
}

fun simulateTunnelLatencies(
    profiles: List<TunnelProfile>,
    random: Random = Random.Default,
): List<TunnelProfile> = profiles.map { profile ->
    val variance = when {
        profile.baseLatencyMs <= 30 -> 5
        profile.baseLatencyMs <= 60 -> 8
        else -> 12
    }
    val nextLatency = (profile.baseLatencyMs + random.nextInt(-variance, variance + 1)).coerceAtLeast(14)
    profile.withLatency(nextLatency)
}

private fun detectTunnelConfigFormat(rawConfig: String): TunnelConfigFormat {
    val trimmed = rawConfig.trim()
    return when {
        (trimmed.startsWith("{") || trimmed.startsWith("[")) && trimmed.contains(":") -> TunnelConfigFormat.Json
        Regex("^\\s*\\[.+]", setOf(RegexOption.MULTILINE)).containsMatchIn(trimmed) ||
            Regex("^\\s*[A-Za-z0-9_.-]+\\s*=", setOf(RegexOption.MULTILINE)).containsMatchIn(trimmed) -> TunnelConfigFormat.Toml
        Regex("^\\s*[A-Za-z0-9_.-]+\\s*:", setOf(RegexOption.MULTILINE)).containsMatchIn(trimmed) -> TunnelConfigFormat.Yaml
        else -> TunnelConfigFormat.Unknown
    }
}

private fun detectTunnelKind(rawConfig: String): TunnelKind {
    val normalized = rawConfig.lowercase()
    return when {
        normalized.contains("serveraddr") || normalized.contains("remoteport") || normalized.contains("[[proxies]]") || normalized.contains("frp") -> TunnelKind.Frp
        normalized.contains("vkey") || normalized.contains("bridgeport") || normalized.contains("nps") -> TunnelKind.Nps
        normalized.contains("playit") || normalized.contains("secret_key") || normalized.contains("agent_version") -> TunnelKind.Playit
        normalized.contains("tailscale") || normalized.contains("tsnet") -> TunnelKind.Tailscale
        else -> TunnelKind.Custom
    }
}

private fun extractString(rawConfig: String, keys: List<String>): String? {
    keys.forEach { key ->
        val patterns = listOf(
            Regex("\\\"$key\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", RegexOption.IGNORE_CASE),
            Regex("^\\s*$key\\s*[:=]\\s*\\\"?([^\\\"#\\n\\r]+)\\\"?", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
            Regex("^\\s*$key\\s*[:=]\\s*'([^'\\n\\r]+)'", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
        )
        patterns.forEach { pattern ->
            pattern.find(rawConfig)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
    }
    return null
}

private fun extractInt(rawConfig: String, keys: List<String>): Int? {
    keys.forEach { key ->
        val patterns = listOf(
            Regex("\\\"$key\\\"\\s*:\\s*(\\d+)", RegexOption.IGNORE_CASE),
            Regex("^\\s*$key\\s*[:=]\\s*(\\d+)", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
        )
        patterns.forEach { pattern ->
            pattern.find(rawConfig)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        }
    }
    return null
}

private fun extractPortRange(rawConfig: String): String? {
    val rangePatterns = listOf(
        Regex("(\\d{2,5})\\s*[-~]\\s*(\\d{2,5})"),
    )
    rangePatterns.forEach { pattern ->
        pattern.find(rawConfig)?.groupValues?.let { values ->
            val start = values.getOrNull(1)
            val end = values.getOrNull(2)
            if (!start.isNullOrBlank() && !end.isNullOrBlank()) return "$start-$end"
        }
    }
    return null
}

private fun defaultLatencyForKind(kind: TunnelKind): Int = when (kind) {
    TunnelKind.Frp -> 38
    TunnelKind.Nps -> 54
    TunnelKind.Playit -> 62
    TunnelKind.Tailscale -> 28
    TunnelKind.Custom -> DefaultManualLatency
}

private fun latencyHealthLabel(latencyMs: Int): String = when {
    latencyMs <= 35 -> "超快"
    latencyMs <= 70 -> "稳定"
    latencyMs <= 110 -> "可用"
    else -> "偏高"
}

private fun createTunnelId(name: String, source: TunnelSource): String {
    val slug = name.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { source.name.lowercase() }
    return "${source.name.lowercase()}-$slug-${System.currentTimeMillis()}"
}
