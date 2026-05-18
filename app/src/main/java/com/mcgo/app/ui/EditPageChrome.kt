package com.mcgo.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mcgo.app.ui.components.FluidGradientBackground
import com.mcgo.app.ui.model.PaperDifficulty
import com.mcgo.app.ui.model.PaperGameMode
import com.mcgo.app.ui.theme.LocalMcGoVisualTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class EditFullScreenScaffoldLayoutMode {
    PinnedChrome,
    ScrollableChrome,
}

internal data class EditPageColors(
    val backgroundOverlayColor: Color,
    val cardContainerColor: Color,
    val editorContainerColor: Color,
    val iconContainerColor: Color,
    val cardStrokeColor: Color,
    val dividerColor: Color,
    val primaryText: Color,
    val secondaryText: Color,
)

@Composable
internal fun editPageColors(): EditPageColors {
    val visuals = LocalMcGoVisualTokens.current
    val scheme = MaterialTheme.colorScheme
    return EditPageColors(
        backgroundOverlayColor = scheme.background.copy(alpha = 0.52f),
        cardContainerColor = scheme.surface.copy(alpha = 0.92f),
        editorContainerColor = scheme.surfaceVariant.copy(alpha = 0.88f),
        iconContainerColor = scheme.primary.copy(alpha = 0.14f),
        cardStrokeColor = visuals.cardStrokeColor,
        dividerColor = scheme.outline.copy(alpha = 0.24f),
        primaryText = visuals.primaryTextColor,
        secondaryText = visuals.secondaryTextColor,
    )
}

@Composable
internal fun EditFullScreenScaffold(
    title: String,
    subtitle: String,
    leadingIcon: ImageVector,
    dynamicBackground: Boolean,
    layoutMode: EditFullScreenScaffoldLayoutMode = EditFullScreenScaffoldLayoutMode.PinnedChrome,
    onDismiss: () -> Unit,
    footer: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = editPageColors()
    val backgroundSpec = LocalMcGoVisualTokens.current.fluidBackgroundSpec
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = colors.primaryText,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            FluidGradientBackground(
                spec = backgroundSpec,
                animate = dynamicBackground,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.backgroundOverlayColor),
            )
            EditOverlayInteractionBlocker()
            val density = LocalDensity.current
            var headerOverlayHeightPx by remember { mutableIntStateOf(0) }
            var footerOverlayHeightPx by remember { mutableIntStateOf(0) }
            val contentTopPadding = with(density) { headerOverlayHeightPx.toDp() }
            val footerBottomPadding = with(density) { footerOverlayHeightPx.toDp() }
            val headerCard: @Composable (Modifier) -> Unit = { modifier ->
                Surface(
                    modifier = modifier,
                    color = colors.cardContainerColor,
                    contentColor = colors.primaryText,
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(1.dp, colors.cardStrokeColor),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EditSettingsLeadingIcon(icon = leadingIcon)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.primaryText,
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText,
                            )
                        }
                        Surface(
                            color = colors.iconContainerColor,
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "关闭",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
            val headerOverlay: @Composable () -> Unit = {
                headerCard(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .onSizeChanged { headerOverlayHeightPx = it.height },
                )
            }
            val footerCard: @Composable (Modifier) -> Unit = { modifier ->
                Surface(
                    modifier = modifier,
                    color = colors.cardContainerColor,
                    contentColor = colors.primaryText,
                    shape = RoundedCornerShape(26.dp),
                    border = BorderStroke(1.dp, colors.cardStrokeColor),
                ) {
                    Box(modifier = Modifier.padding(14.dp)) {
                        footer()
                    }
                }
            }
            val footerOverlay: @Composable () -> Unit = {
                footerCard(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .onSizeChanged { footerOverlayHeightPx = it.height },
                )
            }

            val headerInline: @Composable () -> Unit = {
                headerCard(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            val footerInline: @Composable () -> Unit = {
                footerCard(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            when (layoutMode) {
                EditFullScreenScaffoldLayoutMode.ScrollableChrome -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        headerInline()
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            content()
                        }
                        footerInline()
                    }
                }

                EditFullScreenScaffoldLayoutMode.PinnedChrome -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding()
                            .padding(top = contentTopPadding, start = 16.dp, end = 16.dp, bottom = footerBottomPadding),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            content()
                        }
                    }
                    headerOverlay()
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .imePadding(),
                    ) {
                        footerOverlay()
                    }
                }
            }
        }
    }
}

