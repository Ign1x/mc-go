package com.mcgo.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private val BaseMcGoTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = 28.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Medium,
    ),
)

fun mcGoTypography(scale: Float): Typography {
    val safeScale = scale.coerceIn(0.88f, 1.16f)
    return Typography(
        headlineSmall = BaseMcGoTypography.headlineSmall.scaledBy(safeScale),
        titleLarge = BaseMcGoTypography.titleLarge.scaledBy(safeScale),
        titleMedium = BaseMcGoTypography.titleMedium.scaledBy(safeScale),
        titleSmall = BaseMcGoTypography.titleSmall.scaledBy(safeScale),
        bodyLarge = BaseMcGoTypography.bodyLarge.scaledBy(safeScale),
        bodyMedium = BaseMcGoTypography.bodyMedium.scaledBy(safeScale),
        bodySmall = BaseMcGoTypography.bodySmall.scaledBy(safeScale),
        labelLarge = BaseMcGoTypography.labelLarge.scaledBy(safeScale),
        labelMedium = BaseMcGoTypography.labelMedium.scaledBy(safeScale),
        labelSmall = BaseMcGoTypography.labelSmall.scaledBy(safeScale),
    )
}

private fun TextStyle.scaledBy(scale: Float): TextStyle = copy(
    fontSize = fontSize.scaledBy(scale),
    lineHeight = lineHeight.scaledBy(scale),
)

private fun TextUnit.scaledBy(scale: Float): TextUnit = (value * scale).sp
