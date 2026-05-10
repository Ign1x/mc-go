package com.mcgo.app.ui

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class MCGoServerIconContractTest {
    private val appSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/MCGoApp.kt")))
    private val modelSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/model/McGoUiModels.kt")))
    private val storeSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/storage/ServerProfileStore.kt")))
    private val runtimeSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/server/PaperServerRuntime.kt")))
    private val serversScreenSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/screens/ServersScreen.kt")))

    @Test
    fun editServerFlow_supportsPickingCroppingAndSavingSquareServerIcon() {
        val editDialog = appSource.substringAfter("private fun EditPaperServerDialog(")
            .substringBefore("private enum class EditFullScreenScaffoldLayoutMode")
        val decodeSection = appSource.substringAfter("internal fun decodeServerIconPreviewBitmap(")
            .substringBefore("private fun calculateInSampleSize(")
        val editorCardSection = appSource.substringAfter("internal fun ServerIconEditorCard(")
            .substringBefore("@Composable\nfun ServerAvatar(")

        assertThat(editDialog).contains("rememberLauncherForActivityResult(")
        assertThat(editDialog).contains("ActivityResultContracts.PickVisualMedia()")
        assertThat(editDialog).contains("PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)")
        assertThat(editDialog).contains("ServerIconCropDialog(")
        assertThat(editDialog).contains("writeManagedServerIcon(")
        assertThat(editDialog).contains("syncManagedServerIconToAuthorizedDirectory(")
        assertThat(editDialog).contains("deleteManagedServerIconFromAuthorizedDirectory(")
        assertThat(editDialog).contains("showRemoveAction = true")
        assertThat(editDialog).contains("preferSingleRowActions = true")
        assertThat(editDialog).contains("onFailure")
        assertThat(editDialog).contains("服务器图标读取失败")
        assertThat(appSource).contains("cropServerIconToSquarePng(")
        assertThat(appSource).contains("clampServerIconCropOffset(")
        assertThat(appSource).contains("resolveServerIconCropWindow(")
        assertThat(appSource).contains("contentScale = ContentScale.Fit")
        assertThat(appSource).doesNotContain("contentScale = ContentScale.FillBounds")
        assertThat(appSource).contains("layoutMode = EditFullScreenScaffoldLayoutMode.ScrollableChrome")
        assertThat(decodeSection).doesNotContain("readBytes()")
        assertThat(decodeSection).contains("ImageDecoder.createSource")
        assertThat(editorCardSection).contains("showRemoveAction: Boolean = true")
        assertThat(editorCardSection).contains("pickButtonLabel: String = \"更换图标\"")
        assertThat(editorCardSection).contains("preferSingleRowActions: Boolean = false")
        assertThat(editorCardSection).contains("if (preferSingleRowActions)")
        assertThat(editorCardSection).contains("Text(pickButtonLabel)")
        assertThat(editorCardSection).contains("Text(\"移除图标\")")
    }

    @Test
    fun serverModelStoreRuntimeAndListCard_preserveAndRenderCustomServerIcon() {
        assertThat(modelSource).contains("serverIconVersion: Long = 0L")
        assertThat(storeSource).contains("serverIconVersion")
        assertThat(runtimeSource).contains("writeManagedServerIcon(")
        assertThat(runtimeSource).contains("managedPaperServerIconFile(")
        assertThat(runtimeSource).contains("server-icon.png")
        assertThat(appSource).contains("fun ServerAvatar(")
        assertThat(appSource).contains("restoreManagedServerIconFromAuthorizedDirectory(")
        assertThat(appSource).contains("targetIconPath = managedPaperServerIconFile(appContext.filesDir.toPath(), server.id)")
        assertThat(serversScreenSource).contains("ServerAvatar(")
        assertThat(serversScreenSource).doesNotContain("imageVector = Icons.Outlined.Terminal")
    }

    private fun projectRoot(): Path =
        generateSequence(Path.of(".").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("app/build.gradle.kts")) }
            ?: error("project root not found")
}
