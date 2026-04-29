package com.mcgo.app.ui.model

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class JavaManagementModelsTest {

    @Test
    fun defaultJavaManagementState_listsAppManagedJreVersionsForMinecraftWithoutPermissionItems() {
        val state = defaultJavaManagementState()

        assertThat(state.summaryLabel).contains("JRE")
        assertThat(state.sectionTitle).isEqualTo("托管 JRE")
        assertThat(state.runtimeOptions.map { it.majorVersion }).containsExactly(8, 11, 17, 21, 25).inOrder()
        assertThat(state.runtimeOptions.map { it.title }).containsExactly(
            "Java 8",
            "Java 11",
            "Java 17",
            "Java 21",
            "Java 25",
        ).inOrder()
        assertThat(state.runtimeOptions.all { it.managedByApp }).isTrue()
        assertThat(state.runtimeOptions.joinToString("\n") { it.description }).contains("Minecraft")
        assertThat(state.runtimeOptions.joinToString("\n") { it.description }).doesNotContain("系统 PATH")
    }

    @Test
    fun javaManagementCopyDoesNotExposeRuntimeStrategyOrAndroidPermissionRows() {
        val state = defaultJavaManagementState()
        val visibleText = buildString {
            appendLine(state.sectionTitle)
            appendLine(state.summaryLabel)
            state.runtimeOptions.forEach { option ->
                appendLine(option.title)
                appendLine(option.description)
                appendLine(option.statusLabel)
            }
        }

        assertThat(visibleText).doesNotContain("Runtime 策略")
        assertThat(visibleText).doesNotContain("POST_NOTIFICATIONS")
        assertThat(visibleText).doesNotContain("WAKE_LOCK")
        assertThat(visibleText).doesNotContain("FOREGROUND_SERVICE")
    }
}
