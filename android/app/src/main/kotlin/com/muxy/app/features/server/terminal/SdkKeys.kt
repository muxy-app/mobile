package com.muxy.app.features.server.terminal

import com.muxy.app.features.terminal.TerminalScrollDirection
import com.muxy.app.features.terminalkit.TerminalKey
import com.muxy.app.features.terminalkit.TerminalKeyModifiers
import com.muxy.app.features.terminalkit.TerminalKeyStroke
import uniffi.muxy_mobile.Key
import uniffi.muxy_mobile.Modifiers
import uniffi.muxy_mobile.ScrollDirection

object SdkKeys {
    val none: Modifiers = Modifiers(shift = false, alt = false, control = false)

    fun key(stroke: TerminalKeyStroke): Key =
        when (val key = stroke.key) {
            is TerminalKey.Character -> Key.Character(key.text)
            TerminalKey.Enter -> Key.Enter
            TerminalKey.Tab -> Key.Tab
            TerminalKey.BackTab -> Key.BackTab
            TerminalKey.Escape -> Key.Escape
            TerminalKey.Backspace -> Key.Backspace
            TerminalKey.Insert -> Key.Insert
            TerminalKey.Delete -> Key.Delete
            TerminalKey.Up -> Key.Up
            TerminalKey.Down -> Key.Down
            TerminalKey.Left -> Key.Left
            TerminalKey.Right -> Key.Right
            TerminalKey.Home -> Key.Home
            TerminalKey.End -> Key.End
            TerminalKey.PageUp -> Key.PageUp
            TerminalKey.PageDown -> Key.PageDown
            is TerminalKey.Function -> Key.Function(key.number.coerceIn(0, UByte.MAX_VALUE.toInt()).toUByte())
        }

    fun modifiers(modifiers: TerminalKeyModifiers): Modifiers = Modifiers(modifiers.shift, modifiers.alt, modifiers.control)

    fun direction(direction: TerminalScrollDirection): ScrollDirection =
        when (direction) {
            TerminalScrollDirection.UP -> ScrollDirection.UP
            TerminalScrollDirection.DOWN -> ScrollDirection.DOWN
        }
}
