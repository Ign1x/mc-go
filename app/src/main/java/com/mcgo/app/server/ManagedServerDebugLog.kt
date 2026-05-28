package com.mcgo.app.server

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val MaxDebugLogPreviewBytesPerFile = 256 * 1024
private val ManagedServerDebugTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

internal fun buildManagedServerDebugLogLine(
    message: String,
    details: Map<String, Any?> = emptyMap(),
    timestamp: LocalDateTime = LocalDateTime.now(),
): String {
    val normalizedDetails = details.entries
        .asSequence()
        .filter { (_, value) -> value != null }
        .joinToString(separator = " ") { (key, value) -> "${key.toStructuredDebugLogPart()}=${value!!.toStructuredDebugLogPart()}" }
        .trim()
    val prefix = "[debug] ${ManagedServerDebugTimestampFormatter.format(timestamp)} ${message.toStructuredDebugLogPart()}"
    return if (normalizedDetails.isBlank()) prefix else "$prefix | $normalizedDetails"
}

private fun Any.toStructuredDebugLogPart(): String = toString()
    .replace(Regex("\\s+"), " ")
    .replace('|', '¦')
    .replace('=', ':')
    .trim()

internal fun appendManagedServerDebugLog(
    logFile: Path,
    message: String,
    details: Map<String, Any?> = emptyMap(),
    timestamp: LocalDateTime = LocalDateTime.now(),
    onLogLine: ((String) -> Unit)? = null,
): String {
    val line = buildManagedServerDebugLogLine(message = message, details = details, timestamp = timestamp)
    appendDebugLogLine(logFile, line)
    onLogLine?.invoke(line)
    return line
}

internal fun mcGoAppDebugLogFile(filesDir: Path): Path = filesDir.resolve("logs/mcgo-debug.log")

internal fun appendMcGoAppDebugLog(
    filesDir: Path,
    message: String,
    details: Map<String, Any?> = emptyMap(),
    timestamp: LocalDateTime = LocalDateTime.now(),
): String = appendManagedServerDebugLog(
    logFile = mcGoAppDebugLogFile(filesDir),
    message = message,
    details = details,
    timestamp = timestamp,
)

internal fun readRecentDebugLogPreview(
    filesDir: Path,
    maxLinesPerFile: Int = 80,
): String {
    val logFiles = buildList {
        val appLog = mcGoAppDebugLogFile(filesDir)
        if (isReadableDebugLogFile(appLog)) add(appLog)
        val serversRoot = filesDir.resolve("servers")
        if (Files.isDirectory(serversRoot, LinkOption.NOFOLLOW_LINKS)) {
            Files.walk(serversRoot).use { paths ->
                paths
                    .filter { isReadableDebugLogFile(it) && it.fileName.toString() == "mcgo-latest.log" }
                    .sorted()
                    .forEach { add(it) }
            }
        }
    }
    return logFiles.joinToString(separator = "\n\n") { path ->
        val title = filesDir.toAbsolutePath().normalize()
            .relativize(path.toAbsolutePath().normalize())
            .toString()
            .replace('\\', '/')
        val tail = runCatching { readLastDebugLogLines(path, maxLinesPerFile.coerceAtLeast(1)) }
            .getOrElse { listOf("<read failed: ${it.message}>") }
            .joinToString(separator = "\n")
        "===== $title =====\n$tail"
    }
}

private fun readLastDebugLogLines(path: Path, maxLines: Int): List<String> {
    val lineLimit = maxLines.coerceAtLeast(1)
    Files.newByteChannel(path).use { channel ->
        val fileSize = channel.size()
        if (fileSize == 0L) return emptyList()

        val chunkSize = 8192
        val buffer = ByteBuffer.allocate(chunkSize)
        val bytes = ArrayDeque<Byte>()
        var newlineCount = 0
        var position = fileSize

        while (position > 0 && newlineCount <= lineLimit) {
            val readSize = minOf(chunkSize.toLong(), position).toInt()
            position -= readSize
            buffer.clear()
            buffer.limit(readSize)
            channel.position(position)
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) <= 0) break
            }
            val chunk = buffer.array()
            for (index in buffer.position() - 1 downTo 0) {
                val byte = chunk[index]
                bytes.addFirst(byte)
                if (bytes.size > MaxDebugLogPreviewBytesPerFile) {
                    bytes.removeFirst()
                }
                if (byte == '\n'.code.toByte()) {
                    newlineCount += 1
                    if (newlineCount > lineLimit) break
                }
            }
        }

        val text = String(bytes.toByteArray(), StandardCharsets.UTF_8).trimEnd('\r', '\n')
        if (text.isEmpty()) return emptyList()
        val rawTailLines = text.lines()
            .filterIndexed { index, _ -> index != 0 || newlineCount <= lineLimit }
            .takeLast(lineLimit)
            .toList()
        return summarizeMinecraftClassListingLogLines(rawTailLines)
    }
}

private fun isReadableDebugLogFile(path: Path): Boolean =
    Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)

private fun appendDebugLogLine(logFile: Path, line: String) {
    Files.createDirectories(logFile.parent)
    Files.write(
        logFile,
        "$line\n".toByteArray(StandardCharsets.UTF_8),
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND,
    )
}
