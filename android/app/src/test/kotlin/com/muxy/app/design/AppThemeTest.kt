package com.muxy.app.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeTest {
    @Test
    fun defaultMuxyThemeIsDark() {
        assertTrue(AppTheme.muxy.isDark)
    }

    @Test
    fun defaultMuxyThemeUsesMuxyBackgroundAndForeground() {
        assertEquals(rgbColor(0x19171F), AppTheme.muxy.background)
        assertEquals(rgbColor(0xC9C2D9), AppTheme.muxy.foreground)
    }

    @Test
    fun accentUsesPaletteIndexFour() {
        assertEquals(rgbColor(0xC370D3), AppTheme.muxy.accent)
    }

    @Test
    fun lightBackgroundIsNotDark() {
        assertFalse(AppTheme.from(palette(foreground = 0x000000, background = 0xFFFFFF)).isDark)
    }

    @Test
    fun darkBackgroundIsDark() {
        assertTrue(AppTheme.from(palette(foreground = 0xFFFFFF, background = 0x000000)).isDark)
    }

    @Test
    fun accentFallsBackToMuxyPaletteWhenMissing() {
        assertEquals(rgbColor(0xC370D3), AppTheme.from(palette(foreground = 0xFFFFFF, background = 0x000000)).accent)
    }

    @Test
    fun terminalPaletteUsesTheCanvasAsItsBackground() {
        ThemeCatalog.all.forEach { palette ->
            val terminalPalette = AppTheme.from(palette).terminalPalette
            assertEquals(palette.name, ThemeTokens.from(palette).groupedBackground.rgb, terminalPalette.background)
            assertEquals(palette.name, palette, terminalPalette.replacingBackground(palette.background))
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
