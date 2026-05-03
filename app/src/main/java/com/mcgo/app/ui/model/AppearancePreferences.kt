package com.mcgo.app.ui.model

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import kotlin.math.roundToInt

enum class ThemeModePreference(val label: String) {
    FollowSystem("跟随系统"),
    Light("浅色"),
    Dark("深色");

    fun resolvesToDark(systemIsDark: Boolean): Boolean = when (this) {
        Light -> false
        FollowSystem -> systemIsDark
        Dark -> true
    }

    fun next(): ThemeModePreference = when (this) {
        FollowSystem -> Light
        Light -> Dark
        Dark -> FollowSystem
    }

    companion object {
        fun fromLabel(label: String): ThemeModePreference = entries.firstOrNull { it.label == label } ?: FollowSystem
    }
}

enum class AccentPreset(
    val label: String,
    val primaryHex: Long,
    val secondaryHex: Long,
    val tertiaryHex: Long,
) {
    Ocean(
        label = "科技蓝",
        primaryHex = 0xFF437BFF,
        secondaryHex = 0xFF18C28E,
        tertiaryHex = 0xFF7F68FF,
    ),
    Forest(
        label = "森林绿",
        primaryHex = 0xFF18C28E,
        secondaryHex = 0xFF437BFF,
        tertiaryHex = 0xFF7F68FF,
    ),
    Amethyst(
        label = "紫晶",
        primaryHex = 0xFF7F68FF,
        secondaryHex = 0xFF437BFF,
        tertiaryHex = 0xFF18C28E,
    ),
    Sunset(
        label = "暖阳橙",
        primaryHex = 0xFFF2B649,
        secondaryHex = 0xFFE56060,
        tertiaryHex = 0xFF437BFF,
    ),
    System(
        label = "系统颜色",
        primaryHex = 0xFF4F6BED,
        secondaryHex = 0xFF5E8E3E,
        tertiaryHex = 0xFF8E5CF6,
    );

    companion object {
        fun fromLabel(label: String): AccentPreset = entries.firstOrNull { it.label == label } ?: Forest
    }
}

enum class FontScalePreference(val label: String, val multiplier: Float) {
    Compact("紧凑", 0.92f),
    Standard("标准", 1.0f),
    Wide("宽松", 1.10f);

    companion object {
        fun fromLabel(label: String): FontScalePreference = entries.firstOrNull { it.label == label } ?: Compact
    }
}

data class AppearancePreferences(
    val themeMode: ThemeModePreference = ThemeModePreference.FollowSystem,
    val accentPreset: AccentPreset = AccentPreset.Forest,
    val fontScale: FontScalePreference = FontScalePreference.Compact,
    val cardTransparencyPercent: Int = 82,
    val transparentCards: Boolean = true,
    val dynamicBackground: Boolean = true,
) {
    fun effectiveTypographyScale(): Float = FontScalePreference.Compact.multiplier

    fun cardContainerAlpha(): Float = if (transparentCards) {
        cardTransparencyPercent.coerceIn(0, 100) / 100f
    } else {
        1f
    }

    fun backgroundAuraAlpha(): Float = if (dynamicBackground) 0.24f else 0f

    fun backgroundMotionScale(): Float = if (dynamicBackground) 1.18f else 1f

    fun summaryLabel(): String = "${themeMode.label} · ${accentPreset.label}"
}

val AppearancePreferencesSaver: Saver<AppearancePreferences, Any> = listSaver(
    save = {
        listOf(
            it.themeMode.name,
            it.accentPreset.name,
            it.fontScale.name,
            it.cardTransparencyPercent,
            it.transparentCards,
            it.dynamicBackground,
        )
    },
    restore = { values -> restoreAppearancePreferences(values) },
)

fun restoreAppearancePreferences(values: List<Any?>): AppearancePreferences {
    val isLegacyPayload = values.size >= 8 && values.getOrNull(3) is String
    val themeMode = ThemeModePreference.valueOf(values[0] as String)
    val accentPreset = AccentPreset.valueOf(values[1] as String)
    val legacyFontScale = when (values[2] as String) {
        "Comfortable" -> FontScalePreference.Wide.name
        else -> values[2] as String
    }
    val cardTransparencyPercent = if (isLegacyPayload) values[4] as Int else values[3] as Int
    val transparentCards = if (isLegacyPayload) values[5] as Boolean else values[4] as Boolean
    val dynamicBackground = if (isLegacyPayload) values[6] as Boolean else values[5] as Boolean
    val legacyCompactTypography = if (isLegacyPayload) values[7] as Boolean else null
    val fontScale = when {
        legacyCompactTypography == true -> FontScalePreference.Compact
        else -> FontScalePreference.valueOf(legacyFontScale)
    }
    return AppearancePreferences(
        themeMode = themeMode,
        accentPreset = accentPreset,
        fontScale = fontScale,
        cardTransparencyPercent = cardTransparencyPercent,
        transparentCards = transparentCards,
        dynamicBackground = dynamicBackground,
    )
}

fun usedMemoryPercent(usedBytes: Long, totalBytes: Long): Int {
    val safeTotalBytes = totalBytes.coerceAtLeast(1L)
    val safeUsedBytes = usedBytes.coerceIn(0L, safeTotalBytes)
    return ((safeUsedBytes * 100f) / safeTotalBytes).roundToInt().coerceIn(0, 100)
}