internal fun buildServerPropertiesAnnotatedText(
    rawText: String,
    baseColor: Color,
    secondaryColor: Color,
    keyColor: Color,
    separatorColor: Color,
): AnnotatedString {
    val builder = AnnotatedString.Builder(rawText)
    builder.addStyle(
        SpanStyle(
            color = baseColor,
            fontFamily = FontFamily.Monospace,
        ),
        start = 0,
        end = rawText.length,
    )
    rawText.lineSequence().fold(0) { offset, line ->
        val lineEnd = offset + line.length
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("#") -> {
                builder.addStyle(SpanStyle(color = secondaryColor), offset, lineEnd)
            }
            '=' in line -> {
                val separatorIndex = line.indexOf('=')
                if (separatorIndex > 0) {
                    builder.addStyle(SpanStyle(color = keyColor), offset, offset + separatorIndex)
                    builder.addStyle(SpanStyle(color = separatorColor), offset + separatorIndex, offset + separatorIndex + 1)
                    if (separatorIndex + 1 < line.length) {
                        builder.addStyle(SpanStyle(color = baseColor.copy(alpha = 0.92f)), offset + separatorIndex + 1, lineEnd)
                    }
                }
            }
        }
        lineEnd + 1
    }
    return builder.toAnnotatedString()
}

@Composable
private fun EditOverlayInteractionBlocker() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    )
}

@Composable
internal fun EditSettingsInfoCard(
    icon: ImageVector,
    title: String,
    body: String,
) {
    val colors = editPageColors()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.cardContainerColor,
        contentColor = colors.primaryText,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, colors.cardStrokeColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditSettingsLeadingIcon(icon = icon)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.primaryText,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText,
                )
            }
        }
    }
}

@Composable
internal fun EditSettingsSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = editPageColors()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = colors.secondaryText,
        )
        Surface(
            color = colors.cardContainerColor,
            contentColor = colors.primaryText,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, colors.cardStrokeColor),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun EditSettingsLeadingIcon(icon: ImageVector) {
    val colors = editPageColors()
    Surface(
        color = colors.iconContainerColor,
        contentColor = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.padding(10.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun EditSettingRowShell(
    icon: ImageVector,
    label: String,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable () -> Unit,
) {
    val colors = editPageColors()
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EditSettingsLeadingIcon(icon = icon)
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.primaryText,
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd,
        ) {
            trailingContent()
        }
    }
}

@Composable
internal fun EditTextSettingRow(
    icon: ImageVector,
    label: String,
    value: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit,
) {
    val colors = editPageColors()
    val (bringIntoViewRequester, onFocusChanged) = rememberImeBringIntoViewRequester()
    EditSettingRowShell(icon = icon, label = label) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusEvent { onFocusChanged(it.isFocused) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = colors.primaryText,
                textAlign = TextAlign.End,
            ),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (value.isBlank()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryText,
                            textAlign = TextAlign.End,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
internal fun rememberImeBringIntoViewRequester(): Pair<BringIntoViewRequester, (Boolean) -> Unit> {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val onFocusChanged = remember(bringIntoViewRequester, scope) {
        { isFocused: Boolean ->
            if (isFocused) {
                scope.launch {
                    delay(120)
                    bringIntoViewRequester.bringIntoView()
                }
            }
        }
    }
    return bringIntoViewRequester to onFocusChanged
}

@Composable
internal fun <T> EditMenuSettingRow(
    icon: ImageVector,
    label: String,
    valueLabel: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val colors = editPageColors()
    var expanded by remember(label, valueLabel, options) { mutableStateOf(false) }

    EditSettingRowShell(
        icon = icon,
        label = label,
        onClick = { expanded = true },
    ) {
        Box(
            modifier = Modifier.wrapContentWidth(align = Alignment.End),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryText,
                    textAlign = TextAlign.End,
                )
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = colors.secondaryText,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun EditSwitchSettingRow(
    icon: ImageVector,
    label: String,
    supportingText: String? = null,
    supportingTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    EditSettingRowShell(
        icon = icon,
        label = label,
        onClick = { onCheckedChange(!checked) },
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
            supportingText?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = supportingTextColor,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
internal fun EditSettingsDivider() {
    val colors = editPageColors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(colors.dividerColor),
    )
}

internal fun PaperGameMode.displayLabel(): String = when (this) {
    PaperGameMode.Survival -> "生存"
    PaperGameMode.Creative -> "创造"
    PaperGameMode.Adventure -> "冒险"
    PaperGameMode.Spectator -> "旁观"
}

internal fun PaperDifficulty.displayLabel(): String = when (this) {
    PaperDifficulty.Peaceful -> "和平"
    PaperDifficulty.Easy -> "简单"
    PaperDifficulty.Normal -> "普通"
    PaperDifficulty.Hard -> "困难"
}
