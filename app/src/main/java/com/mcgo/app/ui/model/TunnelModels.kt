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

enum class TunnelProtocol(val label: String) {
    Tcp("TCP"),
    Udp("UDP"),
    Http("HTTP"),
    Https("HTTPS");

    companion object {
        fun fromLabel(label: String): TunnelProtocol = entries.firstOrNull { it.label == label } ?: Tcp
    }
}

data class TunnelProfile(
    val id: String,
    val name: String,
    val kind: TunnelKind,
    val source: TunnelSource,
    val format: TunnelConfigFormat? = null,
    val protocol: TunnelProtocol = TunnelProtocol.Tcp,
    val serverAddress: String,
    val remotePort: Int? = null,
    val localPort: Int? = null,
    val baseLatencyMs: Int,
    val currentLatencyMs: Int,
    val healthLabel: String,
    val rawConfigPreview: String? = null,
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
        val protocolLabel = protocol.label
        val remoteLabel = remotePort?.let { "远端 $it" } ?: "远端未解析"
        return "$protocolLabel · $serverAddress · $remoteLabel"
    }

    fun detailSummary(): String = detail ?: when (source) {
        TunnelSource.ManualServer -> "启动时可自定义本地端口"
        TunnelSource.PastedConfig -> "粘贴配置导入，启动时按单隧道选择"
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
            remotePort: Int?,
            protocol: TunnelProtocol = TunnelProtocol.Tcp,
            baseLatencyMs: Int = DefaultManualLatency,
        ): TunnelProfile = TunnelProfile(
            id = createTunnelId(name, TunnelSource.ManualServer),
            name = name,
            kind = kind,
            source = TunnelSource.ManualServer,
            protocol = protocol,
            serverAddress = serverAddress.ifBlank { "未填写地址" },
            remotePort = remotePort,
            localPort = null,
            baseLatencyMs = baseLatencyMs,
            currentLatencyMs = baseLatencyMs,
            healthLabel = latencyHealthLabel(baseLatencyMs),
            detail = "${kind.label} 服务器参数 · 启动时可改端口",
        )
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

fun importTunnelProfile(
    rawConfig: String,
    fallbackName: String,
): TunnelProfile {
    val cleanedConfig = rawConfig.trim()
    val format = detectTunnelConfigFormat(cleanedConfig)
    val kind = detectTunnelKind(cleanedConfig)
    val protocol = detectTunnelProtocol(cleanedConfig)
    val resolvedName = extractString(cleanedConfig, listOf("name", "proxy_name", "tunnel_name"))
        ?.takeIf { it.isNotBlank() }
        ?: fallbackName.ifBlank { "导入隧道" }
    val serverAddress = extractString(
        cleanedConfig,
        listOf("serverAddr", "server_addr", "server_address", "host", "server"),
    )?.takeIf { it.isNotBlank() } ?: "待解析地址"
    val remotePort = extractInt(cleanedConfig, listOf("remotePort", "remote_port", "remote", "bind_port"))
    val localPort = extractInt(cleanedConfig, listOf("localPort", "local_port", "local", "listen_port"))
    val baseLatencyMs = defaultLatencyForKind(kind) - 12
    return TunnelProfile(
        id = createTunnelId(resolvedName, TunnelSource.PastedConfig),
        name = resolvedName,
        kind = kind,
        source = TunnelSource.PastedConfig,
        format = format,
        protocol = protocol,
        serverAddress = serverAddress,
        remotePort = remotePort,
        localPort = localPort,
        baseLatencyMs = baseLatencyMs,
        currentLatencyMs = baseLatencyMs,
        healthLabel = latencyHealthLabel(baseLatencyMs),
        rawConfigPreview = cleanedConfig.lineSequence().take(4).joinToString("\n"),
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
        trimmed.startsWith("{") || trimmed.startsWith("[") && trimmed.contains(":") -> TunnelConfigFormat.Json
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

private fun detectTunnelProtocol(rawConfig: String): TunnelProtocol {
    val normalized = rawConfig.lowercase()
    return when {
        normalized.contains("https") -> TunnelProtocol.Https
        normalized.contains("http") -> TunnelProtocol.Http
        normalized.contains("udp") -> TunnelProtocol.Udp
        else -> TunnelProtocol.Tcp
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
