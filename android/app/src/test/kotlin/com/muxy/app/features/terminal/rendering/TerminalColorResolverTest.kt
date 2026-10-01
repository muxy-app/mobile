package com.muxy.app.features.terminal.rendering

import com.muxy.app.design.ThemeCatalog
import com.muxy.app.design.ThemePalette
import com.muxy.app.features.terminal.TerminalColor
import com.muxy.app.features.terminal.TerminalStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalColorResolverTest {
    private val theme = ThemeCatalog.muxy
    private val resolver = TerminalColorResolver(theme)

    @Test
    fun defaultColorsComeFromTheTheme() {
        val resolved = resolver.resolve(TerminalStyle.PLAIN)
        assertEquals(theme.foreground, resolved.foreground)
        assertEquals(theme.background, resolved.background)
        assertTrue(resolved.drawsText)
    }

    @Test
    fun theFirstSixteenColorsComeFromTheTheme() {
        assertEquals(theme.ansi[1], resolver.rgb(TerminalColor.Indexed(1), 0))
        assertEquals(theme.ansi[15], resolver.rgb(TerminalColor.Indexed(15), 0))
    }

    @Test
    fun extendedColorsFollowTheXtermPalette() {
        val expected = mapOf(16 to 0x000000, 21 to 0x0000FF, 196 to 0xFF0000, 231 to 0xFFFFFF, 232 to 0x080808, 255 to 0xEEEEEE)
        expected.forEach { (index, rgb) -> assertEquals("index $index", rgb, resolver.rgb(TerminalColor.Indexed(index), 0)) }
    }

    @Test
    fun rgbColorsAreExact() {
        assertEquals(0x123456, resolver.rgb(TerminalColor.Rgb(0x123456), 0))
    }

    @Test
    fun aShortPaletteFallsBack() {
        val short = TerminalColorResolver(ThemePalette("Short", 0xFFFFFF, 0x000000, emptyList(), 0xFFFFFF, 0x000000, 0xFFFFFF, 0x000000))
        assertEquals(0xABCDEF, short.rgb(TerminalColor.Indexed(3), 0xABCDEF))
    }

    @Test
    fun inverseSwapsIncludingDefaults() {
        val resolved = resolver.resolve(TerminalStyle(inverse = true))
        assertEquals(theme.background, resolved.foreground)
        assertEquals(theme.foreground, resolved.background)
    }

    @Test
    fun faintBlendsHalfwayTowardTheBackground() {
        val style = TerminalStyle(foreground = TerminalColor.Rgb(0xFFFFFF), background = TerminalColor.Rgb(0x000000), faint = true)
        assertEquals(0x808080, resolver.resolve(style).foreground)
    }

    @Test
    fun invisibleTextDrawsOnlyTheBackground() {
        assertFalse(resolver.resolve(TerminalStyle(invisible = true)).drawsText)
    }

    @Test
    fun underlinesUseTheirOwnColorOrTheForeground() {
        assertEquals(0x010203, resolver.resolve(TerminalStyle(foreground = TerminalColor.Rgb(0x010203))).decoration)
        assertEquals(0x090909, resolver.resolve(TerminalStyle(underlineColor = TerminalColor.Rgb(0x090909))).decoration)
    }
}
