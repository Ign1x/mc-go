package com.mcgo.app.ui.model

private const val PendingLatencyMs = 0
private const val UnreachableLatencyMs = -1

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
        addressLabel = "服务端地址（IP/域名:端口）",
        addressHint = "例如 1.2.3.4:7000 / frp.example.com:7000",
        credentialLabel = "Token",
        credentialHint = "填写服务端分配的 token",
        portRangeLabel = "可分配端口范围",
        portRangeHint = "例如 38000-38100",
    )
    TunnelKind.Nps -> TunnelManualFieldSpec(
        addressLabel = "服务端地址（IP/域名:端口）",
        addressHint = "例如 1.2.3.4:8024 / nps.example.com:8024",
        credentialLabel = "VKey",
        credentialHint = "填写客户端对应的 vkey",
        portRangeLabel = "映射端口范围",
        portRangeHint = "例如 39000-39100",
    )
    TunnelKind.Playit -> TunnelManualFieldSpec(
        addressLabel = "服务端地址（IP/域名:端口）",
        addressHint = "例如 playit.gg:443 / 节点域名:端口",
        credentialLabel = "Agent Key",
        credentialHint = "填写 Playit agent key",
        portRangeLabel = "预留端口范围",
        portRangeHint = "例如 25565-25585",
    )
    TunnelKind.Tailscale -> TunnelManualFieldSpec(
        addressLabel = "服务端地址（IP/域名:端口）",
        addressHint = "例如 100.x.y.z:41641 / tailnet 节点:端口",
        credentialLabel = "Auth Key",
        credentialHint = "填写 tailscale auth key 或设备授权信息",
        portRangeLabel = "可用端口范围",
        portRangeHint = "例如 25565-25600",
    )
    TunnelKind.Custom -> TunnelManualFieldSpec(
        addressLabel = "服务端地址（IP/域名:端口）",
        addressHint = "例如 tunnel.example.com:443",
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
    val baseLatencyMs: Int = PendingLatencyMs,
    val currentLatencyMs: Int = PendingLatencyMs,
    val healthLabel: String = latencyHealthLabel(PendingLatencyMs),
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

    fun latencyLabel(): String = when {
        currentLatencyMs > 0 -> "${currentLatencyMs} ms"
        currentLatencyMs == PendingLatencyMs -> "检测中"
        else -> "不可达"
    }

    fun latencyBadgeLines(): List<String> {
        val latency = latencyLabel()
        return if (latency == healthLabel) listOf(latency) else listOf(latency, healthLabel)
    }

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
                add("${kind.label} 服务器参数")
                credentialValue?.takeIf { it.isNotBlank() }?.let { add("${spec.credentialLabel} 已保存") }
                if (!portRange.isNullOrBlank()) add("端口范围可编辑")
                add("开服时可改端口")
            }.joinToString(" · ")
        }
        TunnelSource.PastedConfig -> "${kind.label} ${format?.label ?: TunnelConfigFormat.Unknown.label} 配置 · 可再次编辑粘贴内容"
    }

    fun formatLabel(): String? = format?.label

    fun withLatencyResult(latencyMs: Int?): TunnelProfile {
        val resolvedLatency = latencyMs?.coerceAtLeast(1) ?: UnreachableLatencyMs
        return copy(
            currentLatencyMs = resolvedLatency,
            healthLabel = latencyHealthLabel(resolvedLatency),
        )
    }

    companion object {
        fun manualServer(
            name: String,
            kind: TunnelKind,
            serverAddress: String,
            credentialValue: String,
            portRange: String,
            baseLatencyMs: Int = PendingLatencyMs,
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
                currentLatencyMs = PendingLatencyMs,
                healthLabel = latencyHealthLabel(PendingLatencyMs),
                detail = buildList {
                    add("${kind.label} 服务器参数")
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
): ServerCardState = startPaperServer(tunnel = tunnel, startupPort = startupPort)

fun ServerCardState.startPaperServer(
    tunnel: TunnelProfile?,
    startupPort: Int?,
): ServerCardState {
    val resolvedPort = tunnel?.resolveStartupPort(defaultPort, startupPort) ?: startupPort ?: defaultPort
    val plan = PaperLaunchPlan(
        serverJarName = "paper-$minecraftVersion.jar",
        javaMajorVersion = javaMajorVersion,
        arguments = listOf(
            "java-$javaMajorVersion",
            "-Xms${(memoryMb / 2).coerceAtLeast(512)}M",
            "-Xmx${memoryMb}M",
            "-jar",
            "paper-$minecraftVersion.jar",
            "nogui",
        ),
    )
    return copy(
        isOnline = true,
        port = resolvedPort,
        selectedTunnelId = tunnel?.id,
        activeTunnelLabel = tunnel?.let { "${it.name} · ${it.latencyLabel()}" },
        launchStatus = ServerLaunchStatus.Running,
        launchPlan = plan,
    )
}

fun ServerCardState.stopServer(): ServerCardState = copy(
    isOnline = false,
    port = defaultPort,
    activeTunnelLabel = null,
    launchStatus = ServerLaunchStatus.Stopped,
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

data class TunnelLatencyResult(
    val tunnelId: String,
    val serverAddress: String,
    val latencyMs: Int?,
)

fun applyTunnelLatencyResults(
    profiles: List<TunnelProfile>,
    results: List<TunnelLatencyResult>,
): List<TunnelProfile> {
    val resultsByTunnel = results.associateBy { it.tunnelId }
    return profiles.map { profile ->
        val result = resultsByTunnel[profile.id]
        if (result != null && result.serverAddress == profile.serverAddress) {
            profile.withLatencyResult(result.latencyMs)
        } else {
            profile
        }
    }
}

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
    val serverHost = extractString(
        cleanedConfig,
        listOf("serverAddr", "server_addr", "server_address", "host", "server"),
    )?.takeIf { it.isNotBlank() } ?: "待解析地址"
    val serverPort = extractInt(cleanedConfig, listOf("serverPort", "server_port", "server-port", "port"))
    val remotePort = extractInt(cleanedConfig, listOf("remotePort", "remote_port", "remote", "bind_port"))
    val localPort = extractInt(cleanedConfig, listOf("localPort", "local_port", "local", "listen_port"))
    return TunnelProfile(
        id = createTunnelId(resolvedName, TunnelSource.PastedConfig),
        name = resolvedName,
        kind = kind,
        source = TunnelSource.PastedConfig,
        format = format,
        serverAddress = combineHostAndPort(serverHost, serverPort),
        remotePort = remotePort,
        localPort = localPort,
        credentialValue = extractString(cleanedConfig, listOf("token", "auth_token", "vkey", "secret_key")),
        portRange = extractPortRange(cleanedConfig),
        baseLatencyMs = PendingLatencyMs,
        currentLatencyMs = PendingLatencyMs,
        healthLabel = latencyHealthLabel(PendingLatencyMs),
        rawConfigPreview = cleanedConfig.lineSequence().take(4).joinToString("\n"),
        rawConfigText = cleanedConfig,
        detail = "${kind.label} ${format.label} 配置 · 启动时作为单隧道使用",
    )
}

private fun combineHostAndPort(host: String, port: Int?): String {
    val trimmedHost = host.trim()
    if (trimmedHost.isBlank() || port == null) return trimmedHost
    if (Regex("^\\[[^]]+]:\\d+$").matches(trimmedHost) || Regex("^[^:]+:\\d+$").matches(trimmedHost)) {
        return trimmedHost
    }
    return "$trimmedHost:$port"
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

private fun latencyHealthLabel(latencyMs: Int): String = when {
    latencyMs < 0 -> "不可达"
    latencyMs == PendingLatencyMs -> "检测中"
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
