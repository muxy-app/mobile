package com.muxy.app.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AppTheme(
    val background: Color,
    val secondaryBackground: Color,
    val groupedBackground: Color,
    val secondaryGroupedBackground: Color,
    val tertiaryGroupedBackground: Color,
    val separator: Color,
    val foreground: Color,
    val secondaryForeground: Color,
    val accent: Color,
    val onAccent: Color,
    val red: Color,
    val green: Color,
    val yellow: Color,
    val cyan: Color,
    val isDark: Boolean,
    val terminalPalette: ThemePalette,
) {
    companion object {
        val muxy: AppTheme = from(ThemeCatalog.muxy)

        fun from(palette: ThemePalette): AppTheme {
            val tokens = ThemeTokens.from(palette)
            return AppTheme(
                background = tokens.background.color,
                secondaryBackground = tokens.secondaryBackground.color,
                groupedBackground = tokens.groupedBackground.color,
                secondaryGroupedBackground = tokens.secondaryGroupedBackground.color,
                tertiaryGroupedBackground = tokens.tertiaryGroupedBackground.color,
                separator = tokens.separator.color,
                foreground = tokens.foreground.color,
                secondaryForeground = tokens.secondaryForeground.color,
                accent = tokens.accent.color,
                onAccent = tokens.onAccent.color,
                red = tokens.red.color,
                green = tokens.green.color,
                yellow = tokens.yellow.color,
                cyan = tokens.cyan.color,
                isDark = tokens.isDark,
                terminalPalette = palette.replacingBackground(tokens.groupedBackground.rgb),
            )
        }

        fun interfaceTheme(
            isDark: Boolean,
            terminalPalette: ThemePalette,
        ): AppTheme {
            val palette =
                ThemeCatalog.muxy.copy(
                    foreground = if (isDark) 0xF3F4F8 else 0x20232D,
                    background = if (isDark) 0x171A21 else 0xF4F5F8,
                )
            val terminalBackground = ThemeTokens.from(terminalPalette).groupedBackground.rgb
            return from(palette).copy(terminalPalette = terminalPalette.replacingBackground(terminalBackground))
        }
    }
}

val LocalAppTheme = compositionLocalOf { AppTheme.muxy }

val ThemeColor.color: Color
    get() = rgbColor(rgb)

fun rgbColor(rgb: Int): Color = Color(OPAQUE_ALPHA or rgb)

private const val OPAQUE_ALPHA = 0xFF shl 24
