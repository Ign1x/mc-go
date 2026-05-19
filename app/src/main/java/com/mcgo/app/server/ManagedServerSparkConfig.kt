package com.mcgo.app.server

import com.mcgo.app.ui.model.ServerCardState
import java.nio.file.Files
import java.nio.file.Path

internal fun prepareAndroidCompatibleSparkConfig(workDir: Path, server: ServerCardState) {
    if (
        server.serverType == com.mcgo.app.ui.model.MinecraftServerType.Vanilla ||
        server.serverType == com.mcgo.app.ui.model.MinecraftServerType.Fabric
    ) return
    val sparkConfigPath = workDir.resolve("plugins/spark/config.json")
    Files.createDirectories(sparkConfigPath.parent)
    val existingConfig = sparkConfigPath
        .takeIf { Files.isRegularFile(it) }
        ?.let { String(Files.readAllBytes(it)) }
    Files.write(
        sparkConfigPath,
        mergeAndroidCompatibleSparkConfig(existingConfig).toByteArray(),
    )
}

private fun mergeAndroidCompatibleSparkConfig(existingConfig: String?): String {
    val normalized = existingConfig
        ?.takeIf { it.contains('{') && it.contains('}') }
        ?.trim()
        ?.ifBlank { null }
        ?: "{}"
    return normalized
        .let { upsertTopLevelJsonScalarProperty(it, "backgroundProfiler", "false") }
        .let { upsertTopLevelJsonScalarProperty(it, "backgroundProfilerEngine", "\"java\"") }
        .trimEnd()
        .plus("\n")
}

private fun upsertTopLevelJsonScalarProperty(json: String, key: String, rawValue: String): String {
    findTopLevelJsonValueRange(json, key)?.let { valueRange ->
        return json.replaceRange(valueRange.first, valueRange.last + 1, rawValue)
    }
    val closeBraceIndex = json.lastIndexOf('}')
    if (closeBraceIndex < 0) {
        return "{\n  \"$key\": $rawValue\n}"
    }
    val hasEntries = json.substring(0, closeBraceIndex).any { it != '{' && !it.isWhitespace() }
    val insertion = if (hasEntries) {
        ",\n  \"$key\": $rawValue\n"
    } else {
        "\n  \"$key\": $rawValue\n"
    }
    return json.replaceRange(closeBraceIndex, closeBraceIndex, insertion)
}

private fun findTopLevelJsonValueRange(json: String, key: String): IntRange? {
    var depth = 0
    var index = 0
    while (index < json.length) {
        when (val current = json[index]) {
            '{', '[' -> {
                depth += 1
                index += 1
            }
            '}', ']' -> {
                depth = (depth - 1).coerceAtLeast(0)
                index += 1
            }
            '\"' -> {
                val stringEnd = json.findJsonStringEnd(index)
                if (depth == 1) {
                    val token = json.substring(index + 1, stringEnd)
                    val afterKey = json.indexOfFirstNonWhitespace(stringEnd + 1)
                    if (token == key && afterKey in json.indices && json[afterKey] == ':') {
                        val valueStart = json.indexOfFirstNonWhitespace(afterKey + 1)
                        if (valueStart in json.indices) {
                            val valueEndExclusive = json.findJsonValueEnd(valueStart)
                            return valueStart until valueEndExclusive
                        }
                    }
                }
                index = stringEnd + 1
            }
            else -> index += 1
        }
    }
    return null
}

private fun String.indexOfFirstNonWhitespace(startIndex: Int): Int {
    for (index in startIndex until length) {
        if (!this[index].isWhitespace()) return index
    }
    return -1
}

private fun String.findJsonStringEnd(startIndex: Int): Int {
    var index = startIndex + 1
    var escaped = false
    while (index < length) {
        val current = this[index]
        if (escaped) {
            escaped = false
        } else if (current == '\\') {
            escaped = true
        } else if (current == '\"') {
            return index
        }
        index += 1
    }
    return lastIndex.coerceAtLeast(startIndex)
}

private fun String.findJsonValueEnd(startIndex: Int): Int {
    if (startIndex !in indices) return startIndex
    return when (this[startIndex]) {
        '\"' -> findJsonStringEnd(startIndex) + 1
        '{', '[' -> {
            var index = startIndex
            var depth = 0
            while (index < length) {
                when (this[index]) {
                    '\"' -> index = findJsonStringEnd(index)
                    '{', '[' -> depth += 1
                    '}', ']' -> {
                        depth -= 1
                        if (depth == 0) return index + 1
                    }
                }
                index += 1
            }
            length
        }
        else -> {
            var index = startIndex
            while (index < length && this[index] != ',' && this[index] != '}') {
                index += 1
            }
            index.coerceAtLeast(startIndex)
        }
    }
}
