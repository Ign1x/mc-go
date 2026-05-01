package com.mcgo.app.server

import java.nio.file.Files
import java.nio.file.Path

data class LogTailResult(
    val nextOffset: Long,
    val line: String?,
)

fun readLastAppendedNonBlankLine(logFile: Path, previousOffset: Long): LogTailResult = runCatching {
    if (!Files.isRegularFile(logFile)) return@runCatching LogTailResult(previousOffset, null)
    val fileSize = Files.size(logFile)
    if (fileSize <= previousOffset) return@runCatching LogTailResult(fileSize, null)
    val startOffset = previousOffset.coerceAtLeast(0L).coerceAtMost(fileSize)
    val bytes = Files.newInputStream(logFile).use { input ->
        input.skip(startOffset)
        input.readBytes()
    }
    val line = bytes.toString(Charsets.UTF_8)
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .lastOrNull()
    LogTailResult(fileSize, line)
}.getOrElse { LogTailResult(previousOffset, null) }
