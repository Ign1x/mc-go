package com.mcgo.app.server

import com.mcgo.app.network.TcpEndpoint
import com.mcgo.app.network.formatTcpEndpoint
import com.mcgo.app.network.parseTcpEndpoint
import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.TunnelKind
import com.mcgo.app.ui.model.TunnelProfile
import com.mcgo.app.ui.model.TunnelSource
import com.mcgo.app.ui.model.isRuntimeBusy
import com.mcgo.app.ui.model.readableSlug

private const val DefaultBundledFrpcAssetArm64 = "frp/android_arm64/frpc"

data class TunnelRuntimePlan(
    val tunnelId: String,
    val binaryPath: java.nio.file.Path,
    val extractedBinaryPath: java.nio.file.Path,
    val configPath: java.nio.file.Path,
    val configText: String,
    val displayLabel: String,
    val runtimeAddress: String,
    val remotePort: Int,
)

fun defaultBundledFrpcAssetRelativePath(abi: String): String = when (abi) {
    "arm64-v8a" -> DefaultBundledFrpcAssetArm64
    else -> error("暂不支持的 FRP ABI：$abi")
}

fun allocateRuntimeSlot(
    servers: List<ServerCardState>,
    targetServerId: String,
    maxSlots: Int,
): Int? {
    val occupied = servers
        .filter { it.id != targetServerId && it.isRuntimeBusy() }
        .mapNotNull { it.runtimeSlot }
        .toSet()
    return (1..maxSlots).firstOrNull { it !in occupied }
}

fun buildFrpcConfigForTunnel(server: ServerCardState, tunnel: TunnelProfile): String {
    require(tunnel.kind == TunnelKind.Frp) { "当前仅支持 FRP 隧道配置" }
    tunnel.rawConfigText
        ?.takeIf { tunnel.source == TunnelSource.PastedConfig }
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    val trimmedToken = tunnel.credentialValue?.trim()
    require(!trimmedToken.isNullOrBlank()) { "FRP token 不能为空" }
    val endpoint = requireFrpServerEndpoint(tunnel.serverAddress)
    val remotePort = tunnel.remotePort ?: server.tunnelRemotePort ?: server.port
    return buildGeneratedFrpcConfig(
        server = server,
        tunnel = tunnel,
        host = endpoint.host,
        serverPort = endpoint.port,
        remotePort = remotePort,
        token = trimmedToken,
    )
}

private fun buildGeneratedFrpcConfig(
    server: ServerCardState,
    tunnel: TunnelProfile,
    host: String,
    serverPort: Int,
    remotePort: Int,
    token: String,
): String {
    val escapedToken = token
        .replace("\\", "\\\\")
        .replace("\n", "")
        .replace("\r", "")
        .replace("\"", "\\\"")
    val proxyName = listOf(
        "mcgo",
        readableSlug(server.name),
        readableSlug(tunnel.name),
        remotePort.toString(),
    ).filter { it.isNotBlank() }.joinToString("-")
    return buildString {
        appendLine("serverAddr = \"$host\"")
        appendLine("serverPort = $serverPort")
        appendLine()
        appendLine("auth.method = \"token\"")
        appendLine("auth.token = \"$escapedToken\"")
        appendLine()
        appendLine("[[proxies]]")
        appendLine("name = \"$proxyName\"")
        appendLine("type = \"tcp\"")
        appendLine("localIP = \"127.0.0.1\"")
        appendLine("localPort = ${server.port}")
        appendLine("remotePort = $remotePort")
    }
}

fun tunnelRuntimePlansForStart(
    filesDir: java.nio.file.Path,
    nativeLibraryDir: java.nio.file.Path,
    server: ServerCardState,
    tunnels: List<TunnelProfile>,
    supportedAbi: String,
): List<TunnelRuntimePlan> = tunnels.map { tunnel ->
    if (tunnel.kind != TunnelKind.Frp) {
        throw JavaRuntimeInstallException("当前仅支持 FRP 真启动；其他隧道类型暂未接入运行时")
    }
    val frpDir = managedPaperServerDirectory(filesDir, server.id).resolve("frp").resolve(sanitizeManagedServerId(tunnel.id))
    val frpcAssetRelativePath = defaultBundledFrpcAssetRelativePath(supportedAbi)
    val frpcBinaryName = nativeLibraryExecutableNameForAsset(frpcAssetRelativePath)
    val endpoint = requireFrpServerEndpoint(tunnel.serverAddress)
    val remotePort = tunnel.remotePort ?: server.tunnelRemotePort ?: server.port
    TunnelRuntimePlan(
        tunnelId = tunnel.id,
        binaryPath = nativeLibraryDir.resolve(frpcBinaryName),
        extractedBinaryPath = frpDir.resolve("bin").resolve("frpc"),
        configPath = frpDir.resolve("frpc.toml"),
        configText = buildFrpcConfigForTunnel(server, tunnel),
        displayLabel = tunnel.name,
        runtimeAddress = endpoint.runtimeAddress(remotePort),
        remotePort = remotePort,
    )
}

private fun requireFrpServerEndpoint(serverAddress: String): TcpEndpoint =
    parseTcpEndpoint(serverAddress) ?: error("FRP 服务端地址无效")

private fun TcpEndpoint.runtimeAddress(remotePort: Int): String = formatTcpEndpoint(host, remotePort)

fun tunnelRuntimePlanForStart(
    filesDir: java.nio.file.Path,
    nativeLibraryDir: java.nio.file.Path,
    server: ServerCardState,
    tunnel: TunnelProfile?,
    supportedAbi: String,
): TunnelRuntimePlan? = tunnel?.let {
    tunnelRuntimePlansForStart(
        filesDir = filesDir,
        nativeLibraryDir = nativeLibraryDir,
        server = server,
        tunnels = listOf(it),
        supportedAbi = supportedAbi,
    ).single()
}

fun nativeLibraryExecutableNameForAsset(assetRelativePath: String): String {
    val baseName = assetRelativePath.substringAfterLast('/')
    return if (baseName.startsWith("lib") && baseName.endsWith(".so")) baseName else "lib${baseName}.so"
}
