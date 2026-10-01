package com.muxy.app.features.terminal.emulator

import android.view.KeyEvent
import com.muxy.app.features.terminalkit.TerminalKey
import com.muxy.app.features.terminalkit.TerminalKeyModifiers
import com.muxy.app.features.terminalkit.TerminalKeyStroke
import com.termux.terminal.KeyHandler

object EmulatorKeyEncoder {
    private const val ESCAPE = "\u001B"
    private const val CONTROL_UPPER_START = 0x40
    private const val CONTROL_UPPER_END = 0x5F
    private const val CONTROL_LOWER_START = 0x61
    private const val CONTROL_LOWER_END = 0x7A
    private const val UPPER_OFFSET = 0x40
    private const val LOWER_OFFSET = 0x60
    private const val SPACE = 0x20

    fun bytes(
        stroke: TerminalKeyStroke,
        applicationCursor: Boolean,
        applicationKeypad: Boolean,
    ): ByteArray? {
        val key = stroke.key
        if (key is TerminalKey.Character) return character(key.text, stroke.modifiers).toByteArray()
        return special(key, stroke.modifiers, applicationCursor, applicationKeypad)?.toByteArray()
    }

    private fun character(
        text: String,
        modifiers: TerminalKeyModifiers,
    ): String {
        val shifted = if (modifiers.shift) text.uppercase() else text
        val controlled = if (modifiers.control) control(shifted) ?: shifted else shifted
        return if (modifiers.alt) ESCAPE + controlled else controlled
    }

    private fun control(text: String): String? {
        if (text.codePointCount(0, text.length) != 1) return null
        val value = text.codePointAt(0)
        return when (value) {
            in CONTROL_UPPER_START..CONTROL_UPPER_END -> (value - UPPER_OFFSET).toChar().toString()
            in CONTROL_LOWER_START..CONTROL_LOWER_END -> (value - LOWER_OFFSET).toChar().toString()
            SPACE -> "\u0000"
            else -> null
        }
    }

    private fun special(
        key: TerminalKey,
        modifiers: TerminalKeyModifiers,
        applicationCursor: Boolean,
        applicationKeypad: Boolean,
    ): String? {
        if (key == TerminalKey.Escape && modifiers.alt) return ESCAPE + ESCAPE
        if ((key == TerminalKey.PageUp || key == TerminalKey.PageDown) && !modifiers.isEmpty) {
            val number = if (key == TerminalKey.PageUp) 5 else 6
            return "$ESCAPE[$number;${xtermModifier(modifiers)}~"
        }
        val keyCode = keyCode(key) ?: return null
        val shifted = if (key == TerminalKey.BackTab) modifiers.copy(shift = true) else modifiers
        return KeyHandler.getCode(keyCode, keyMode(shifted), applicationCursor, applicationKeypad)
    }

    private fun keyCode(key: TerminalKey): Int? =
        when (key) {
            TerminalKey.Enter -> KeyEvent.KEYCODE_ENTER
            TerminalKey.Tab, TerminalKey.BackTab -> KeyEvent.KEYCODE_TAB
            TerminalKey.Escape -> KeyEvent.KEYCODE_ESCAPE
            TerminalKey.Backspace -> KeyEvent.KEYCODE_DEL
            TerminalKey.Insert -> KeyEvent.KEYCODE_INSERT
            TerminalKey.Delete -> KeyEvent.KEYCODE_FORWARD_DEL
            TerminalKey.Up -> KeyEvent.KEYCODE_DPAD_UP
            TerminalKey.Down -> KeyEvent.KEYCODE_DPAD_DOWN
            TerminalKey.Left -> KeyEvent.KEYCODE_DPAD_LEFT
            TerminalKey.Right -> KeyEvent.KEYCODE_DPAD_RIGHT
            TerminalKey.Home -> KeyEvent.KEYCODE_MOVE_HOME
            TerminalKey.End -> KeyEvent.KEYCODE_MOVE_END
            TerminalKey.PageUp -> KeyEvent.KEYCODE_PAGE_UP
            TerminalKey.PageDown -> KeyEvent.KEYCODE_PAGE_DOWN
            is TerminalKey.Function -> key.number.takeIf { it in 1..12 }?.let { KeyEvent.KEYCODE_F1 + it - 1 }
            is TerminalKey.Character -> null
        }

    private fun keyMode(modifiers: TerminalKeyModifiers): Int {
        var mode = 0
        if (modifiers.shift) mode = mode or KeyHandler.KEYMOD_SHIFT
        if (modifiers.alt) mode = mode or KeyHandler.KEYMOD_ALT
        if (modifiers.control) mode = mode or KeyHandler.KEYMOD_CTRL
        return mode
    }

    private fun xtermModifier(modifiers: TerminalKeyModifiers): Int {
        var value = 1
        if (modifiers.shift) value += 1
        if (modifiers.alt) value += 2
        if (modifiers.control) value += 4
        return value
    }
}
