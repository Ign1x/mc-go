package com.mcgo.app.ui

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class MCGoModpackSetupScriptContractTest {

    @Test
    fun modpackSetupApprovalDialog_requiresUserEnteredScriptPathAndMentionsVisibleOutput() {
        val source = readSource("app/src/main/java/com/mcgo/app/ui/MCGoApp.kt")
        val dialogSource = source
            .substringAfter("pendingModpackSetupApproval?.let { pendingApproval ->")
            .substringBefore("AnimatedContent(targetState = destination")

        assertThat(dialogSource).contains("OutlinedTextField(")
        assertThat(dialogSource).contains("value = setupScriptInput")
        assertThat(dialogSource).contains("approveManagedServerSetupScript(workspaceAccess.path, selectedScriptRelativePath)")
        assertThat(dialogSource).contains("stdout/stderr")
        assertThat(dialogSource).contains("显示在启动进度中")
        assertThat(dialogSource).doesNotContain("scriptName = pendingSetupScript.fileName.toString()")
        assertThat(dialogSource).doesNotContain("approveManagedServerSetupScript(workspaceAccess.path)")
    }

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)))

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}
