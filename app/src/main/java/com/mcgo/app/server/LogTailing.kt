package com.mcgo.app.server

import java.nio.file.Files
import java.nio.file.Path

data class LogTailResult(
    val nextOffset: Long,
    val line: String?,
)

fun readAppendedNonBlankLines(logFile: Path, previousOffset: Long): List<String> = runCatching {
    if (!Files.isRegularFile(logFile)) return@runCatching emptyList()
    val fileSize = Files.size(logFile)
    if (fileSize <= previousOffset) return@runCatching emptyList()
    val startOffset = previousOffset.coerceAtLeast(0L).coerceAtMost(fileSize)
    Files.newInputStream(logFile).use { input ->
        input.skip(startOffset)
        input.readBytes()
    }.toString(Charsets.UTF_8)
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toList()
}.getOrElse { emptyList() }

fun readLastAppendedMatchingLine(logFile: Path, previousOffset: Long, predicate: (String) -> Boolean): LogTailResult = runCatching {
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
        .lastOrNull(predicate)
    LogTailResult(fileSize, line)
}.getOrElse { LogTailResult(previousOffset, null) }

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
