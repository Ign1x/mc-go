package com.mcgo.app.server

import java.io.ByteArrayOutputStream
import java.nio.channels.Channels
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption

data class LogTailResult(
    val nextOffset: Long,
    val line: String?,
)

data class AppendedLinesResult(
    val nextOffset: Long,
    val lines: List<String>,
)

internal const val MaxAppendedLogReadBytes = 1024 * 1024
internal const val MaxAppendedLogLineContinuationBytes = 64 * 1024

internal data class AppendedLogReadWindow(
    val startOffset: Long,
    val bytesToRead: Int,
    val nextOffset: Long,
)

internal data class AppendedLogChunk(
    val nextOffset: Long,
    val lines: List<String>,
)

internal fun appendedLogReadWindow(
    fileSize: Long,
    previousOffset: Long,
    maxBytes: Int = MaxAppendedLogReadBytes,
): AppendedLogReadWindow? {
    if (fileSize <= previousOffset) return null
    val startOffset = previousOffset.coerceAtLeast(0L).coerceAtMost(fileSize)
    val appendedBytes = fileSize - startOffset
    if (appendedBytes <= 0L) return null
    val bytesToRead = minOf(
        appendedBytes,
        maxBytes.coerceAtLeast(1).toLong() + MaxAppendedLogLineContinuationBytes.toLong(),
        Int.MAX_VALUE.toLong(),
    ).toInt()
    return AppendedLogReadWindow(
        startOffset = startOffset,
        bytesToRead = bytesToRead,
        nextOffset = startOffset + bytesToRead,
    )
}

internal fun completeAppendedLogChunk(
    bytes: ByteArray,
    startOffset: Long,
    reachedEnd: Boolean,
    discardLeadingPartialLine: Boolean = false,
): AppendedLogChunk {
    if (bytes.isEmpty()) return AppendedLogChunk(nextOffset = startOffset, lines = emptyList())
    val dataStart = if (discardLeadingPartialLine) {
        val firstNewlineIndex = bytes.indexOf('\n'.code.toByte())
        if (firstNewlineIndex < 0) return AppendedLogChunk(nextOffset = startOffset + bytes.size, lines = emptyList())
        firstNewlineIndex + 1
    } else {
        0
    }
    if (dataStart >= bytes.size) return AppendedLogChunk(nextOffset = startOffset + dataStart, lines = emptyList())
    val completeByteCount = if (reachedEnd) {
        bytes.size
    } else {
        val lastNewlineIndex = bytes.lastIndexOf('\n'.code.toByte())
        if (lastNewlineIndex < dataStart) dataStart else lastNewlineIndex + 1
    }
    if (completeByteCount <= dataStart) {
        val nextOffset = if (reachedEnd) startOffset + dataStart else startOffset + bytes.size
        return AppendedLogChunk(nextOffset = nextOffset, lines = emptyList())
    }
    val lines = bytes.copyOfRange(dataStart, completeByteCount)
        .toString(Charsets.UTF_8)
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toList()
    return AppendedLogChunk(
        nextOffset = startOffset + completeByteCount,
        lines = lines,
    )
}

fun readAppendedNonBlankLines(logFile: Path, previousOffset: Long): List<String> =
    readAppendedNonBlankLinesWithOffset(logFile, previousOffset).lines

fun readAppendedNonBlankLinesWithOffset(logFile: Path, previousOffset: Long): AppendedLinesResult =
    readLogTail(logFile, previousOffset) { chunk ->
        AppendedLinesResult(chunk.nextOffset, chunk.lines)
    }.getOrElse { AppendedLinesResult(previousOffset, emptyList()) }

fun readLastAppendedMatchingLine(logFile: Path, previousOffset: Long, predicate: (String) -> Boolean): LogTailResult =
    readLogTail(logFile, previousOffset) { chunk ->
        LogTailResult(chunk.nextOffset, chunk.lines.lastOrNull(predicate))
    }.getOrElse { LogTailResult(previousOffset, null) }

fun readLastAppendedNonBlankLine(logFile: Path, previousOffset: Long): LogTailResult =
    readLogTail(logFile, previousOffset) { chunk ->
        LogTailResult(chunk.nextOffset, chunk.lines.lastOrNull())
    }.getOrElse { LogTailResult(previousOffset, null) }

private inline fun <T> readLogTail(
    logFile: Path,
    previousOffset: Long,
    buildResult: (AppendedLogChunk) -> T,
): Result<T> = runCatching {
    if (!isReadableLogTailFile(logFile)) return@runCatching buildResult(AppendedLogChunk(previousOffset, emptyList()))
    Files.newByteChannel(logFile, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
        val fileSize = channel.size()
        val window = appendedLogReadWindow(fileSize, previousOffset)
            ?: return@runCatching buildResult(AppendedLogChunk(fileSize, emptyList()))
        val bytes = readLogBytesAt(channel, window.startOffset, window.bytesToRead)
        val chunk = completeAppendedLogChunk(
            bytes = bytes,
            startOffset = window.startOffset,
            reachedEnd = window.nextOffset >= fileSize,
            discardLeadingPartialLine = !startsAtLogLineBoundary(channel, window.startOffset),
        )
        buildResult(chunk)
    }
}

private fun isReadableLogTailFile(path: Path): Boolean =
    Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)

private fun readLogBytesAt(channel: SeekableByteChannel, offset: Long, byteCount: Int): ByteArray {
    channel.position(offset)
    return readUpToByteCount(Channels.newInputStream(channel), byteCount)
}

private fun startsAtLogLineBoundary(channel: SeekableByteChannel, offset: Long): Boolean {
    if (offset <= 0L) return true
    return runCatching {
        channel.position(offset - 1L)
        Channels.newInputStream(channel).read() == '\n'.code
    }.getOrDefault(true)
}

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
