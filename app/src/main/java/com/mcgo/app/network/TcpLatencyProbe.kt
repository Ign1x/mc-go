package com.mcgo.app.network

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

data class TcpEndpoint(
    val host: String,
    val port: Int,
)

fun parseTcpEndpoint(rawEndpoint: String): TcpEndpoint? {
    val trimmed = rawEndpoint.trim()
    if (trimmed.isBlank()) return null

    val (host, portText) = if (trimmed.startsWith("[")) {
        val closingIndex = trimmed.indexOf(']')
        if (closingIndex <= 1 || closingIndex + 2 > trimmed.lastIndex || trimmed.getOrNull(closingIndex + 1) != ':') {
            return null
        }
        trimmed.substring(1, closingIndex) to trimmed.substring(closingIndex + 2)
    } else {
        val separatorIndex = trimmed.lastIndexOf(':')
        if (separatorIndex <= 0 || separatorIndex == trimmed.lastIndex) return null
        if (trimmed.indexOf(':') != separatorIndex) return null
        trimmed.substring(0, separatorIndex) to trimmed.substring(separatorIndex + 1)
    }

    val port = portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
    return TcpEndpoint(host.trim().takeIf { it.isNotBlank() } ?: return null, port)
}

fun formatTcpEndpoint(host: String, port: Int): String {
    require(port in 1..65535) { "TCP 端口无效：$port" }
    val trimmedHost = host.trim()
    require(trimmedHost.isNotBlank()) { "TCP 主机不能为空" }
    val displayHost = if (trimmedHost.startsWith("[") && trimmedHost.endsWith("]")) {
        trimmedHost
    } else if (':' in trimmedHost) {
        "[$trimmedHost]"
    } else {
        trimmedHost
    }
    return "$displayHost:$port"
}

fun measureTcpLatency(
    endpoint: TcpEndpoint,
    timeoutMillis: Int = 1500,
    connector: (TcpEndpoint, Int) -> Unit = ::connectTcp,
    nanoTime: () -> Long = System::nanoTime,
): Int? = try {
    val start = nanoTime()
    connector(endpoint, timeoutMillis)
    val elapsedMillis = ((nanoTime() - start) / 1_000_000L).toInt()
    elapsedMillis.coerceAtLeast(1)
} catch (_: IOException) {
    null
} catch (_: SecurityException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

private fun connectTcp(endpoint: TcpEndpoint, timeoutMillis: Int) {
    Socket().use { socket ->
        socket.connect(InetSocketAddress(endpoint.host, endpoint.port), timeoutMillis)
    }
}
