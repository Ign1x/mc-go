package com.mcgo.app.server

import java.nio.file.Files
import java.nio.file.Path
import java.io.ByteArrayOutputStream

data class LogTailResult(
    val nextOffset: Long,
    val line: String?,
)

data class AppendedLinesResult(
    val nextOffset: Long,
    val lines: List<String>,
)

fun readAppendedNonBlankLines(logFile: Path, previousOffset: Long): List<String> =
    readAppendedNonBlankLinesWithOffset(logFile, previousOffset).lines

fun readAppendedNonBlankLinesWithOffset(logFile: Path, previousOffset: Long): AppendedLinesResult = runCatching {
    if (!Files.isRegularFile(logFile)) return@runCatching AppendedLinesResult(previousOffset, emptyList())
    val fileSize = Files.size(logFile)
    if (fileSize <= previousOffset) return@runCatching AppendedLinesResult(fileSize, emptyList())
    val startOffset = previousOffset.coerceAtLeast(0L).coerceAtMost(fileSize)
    val bytesToRead = (fileSize - startOffset).toInt().coerceAtLeast(0)
    val lines = Files.newInputStream(logFile).use { input ->
        input.skip(startOffset)
        readUpToByteCount(input, bytesToRead)
    }.toString(Charsets.UTF_8)
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toList()
    AppendedLinesResult(fileSize, lines)
}.getOrElse { AppendedLinesResult(previousOffset, emptyList()) }

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
        readUpToByteCount(input, (fileSize - startOffset).toInt().coerceAtLeast(0))
    }
    val line = bytes.toString(Charsets.UTF_8)
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .lastOrNull()
    LogTailResult(fileSize, line)
}.getOrElse { LogTailResult(previousOffset, null) }

private fun readUpToByteCount(input: java.io.InputStream, byteCount: Int): ByteArray {
    val buffer = ByteArrayOutputStream(byteCount.coerceAtLeast(32))
    val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
    var remaining = byteCount.coerceAtLeast(0)
    while (remaining > 0) {
        val read = input.read(chunk, 0, minOf(chunk.size, remaining))
        if (read <= 0) break
        buffer.write(chunk, 0, read)
        remaining -= read
    }
    return buffer.toByteArray()
}
