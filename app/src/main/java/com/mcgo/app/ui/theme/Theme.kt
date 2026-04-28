package com.mcgo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val McGoColorScheme = lightColorScheme(
    primary = Blue500,
    secondary = Green500,
    tertiary = Violet500,
    background = MistBackground,
    surface = FrostSurface,
    surfaceVariant = SurfaceSoft,
    onPrimary = MistBackground,
    onSecondary = MistBackground,
    onTertiary = MistBackground,
    onBackground = Ink900,
    onSurface = Ink900,
    onSurfaceVariant = Ink600,
)

@Composable
fun McGoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = McGoColorScheme,
        typography = McGoTypography,
        content = content,
    )
}
