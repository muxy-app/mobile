package com.muxy.app.features.server.terminal

import com.muxy.app.features.terminal.TerminalScrollDirection
import com.muxy.app.features.terminalkit.TerminalKey
import com.muxy.app.features.terminalkit.TerminalKeyModifiers
import com.muxy.app.features.terminalkit.TerminalKeyStroke
import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.muxy_mobile.Key
import uniffi.muxy_mobile.Modifiers
import uniffi.muxy_mobile.ScrollDirection

class SdkKeysTest {
    @Test
    fun controlCharactersKeepTheirModifier() {
        val stroke = TerminalKeyStroke(TerminalKey.Character("c"), TerminalKeyModifiers.CONTROL)
        assertEquals(Key.Character("c"), SdkKeys.key(stroke))
        assertEquals(Modifiers(shift = false, alt = false, control = true), SdkKeys.modifiers(stroke.modifiers))
    }

    @Test
    fun specialKeysMapOneToOne() {
        assertEquals(Key.BackTab, SdkKeys.key(TerminalKeyStroke(TerminalKey.BackTab)))
        assertEquals(Key.PageUp, SdkKeys.key(TerminalKeyStroke(TerminalKey.PageUp)))
        assertEquals(Key.Function(7u), SdkKeys.key(TerminalKeyStroke(TerminalKey.Function(7))))
    }

    @Test
    fun combinedModifiersAreAllSent() {
        val modifiers = TerminalKeyModifiers(shift = true, alt = true, control = true)
        assertEquals(Modifiers(shift = true, alt = true, control = true), SdkKeys.modifiers(modifiers))
    }

    @Test
    fun scrollDirectionsMapOneToOne() {
        assertEquals(ScrollDirection.UP, SdkKeys.direction(TerminalScrollDirection.UP))
        assertEquals(ScrollDirection.DOWN, SdkKeys.direction(TerminalScrollDirection.DOWN))
    }
}
