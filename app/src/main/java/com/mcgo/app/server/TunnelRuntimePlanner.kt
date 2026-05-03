package com.mcgo.app.server

import com.mcgo.app.ui.model.ServerCardState
import com.mcgo.app.ui.model.TunnelKind
import com.mcgo.app.ui.model.TunnelProfile
import com.mcgo.app.ui.model.isRuntimeBusy
import java.net.URI

private const val DefaultBundledFrpcAssetArm64 = "frp/android_arm64/frpc"

data class TunnelRuntimePlan(
    val binaryPath: java.nio.file.Path,
    val configPath: java.nio.file.Path,
    val configText: String,
    val displayLabel: String,
    val runtimeAddress: String,
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
    require(!tunnel.credentialValue.isNullOrBlank()) { "FRP token 不能为空" }
    val endpoint = URI("tcp://${tunnel.serverAddress}")
    val host = endpoint.host ?: error("FRP 服务端地址无效")
    val serverPort = endpoint.port.takeIf { it > 0 } ?: error("FRP 服务端端口无效")
    val remotePort = tunnel.remotePort ?: server.port
    val escapedToken = tunnel.credentialValue
        .replace("\\", "\\\\")
        .replace("\n", "")
        .replace("\r", "")
        .replace("\"", "\\\"")
    return buildString {
        appendLine("serverAddr = \"$host\"")
        appendLine("serverPort = $serverPort")
        appendLine()
        appendLine("auth.method = \"token\"")
        appendLine("auth.token = \"$escapedToken\"")
        appendLine()
        appendLine("[[proxies]]")
        appendLine("name = \"${server.id}\"")
        appendLine("type = \"tcp\"")
        appendLine("localIP = \"127.0.0.1\"")
        appendLine("localPort = ${server.port}")
        appendLine("remotePort = $remotePort")
    }
}

fun tunnelRuntimePlanForStart(
    filesDir: java.nio.file.Path,
    nativeLibraryDir: java.nio.file.Path,
    server: ServerCardState,
    tunnel: TunnelProfile?,
    supportedAbi: String,
): TunnelRuntimePlan? {
    if (tunnel == null) return null
    if (tunnel.kind != TunnelKind.Frp) {
        throw JavaRuntimeInstallException("当前仅支持 FRP 真启动；其他隧道类型暂未接入运行时")
    }
    val frpDir = managedPaperServerDirectory(filesDir, server.id).resolve("frp")
    val frpcAssetRelativePath = defaultBundledFrpcAssetRelativePath(supportedAbi)
    val frpcBinaryName = nativeLibraryExecutableNameForAsset(frpcAssetRelativePath)
    val endpoint = URI("tcp://${tunnel.serverAddress}")
    val host = endpoint.host ?: error("FRP 服务端地址无效")
    val remotePort = tunnel.remotePort ?: server.port
    return TunnelRuntimePlan(
        binaryPath = nativeLibraryDir.resolve(frpcBinaryName),
        configPath = frpDir.resolve("frpc.toml"),
        configText = buildFrpcConfigForTunnel(server, tunnel),
        displayLabel = "${tunnel.name} · $host:$remotePort",
        runtimeAddress = "$host:$remotePort",
    )
}

fun nativeLibraryExecutableNameForAsset(assetRelativePath: String): String {
    val baseName = assetRelativePath.substringAfterLast('/')
    return if (baseName.startsWith("lib") && baseName.endsWith(".so")) baseName else "lib${baseName}.so"
}
