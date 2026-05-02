package com.mcgo.app.ui.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class ConsolePresentationTest {
    @Test
    fun recommendedJavaMajorVersion_supportsModern26SeriesWithJava25() {
        assertThat(recommendedJavaMajorVersion("1.21.11")).isEqualTo(21)
        assertThat(recommendedJavaMajorVersion("26.1")).isEqualTo(25)
        assertThat(recommendedJavaMajorVersion("26.1.2")).isEqualTo(25)
    }

    @Test
    fun buildConsoleAnnotatedLog_colorsTimestampAndLevels() {
        val text = buildConsoleAnnotatedLog(
            "[11:22:33] [Server thread/INFO]: boot\n[11:22:34] [Server thread/WARN]: careful\n[11:22:35] [Server thread/ERROR]: boom",
        )

        assertThat(text.text).contains("[11:22:33]")
        assertThat(text.spanStyles.any { it.item.color == ConsoleTimestampColor }).isTrue()
        assertThat(text.spanStyles.any { it.item.color == ConsoleInfoColor }).isTrue()
        assertThat(text.spanStyles.any { it.item.color == ConsoleWarnColor }).isTrue()
        assertThat(text.spanStyles.any { it.item.color == ConsoleErrorColor }).isTrue()
    }

    @Test
    fun buildConsoleAnnotatedLog_usesMonospaceStyleForWholeConsole() {
        val text = buildConsoleAnnotatedLog("[11:22:33] [INFO] hello")

        assertThat(text.spanStyles.any { it.item.fontFamily == FontFamily.Monospace }).isTrue()
    }

    @Test
    fun normalizeConsoleCommand_appendsSingleTrailingNewline() {
        assertThat(normalizeConsoleCommand("say hello")).isEqualTo("say hello\n")
        assertThat(normalizeConsoleCommand("say hello\n")).isEqualTo("say hello\n")
        assertThat(normalizeConsoleCommand("  list  ")).isEqualTo("list\n")
    }

    @Test
    fun normalizeConsoleCommand_rejectsBlankInput() {
        val error = runCatching { normalizeConsoleCommand("   ") }.exceptionOrNull()
        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }
}
