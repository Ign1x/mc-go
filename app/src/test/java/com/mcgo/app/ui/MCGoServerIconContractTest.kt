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
    private val iconImageSource: String = String(Files.readAllBytes(projectRoot().resolve("app/src/main/java/com/mcgo/app/ui/ServerIconImage.kt")))

    @Test
    fun editServerFlow_supportsPickingCroppingAndSavingSquareServerIcon() {
        val editDialog = appSource.substringAfter("private fun EditPaperServerDialog(")
            .substringBefore("private enum class EditFullScreenScaffoldLayoutMode")
        val decodeSection = iconImageSource.substringAfter("internal fun decodeServerIconPreviewBitmap(")
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
        assertThat(editDialog).contains("pickButtonLabel = \"更换图标\"")
        assertThat(editDialog).contains("preferSingleRowActions = true")
        assertThat(editDialog).contains("onFailure")
        assertThat(editDialog).contains("服务器图标读取失败")
        assertThat(appSource).doesNotContain("internal fun decodeServerIconPreviewBitmap(")
        assertThat(appSource).doesNotContain("private fun calculateInSampleSize(")
        assertThat(appSource).doesNotContain("data class ImageDecoderTargetSize(")
        assertThat(appSource).doesNotContain("private fun loadManagedServerIcon(")
        assertThat(appSource).doesNotContain("internal fun resolveServerIconCropWindow(")
        assertThat(appSource).doesNotContain("internal fun clampServerIconCropOffset(")
        assertThat(appSource).doesNotContain("internal fun formatServerIconCropZoomLabel(")
        assertThat(appSource).doesNotContain("private fun cropServerIconToSquarePng(")
        assertThat(appSource).contains("cropServerIconToSquarePng(")
        assertThat(appSource).contains("clampServerIconCropOffset(")
        assertThat(iconImageSource).contains("internal fun decodeServerIconPreviewBitmap(")
        assertThat(iconImageSource).contains("private fun calculateInSampleSize(")
        assertThat(iconImageSource).contains("data class ImageDecoderTargetSize(")
        assertThat(iconImageSource).contains("internal fun loadManagedServerIcon(")
        assertThat(iconImageSource).contains("data class ServerIconCropWindow(")
        assertThat(iconImageSource).contains("internal fun resolveServerIconCropWindow(")
        assertThat(iconImageSource).contains("internal fun clampServerIconCropOffset(")
        assertThat(iconImageSource).contains("internal fun formatServerIconCropZoomLabel(")
        assertThat(iconImageSource).contains("internal fun cropServerIconToSquarePng(")
        assertThat(iconImageSource).contains("BitmapFactory.decodeFile(iconFile.toString())")
        assertThat(iconImageSource).contains("ImageDecoder.createSource")
        assertThat(iconImageSource).doesNotContain("readBytes()")
        assertThat(appSource).contains("contentScale = ContentScale.Fit")
        assertThat(appSource).doesNotContain("contentScale = ContentScale.FillBounds")
        assertThat(appSource).contains("layoutMode = EditFullScreenScaffoldLayoutMode.ScrollableChrome")
        assertThat(appSource).contains("Slider(")
        assertThat(appSource).contains("Text(\"缩放\",")
        assertThat(appSource).contains("formatServerIconCropZoomLabel(")
        assertThat(appSource).contains("TextButton(onClick = {\n                                updateCropScale(1f)")
        assertThat(appSource).contains("graphicsLayer(")
        assertThat(appSource).contains("scaleX = cropScale")
        assertThat(appSource).contains("scaleY = cropScale")
        assertThat(appSource).contains("translationX = cropOffset.x")
        assertThat(appSource).contains("translationY = cropOffset.y")
        assertThat(appSource).contains("MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)")
        assertThat(appSource).contains("RoundedCornerShape(28.dp)")
        assertThat(appSource).contains("Icon(Icons.Outlined.Remove")
        assertThat(appSource).contains("Icon(Icons.Outlined.Add")
        assertThat(decodeSection).doesNotContain("readBytes()")
        assertThat(decodeSection).contains("ImageDecoder.createSource")
        assertThat(editorCardSection).contains("showRemoveAction: Boolean = true")
        assertThat(editorCardSection).contains("pickButtonLabel: String = \"选择图标\"")
        assertThat(editorCardSection).contains("preferSingleRowActions: Boolean = false")
        assertThat(editorCardSection).contains("if (preferSingleRowActions)")
        assertThat(editorCardSection).contains("Text(pickButtonLabel)")
        assertThat(editorCardSection).contains("Text(\"移除图标\")")
        assertThat(appSource).contains("fun ServerAvatar(")
        assertThat(appSource).contains("loadManagedServerIcon(context.filesDir.toPath(), server.id)")
        assertThat(appSource).contains("BitmapFactory.decodeByteArray(")
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
