package com.mcgo.app.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.mcgo.app.server.appendMcGoAppDebugLog
import com.mcgo.app.server.buildManagedServerDebugLogLine
import com.mcgo.app.server.mcGoAppDebugLogFile
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal const val RuntimePrefsName = "mcgo_runtime_permissions"
internal const val ServerDirectoryUriKey = "server_directory_uri"

internal fun exportDebugLogs(context: Context): Intent {
    val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
    val exportDir = context.cacheDir.toPath().resolve("mcgo_debug_logs")
    Files.createDirectories(exportDir)
    val exportFile = exportDir.resolve("mcgo_debug_logs-$timestamp.txt")
    val filesDir = context.filesDir.toPath()
    appendMcGoAppDebugLog(
        filesDir = filesDir,
        message = "导出调试日志",
        details = mapOf("versionName" to com.mcgo.app.BuildConfig.VERSION_NAME, "versionCode" to com.mcgo.app.BuildConfig.VERSION_CODE),
    )
    val sections = buildList {
        add("== mcgo debug export ==")
        add("generatedAt=$timestamp")
        add("")
        add(
            """
            |===== export_metadata =====
            |versionName=${com.mcgo.app.BuildConfig.VERSION_NAME}
            |versionCode=${com.mcgo.app.BuildConfig.VERSION_CODE}
            |deviceApi=${Build.VERSION.SDK_INT}
            |deviceRelease=${Build.VERSION.RELEASE}
            |supportedAbis=${Build.SUPPORTED_ABIS.joinToString(",")}
            |${buildManagedServerDebugLogLine("[debug] 行会写入托管运行日志，用于记录启动、导入、脚本执行与退出阶段")}
            |
            """.trimMargin(),
        )
        add(readLogExportSection("logs/mcgo-debug.log", mcGoAppDebugLogFile(filesDir)))
        add(readLogExportSection("server_profiles.properties", filesDir.resolve("server_profiles.properties")))
        add(readLogExportSection("tunnel_profiles.properties", filesDir.resolve("tunnel_profiles.properties")))
        add(readLogExportSection("appearance_preferences.properties", filesDir.resolve("appearance_preferences.properties")))
        add(readRuntimePrefsExportSection(context))
        val serversRoot = filesDir.resolve("servers")
        if (Files.isDirectory(serversRoot)) {
            Files.walk(serversRoot).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".log") }
                    .sorted()
                    .forEach { logPath -> add(readLogExportSection(filesDir.relativize(logPath).toString(), logPath)) }
            }
        }
    }
    Files.write(exportFile, sections.joinToString(separator = "\n").toByteArray(Charsets.UTF_8))
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exportFile.toFile())
    return Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "MC-GO 调试日志 $timestamp")
            putExtra(Intent.EXTRA_TEXT, "MC-GO 调试日志，问题反馈时建议附上此文件。")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, exportFile.fileName.toString(), uri)
        },
        "分享 MC-GO 调试日志",
    ).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private fun readLogExportSection(title: String, path: Path): String {
    val body = if (Files.isRegularFile(path)) {
        runCatching { redactSensitiveLogExportText(String(Files.readAllBytes(path), Charsets.UTF_8)) }
            .getOrElse { "<read failed: ${it.message}>" }
    } else {
        "<missing>"
    }
    return """
        |===== $title =====
        |$body
        |
    """.trimMargin()
}

private fun readRuntimePrefsExportSection(context: Context): String {
    val prefs = context.getSharedPreferences(RuntimePrefsName, Context.MODE_PRIVATE)
    val entries = prefs.all.entries.sortedBy { it.key }
    val body = if (entries.isEmpty()) {
        "<empty>"
    } else {
        entries.joinToString(separator = "\n") { (key, value) ->
            val renderedValue = if (key == ServerDirectoryUriKey) "<redacted>" else value.toString()
            "$key=$renderedValue"
        }
    }
    return """
        |===== runtime_prefs =====
        |$body
        |
    """.trimMargin()
}

internal fun redactSensitiveLogExportText(rawText: String): String = rawText
    .lineSequence()
    .map(::redactSensitiveLogExportLine)
    .joinToString(separator = "\n")

private val SensitiveLogExportKeySuffixes = setOf(
    "credentialvalue",
    "rawconfigtext",
    "rawconfigpreview",
)

private val SensitiveLogExportExactKeys = setOf(
    "auth.token",
    "token",
    "vkey",
    "secret_key",
    "rcon.password",
    "management-server-secret",
)

private val SensitiveLogExportSuffixPattern = SensitiveLogExportKeySuffixes.joinToString("|") { Regex.escape(it) }
private val SensitiveLogExportExactKeyPattern = SensitiveLogExportExactKeys.joinToString("|") { Regex.escape(it) }
private val SensitiveLogExportWholeLinePattern = Regex(
    "^\\s*([A-Za-z0-9_.-]*(?:$SensitiveLogExportSuffixPattern))\\s*[:=].*$",
    RegexOption.IGNORE_CASE,
)
private val SensitiveLogExportAssignmentPattern = Regex(
    "(^|[\\s|])([A-Za-z0-9_.-]*(?:$SensitiveLogExportSuffixPattern)|$SensitiveLogExportExactKeyPattern)\\s*[:=]\\s*.*?(?=(\\s+[A-Za-z0-9_.-]+\\s*[:=])|\\s+\\||$)",
    RegexOption.IGNORE_CASE,
)

private fun redactSensitiveLogExportLine(line: String): String {
    SensitiveLogExportWholeLinePattern.matchEntire(line)?.let { match ->
        return "${match.groupValues[1].trim()}=<redacted>"
    }
    return SensitiveLogExportAssignmentPattern.replace(line) { match ->
        val prefix = match.groupValues[1]
        val key = match.groupValues[2].trim()
        "$prefix$key=<redacted>"
    }
}
