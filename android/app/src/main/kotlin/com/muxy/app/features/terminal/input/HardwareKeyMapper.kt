package com.muxy.app.features.terminal.input

import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.muxy.app.features.terminalkit.TerminalKey
import com.muxy.app.features.terminalkit.TerminalKeyModifiers
import com.muxy.app.features.terminalkit.TerminalKeyStroke

sealed interface HardwareKeyAction {
    data class Stroke(
        val stroke: TerminalKeyStroke,
    ) : HardwareKeyAction

    data class Text(
        val text: String,
    ) : HardwareKeyAction

    data object Paste : HardwareKeyAction

    data object Copy : HardwareKeyAction
}

object HardwareKeyMapper {
    private const val FUNCTION_KEYS = 12

    private val specialKeys: Map<Int, TerminalKey> =
        mapOf(
            KeyEvent.KEYCODE_ESCAPE to TerminalKey.Escape,
            KeyEvent.KEYCODE_DPAD_UP to TerminalKey.Up,
            KeyEvent.KEYCODE_DPAD_DOWN to TerminalKey.Down,
            KeyEvent.KEYCODE_DPAD_LEFT to TerminalKey.Left,
            KeyEvent.KEYCODE_DPAD_RIGHT to TerminalKey.Right,
            KeyEvent.KEYCODE_MOVE_HOME to TerminalKey.Home,
            KeyEvent.KEYCODE_MOVE_END to TerminalKey.End,
            KeyEvent.KEYCODE_PAGE_UP to TerminalKey.PageUp,
            KeyEvent.KEYCODE_PAGE_DOWN to TerminalKey.PageDown,
            KeyEvent.KEYCODE_FORWARD_DEL to TerminalKey.Delete,
            KeyEvent.KEYCODE_INSERT to TerminalKey.Insert,
            KeyEvent.KEYCODE_ENTER to TerminalKey.Enter,
            KeyEvent.KEYCODE_NUMPAD_ENTER to TerminalKey.Enter,
            KeyEvent.KEYCODE_DEL to TerminalKey.Backspace,
        ) + (1..FUNCTION_KEYS).associate { KeyEvent.KEYCODE_F1 + it - 1 to TerminalKey.Function(it) }

    fun action(
        keyCode: Int,
        metaState: Int,
        unicodeChar: Int,
    ): HardwareKeyAction? {
        val shift = metaState and KeyEvent.META_SHIFT_ON != 0
        val control = metaState and KeyEvent.META_CTRL_ON != 0
        if (metaState and KeyEvent.META_META_ON != 0) return shortcut(unicodeChar)
        if (control && shift) shortcut(unicodeChar)?.let { return it }
        val modifiers = TerminalKeyModifiers(shift = shift, alt = metaState and KeyEvent.META_ALT_ON != 0, control = control)
        if (keyCode == KeyEvent.KEYCODE_TAB) {
            val key = if (shift) TerminalKey.BackTab else TerminalKey.Tab
            return HardwareKeyAction.Stroke(TerminalKeyStroke(key, modifiers.withoutShift()))
        }
        specialKeys[keyCode]?.let { return HardwareKeyAction.Stroke(TerminalKeyStroke(it, modifiers)) }
        val leftAlt = metaState and KeyEvent.META_ALT_LEFT_ON != 0
        return printable(unicodeChar, TerminalKeyModifiers(shift = shift, alt = leftAlt, control = control))
    }

    fun printableMetaState(metaState: Int): Int {
        val rightAlt = metaState and KeyEvent.META_ALT_RIGHT_ON != 0
        val altBits = if (rightAlt) 0 else KeyEvent.META_ALT_MASK
        val cleared = KeyEvent.META_CTRL_MASK or KeyEvent.META_META_MASK or altBits
        return metaState and cleared.inv()
    }

    private fun printable(
        unicodeChar: Int,
        modifiers: TerminalKeyModifiers,
    ): HardwareKeyAction? {
        if (unicodeChar <= 0 || unicodeChar and KeyCharacterMap.COMBINING_ACCENT != 0) return null
        val text = String(Character.toChars(unicodeChar))
        if (!modifiers.control && !modifiers.alt) return HardwareKeyAction.Text(text)
        return HardwareKeyAction.Stroke(TerminalKeyStroke(TerminalKey.Character(text), modifiers))
    }

    private fun shortcut(unicodeChar: Int): HardwareKeyAction? =
        when (Character.toLowerCase(unicodeChar)) {
            'v'.code -> HardwareKeyAction.Paste
            'c'.code -> HardwareKeyAction.Copy
            else -> null
        }
}
