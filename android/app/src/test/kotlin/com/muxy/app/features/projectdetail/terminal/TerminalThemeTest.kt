package com.muxy.app.features.projectdetail.terminal

import com.muxy.app.design.AppTheme
import com.muxy.app.design.ThemeCatalog
import com.muxy.app.networking.muxy1.protocol.ErrorCode
import com.muxy.app.networking.muxy1.protocol.ProtocolException
import com.muxy.app.networking.muxy1.transport.TransportException
import com.muxy.app.networking.muxy1.transport.TransportFailure
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class TerminalThemeTest {
    @Test
    fun clientThemeUsesThePaletteCursorAndSelection() {
        val palette = ThemeCatalog.named("Catppuccin Mocha")
        val theme = palette.clientTerminalTheme()
        assertEquals(palette.foreground, theme.fg)
        assertEquals(palette.background, theme.bg)
        assertEquals(palette.ansi, theme.palette)
        assertEquals(palette.cursor, theme.cursorColor)
        assertEquals(palette.cursorText, theme.cursorText)
        assertEquals(palette.selectionBackground, theme.selectionBackground)
        assertEquals(palette.selectionForeground, theme.selectionForeground)
    }

    @Test
    fun theMuxyPaletteHasSixteenColors() {
        val theme = ThemeCatalog.muxy.clientTerminalTheme()
        assertEquals(16, theme.palette.size)
        assertEquals(0xEC4899, theme.palette[1])
    }

    @Test
    fun theTerminalBackgroundMatchesTheScreen() {
        val app = AppTheme.from(ThemeCatalog.named("Muxy Light"))
        val theme = app.terminalPalette.clientTerminalTheme()
        assertEquals(0x1E1E2E, theme.fg)
        assertEquals(app.terminalPalette.background, theme.bg)
    }

    @Test
    fun takeoverFailuresExplainWhatHappened() {
        assertEquals(TakeoverFailure.TIMED_OUT, TakeoverFailure.message(TransportException(TransportFailure.TIMED_OUT)))
        assertEquals(TakeoverFailure.PANE_GONE, TakeoverFailure.message(ProtocolException(ErrorCode.NOT_FOUND.body("Unknown pane"))))
        assertEquals("Busy", TakeoverFailure.message(ProtocolException(ErrorCode.INTERNAL_ERROR.body("Busy"))))
        assertEquals(TakeoverFailure.INTERRUPTED, TakeoverFailure.message(IOException("closed")))
    }
}
