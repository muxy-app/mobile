package com.muxy.app.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTokensTest {
    @Test
    fun textAndAccentsReachTheMinimumContrastOnEveryLayer() {
        ThemeCatalog.all.forEach { palette ->
            val tokens = ThemeTokens.from(palette)
            val layers =
                mapOf(
                    "background" to tokens.background,
                    "canvas" to tokens.groupedBackground,
                    "rows" to tokens.secondaryGroupedBackground,
                )
            val legible =
                mapOf(
                    "foreground" to tokens.foreground,
                    "secondaryForeground" to tokens.secondaryForeground,
                    "accent" to tokens.accent,
                    "red" to tokens.red,
                    "green" to tokens.green,
                    "yellow" to tokens.yellow,
                    "cyan" to tokens.cyan,
                )
            legible.forEach { (name, color) ->
                layers.forEach { (layer, background) ->
                    val contrast = color.contrast(background)
                    assertTrue(
                        "${palette.name}: $name on $layer is $contrast:1",
                        contrast >= ThemeTokens.MINIMUM_CONTRAST,
                    )
                }
            }
        }
    }

    @Test
    fun onAccentIsTheBackgroundWhenTheAccentIsLegibleOnIt() {
        ThemeCatalog.all.forEach { palette ->
            val tokens = ThemeTokens.from(palette)
            assertTrue(palette.name, tokens.accent.contrast(tokens.background) >= ThemeTokens.MINIMUM_CONTRAST)
            assertEquals(palette.name, tokens.background, tokens.onAccent)
        }
    }

    @Test
    fun onAccentFallsBackToTheMoreLegibleOfWhiteAndBlack() {
        val tokens = ThemeTokens.from(palette(foreground = 0xFFFFFF, background = 0x777777))
        assertEquals(ThemeColor.WHITE, tokens.accent)
        assertEquals(ThemeColor.BLACK, tokens.onAccent)
    }

    @Test
    fun darkThemesRaiseRowsAndLightThemesRaiseTheCanvas() {
        ThemeCatalog.all.forEach { palette ->
            val tokens = ThemeTokens.from(palette)
            val expected = if (tokens.isDark) tokens.secondaryGroupedBackground else tokens.groupedBackground
            assertEquals(palette.name, expected, tokens.secondaryBackground)
        }
    }

    private fun palette(
        foreground: Int,
        background: Int,
    ): ThemePalette =
        ThemePalette(
            name = "Test",
            foreground = foreground,
            background = background,
            ansi = emptyList(),
            cursor = foreground,
            cursorText = background,
            selectionBackground = foreground,
            selectionForeground = background,
        )
}
