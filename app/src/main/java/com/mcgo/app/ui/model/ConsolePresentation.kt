package com.mcgo.app.ui.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily

val ConsoleTimestampColor: Color = Color(0xFF8B949E)
val ConsoleInfoColor: Color = Color(0xFF4FD1C5)
val ConsoleWarnColor: Color = Color(0xFFF6C453)
val ConsoleErrorColor: Color = Color(0xFFFF6B6B)
private val ConsoleBaseTextColor: Color = Color(0xFFE6EDF3)

private val ConsoleTimestampRegex = Regex("\\[\\d{2}:\\d{2}:\\d{2}]")
private val ConsoleLevelRegex = Regex("\\[(?:[^\\]]*/)?(INFO|WARN|ERROR)]")

fun buildConsoleAnnotatedLog(rawText: String): AnnotatedString {
    val text = rawText.ifBlank { "暂无运行日志，启动服务器后这里会显示最新控制台输出。" }
    val builder = AnnotatedString.Builder(text)
    builder.addStyle(
        SpanStyle(
            color = ConsoleBaseTextColor,
            fontFamily = FontFamily.Monospace,
        ),
        start = 0,
        end = text.length,
    )
    ConsoleTimestampRegex.findAll(text).forEach { match ->
        builder.addStyle(
            SpanStyle(color = ConsoleTimestampColor),
            start = match.range.first,
            end = match.range.last + 1,
        )
    }
    ConsoleLevelRegex.findAll(text).forEach { match ->
        val color = when (match.groupValues[1]) {
            "INFO" -> ConsoleInfoColor
            "WARN" -> ConsoleWarnColor
            "ERROR" -> ConsoleErrorColor
            else -> ConsoleBaseTextColor
        }
        builder.addStyle(
            SpanStyle(color = color),
            start = match.range.first,
            end = match.range.last + 1,
        )
    }
    return builder.toAnnotatedString()
}

fun normalizeConsoleCommand(command: String): String {
    val trimmed = command.trim()
    require(trimmed.isNotBlank()) { "控制台指令不能为空" }
    return "$trimmed\n"
}
