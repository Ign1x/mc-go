package com.mcgo.app.server

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val ManagedServerDebugTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

internal fun buildManagedServerDebugLogLine(
    message: String,
    details: Map<String, Any?> = emptyMap(),
    timestamp: LocalDateTime = LocalDateTime.now(),
): String {
    val normalizedDetails = details.entries
        .asSequence()
        .filter { (_, value) -> value != null }
        .joinToString(separator = " ") { (key, value) -> "$key=$value" }
        .trim()
    val prefix = "[debug] ${ManagedServerDebugTimestampFormatter.format(timestamp)} $message"
    return if (normalizedDetails.isBlank()) prefix else "$prefix | $normalizedDetails"
}

internal fun appendManagedServerDebugLog(
    logFile: Path,
    message: String,
    details: Map<String, Any?> = emptyMap(),
    onLogLine: ((String) -> Unit)? = null,
): String {
    val line = buildManagedServerDebugLogLine(message = message, details = details)
    Files.createDirectories(logFile.parent)
    Files.write(
        logFile,
        "$line\n".toByteArray(StandardCharsets.UTF_8),
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND,
    )
    onLogLine?.invoke(line)
    return line
}
