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
    }
}

val LocalAppTheme = compositionLocalOf { AppTheme.muxy }

val ThemeColor.color: Color
    get() = rgbColor(rgb)

fun rgbColor(rgb: Int): Color = Color(OPAQUE_ALPHA or rgb)

private const val OPAQUE_ALPHA = 0xFF shl 24
