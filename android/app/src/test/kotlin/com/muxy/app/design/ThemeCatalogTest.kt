package com.muxy.app.design

import com.muxy.app.persistence.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ThemeCatalogTest {
    @Test
    fun catalogHasTheIosThemesInOrder() {
        assertEquals(
            listOf(
                "Muxy",
                "Muxy Light",
                "Atom One Dark",
                "Catppuccin Latte",
                "Catppuccin Mocha",
                "Dracula",
                "GitHub Dark Default",
                "GitHub Light Default",
                "Gruvbox Dark",
                "Gruvbox Light",
                "Nord",
                "TokyoNight",
            ),
            ThemeCatalog.all.map { it.name },
        )
    }

    @Test
    fun everyPaletteHasSixteenAnsiColors() {
        ThemeCatalog.all.forEach { assertEquals(it.name, 16, it.ansi.size) }
    }

    @Test
    fun namesResolveToTheirPalette() {
        ThemeCatalog.all.forEach { assertSame(it, ThemeCatalog.named(it.name)) }
    }

    @Test
    fun unknownNamesFallBackToMuxy() {
        assertSame(ThemeCatalog.muxy, ThemeCatalog.named("Solarized"))
        assertSame(ThemeCatalog.muxy, ThemeCatalog.named(""))
    }

    @Test
    fun defaultThemeSettingNamesTheMuxyPalette() {
        assertEquals(ThemeCatalog.muxy.name, AppSettings().themeName)
    }
}
