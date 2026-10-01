package com.muxy.app.features.terminalkit

import com.muxy.app.core.text.Graphemes

enum class TerminalModifier(
    val title: String,
    val displayName: String,
    val glyph: String,
    val keyModifiers: TerminalKeyModifiers,
) {
    CTRL("ctrl", "Control", "⌃", TerminalKeyModifiers.CONTROL),
    SHIFT("shift", "Shift", "⇧", TerminalKeyModifiers.SHIFT),
    ALT("alt", "Alt", "⌥", TerminalKeyModifiers.ALT),
}

data class StickyModifier(
    val active: TerminalModifier = TerminalModifier.CTRL,
    val armed: Boolean = false,
) {
    fun applied(stroke: TerminalKeyStroke): TerminalKeyStroke = stroke.copy(modifiers = stroke.modifiers + active.keyModifiers)

    fun stroke(text: String): TerminalKeyStroke? {
        if (!Graphemes.isSingle(text)) return null
        controlKey(text)?.let { return TerminalKeyStroke(it, active.keyModifiers) }
        return when (active) {
            TerminalModifier.CTRL -> TerminalKeyStroke(TerminalKey.Character(text), TerminalKeyModifiers.CONTROL)
            TerminalModifier.ALT -> TerminalKeyStroke(TerminalKey.Character(text), TerminalKeyModifiers.ALT)
            TerminalModifier.SHIFT -> TerminalKeyStroke(TerminalKey.Character(text.uppercase()))
        }
    }

    private fun controlKey(text: String): TerminalKey? =
        when (text) {
            "\n", "\r", "\r\n" -> TerminalKey.Enter
            "\t" -> TerminalKey.Tab
            else -> null
        }
}
