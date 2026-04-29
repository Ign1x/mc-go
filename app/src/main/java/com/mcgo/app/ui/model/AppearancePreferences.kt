package com.mcgo.app.ui.model

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import kotlin.math.roundToInt

enum class ThemeModePreference(val label: String) {
    Light("浅色"),
    FollowSystem("跟随系统"),
    Dark("深色");

    fun resolvesToDark(systemIsDark: Boolean): Boolean = when (this) {
        Light -> false
        FollowSystem -> systemIsDark
        Dark -> true
    }

    companion object {
        fun fromLabel(label: String): ThemeModePreference = entries.firstOrNull { it.label == label } ?: Light
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
    );

    companion object {
        fun fromLabel(label: String): AccentPreset = entries.firstOrNull { it.label == label } ?: Forest
    }
}

enum class FontScalePreference(val label: String, val multiplier: Float) {
    Compact("紧凑", 0.92f),
    Standard("标准", 1.0f),
    Comfortable("舒适", 1.08f);

    companion object {
        fun fromLabel(label: String): FontScalePreference = entries.firstOrNull { it.label == label } ?: Compact
    }
}

enum class MotionPreference(val label: String, val auraAlpha: Float, val pulseScale: Float, val animationMillis: Int) {
    Reduced("省电", 0.05f, 1.0f, 0),
    Standard("标准", 0.11f, 1.08f, 2800),
    Expressive("灵动", 0.18f, 1.16f, 1800);

    companion object {
        fun fromLabel(label: String): MotionPreference = entries.firstOrNull { it.label == label } ?: Standard
    }
}

data class AppearancePreferences(
    val themeMode: ThemeModePreference = ThemeModePreference.Light,
    val accentPreset: AccentPreset = AccentPreset.Forest,
    val fontScale: FontScalePreference = FontScalePreference.Compact,
    val motionPreference: MotionPreference = MotionPreference.Standard,
    val cardTransparencyPercent: Int = 82,
    val transparentCards: Boolean = true,
    val dynamicBackground: Boolean = true,
    val compactTypography: Boolean = true,
) {
    fun effectiveTypographyScale(): Float = if (compactTypography) {
        FontScalePreference.Compact.multiplier
    } else {
        fontScale.multiplier
    }

    fun cardContainerAlpha(): Float = if (transparentCards) {
        cardTransparencyPercent.coerceIn(50, 96) / 100f
    } else {
        1f
    }

    fun backgroundAuraAlpha(): Float = if (dynamicBackground) motionPreference.auraAlpha else 0f

    fun summaryLabel(): String = "${themeMode.label} · ${accentPreset.label}"
}

val AppearancePreferencesSaver: Saver<AppearancePreferences, Any> = listSaver(
    save = {
        listOf(
            it.themeMode.name,
            it.accentPreset.name,
            it.fontScale.name,
            it.motionPreference.name,
            it.cardTransparencyPercent,
            it.transparentCards,
            it.dynamicBackground,
            it.compactTypography,
        )
    },
    restore = { values ->
        AppearancePreferences(
            themeMode = ThemeModePreference.valueOf(values[0] as String),
            accentPreset = AccentPreset.valueOf(values[1] as String),
            fontScale = FontScalePreference.valueOf(values[2] as String),
            motionPreference = MotionPreference.valueOf(values[3] as String),
            cardTransparencyPercent = values[4] as Int,
            transparentCards = values[5] as Boolean,
            dynamicBackground = values[6] as Boolean,
            compactTypography = values[7] as Boolean,
        )
    },
)

fun usedMemoryPercent(usedBytes: Long, totalBytes: Long): Int {
    val safeTotalBytes = totalBytes.coerceAtLeast(1L)
    val safeUsedBytes = usedBytes.coerceIn(0L, safeTotalBytes)
    return ((safeUsedBytes * 100f) / safeTotalBytes).roundToInt().coerceIn(0, 100)
}
