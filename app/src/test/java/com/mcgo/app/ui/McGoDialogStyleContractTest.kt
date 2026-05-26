package com.mcgo.app.ui

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class McGoDialogStyleContractTest {
    private val cardDialogSource: String = readSource("app/src/main/java/com/mcgo/app/ui/components/McGoCardDialog.kt")
    private val createServerDialogSource: String = readSource("app/src/main/java/com/mcgo/app/ui/screens/CreateServerDialog.kt")
    private val serversScreenSource: String = readSource("app/src/main/java/com/mcgo/app/ui/screens/ServersScreen.kt")
    private val tunnelScreenSource: String = readSource("app/src/main/java/com/mcgo/app/ui/screens/TunnelScreen.kt")
    private val modpackDialogSource: String = readSource("app/src/main/java/com/mcgo/app/ui/ModpackSetupApprovalDialog.kt")
    private val consoleDialogSource: String = readSource("app/src/main/java/com/mcgo/app/ui/ServerConsoleDialog.kt")

    @Test
    fun sharedDialogWindow_usesGlassCardVisualTokens() {
        assertThat(cardDialogSource).contains("fun McGoCardDialog(")
        assertThat(cardDialogSource).contains("Dialog(")
        assertThat(cardDialogSource).contains("LocalMcGoVisualTokens.current")
        assertThat(cardDialogSource).contains("val dialogContainerColor = frostedDialogContainerColor(visuals.cardContainerColor)")
        assertThat(cardDialogSource).contains("color = dialogContainerColor")
        assertThat(cardDialogSource).contains("Box(modifier = Modifier.frostedDialogBackdrop())")
        assertThat(cardDialogSource).contains("contentColor = visuals.cardContentColor")
        assertThat(cardDialogSource).contains("border = BorderStroke(1.dp, visuals.cardStrokeColor)")
        assertThat(cardDialogSource).contains("shape = RoundedCornerShape(28.dp)")
        assertThat(cardDialogSource).contains("widthIn(max = 560.dp)")
        assertThat(cardDialogSource).contains("heightIn(max = configuration.screenHeightDp.dp * 0.92f)")
        assertThat(cardDialogSource).contains("tonalElevation = 0.dp")
        assertThat(cardDialogSource).contains("shadowElevation = 0.dp")
        assertThat(cardDialogSource).contains("private fun frostedDialogContainerColor(base: Color): Color =")
        assertThat(cardDialogSource).contains("base.copy(alpha = base.alpha.coerceAtLeast(0.90f))")
        assertThat(cardDialogSource).contains("private fun Modifier.frostedDialogBackdrop(): Modifier = drawWithContent")
        assertThat(cardDialogSource).contains("drawRoundRect(")
        assertThat(cardDialogSource).contains("Brush.verticalGradient")
        assertThat(cardDialogSource).contains("EnableDialogWindowFrostedBlur()")
        assertThat(cardDialogSource).contains("LocalView.current")
        assertThat(cardDialogSource).contains("DialogWindowProvider")
        assertThat(cardDialogSource).contains("Build.VERSION.SDK_INT >= Build.VERSION_CODES.S")
        assertThat(cardDialogSource).contains("WindowManager.LayoutParams.FLAG_BLUR_BEHIND")
        assertThat(cardDialogSource).contains("blurBehindRadius = 24")
        assertThat(cardDialogSource).contains("previousWindowFlags")
        assertThat(cardDialogSource).doesNotContain("setBackgroundBlurRadius")
        val frostedBackdropBlock = cardDialogSource.substringAfter("private fun Modifier.frostedDialogBackdrop(): Modifier = drawWithContent")
        assertThat(frostedBackdropBlock.indexOf("drawRoundRect(")).isLessThan(frostedBackdropBlock.indexOf("drawContent()"))
    }

    @Test
    fun dialogWindows_useSharedCardDialogInsteadOfRawMaterialAlertDialog() {
        val dialogSources = listOf(
            createServerDialogSource,
            serversScreenSource,
            tunnelScreenSource,
            modpackDialogSource,
            consoleDialogSource,
        )
        dialogSources.forEach { source ->
            assertThat(source).doesNotContain("AlertDialog(")
            assertThat(source).doesNotContain("import androidx.compose.material3.AlertDialog")
        }

        assertThat(createServerDialogSource).contains("McGoCardDialog(")
        assertThat(serversScreenSource).contains("McGoCardDialog(")
        assertThat(tunnelScreenSource).contains("McGoCardDialog(")
        assertThat(modpackDialogSource).contains("McGoCardDialog(")
        assertThat(consoleDialogSource).contains("McGoCardDialog(")
    }

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)))

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}
