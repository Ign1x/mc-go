package com.mcgo.app.ui

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class MCGoModpackSetupScriptContractTest {
    private val appSource: String = readSource("app/src/main/java/com/mcgo/app/ui/MCGoApp.kt")
    private val setupDialogSource: String = readSource("app/src/main/java/com/mcgo/app/ui/ModpackSetupApprovalDialog.kt")

    @Test
    fun modpackSetupApprovalDialog_requiresUserEnteredScriptPathAndMentionsVisibleOutput() {
        val dialogCallSite = appSource
            .substringAfter("pendingModpackSetupApproval?.let { pendingApproval ->")
            .substringBefore("AnimatedContent(targetState = destination")

        assertThat(dialogCallSite).contains("ModpackSetupApprovalDialog(")
        assertThat(dialogCallSite).contains("onConfirm = { selectedScriptRelativePath ->")
        assertThat(dialogCallSite).contains("approveManagedServerSetupScript(workspaceAccess.path, selectedScriptRelativePath)")
        assertThat(dialogCallSite).doesNotContain("AlertDialog(")
        assertThat(dialogCallSite).doesNotContain("OutlinedTextField(")
        assertThat(dialogCallSite).doesNotContain("scriptName = pendingSetupScript.fileName.toString()")
        assertThat(dialogCallSite).doesNotContain("approveManagedServerSetupScript(workspaceAccess.path)")

        assertThat(setupDialogSource).contains("internal fun ModpackSetupApprovalDialog(")
        assertThat(setupDialogSource).contains("defaultScriptRelativePath: String")
        assertThat(setupDialogSource).contains("rememberSaveable(")
        assertThat(setupDialogSource).contains("OutlinedTextField(")
        assertThat(setupDialogSource).contains("value = setupScriptInput")
        assertThat(setupDialogSource).contains("scriptCandidates.take(6).forEach { scriptCandidate ->")
        assertThat(setupDialogSource).contains("TextButton(onClick = { setupScriptInput = scriptCandidate })")
        assertThat(setupDialogSource).contains("Text(scriptCandidate)")
        assertThat(setupDialogSource).doesNotContain("candidateScriptSummary")
        assertThat(setupDialogSource).contains("stdout/stderr")
        assertThat(setupDialogSource).contains("显示在启动进度中")
        assertThat(setupDialogSource).contains("onConfirm(setupScriptInput.trim())")
    }

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)))

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}
