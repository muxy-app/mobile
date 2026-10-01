package com.muxy.app.features.terminalkit

sealed interface TerminalKey {
    data class Character(
        val text: String,
    ) : TerminalKey

    data object Enter : TerminalKey

    data object Tab : TerminalKey

    data object BackTab : TerminalKey

    data object Escape : TerminalKey

    data object Backspace : TerminalKey

    data object Insert : TerminalKey

    data object Delete : TerminalKey

    data object Up : TerminalKey

    data object Down : TerminalKey

    data object Left : TerminalKey

    data object Right : TerminalKey

    data object Home : TerminalKey

    data object End : TerminalKey

    data object PageUp : TerminalKey

    data object PageDown : TerminalKey

    data class Function(
        val number: Int,
    ) : TerminalKey
}

data class TerminalKeyModifiers(
    val shift: Boolean = false,
    val alt: Boolean = false,
    val control: Boolean = false,
) {
    val isEmpty: Boolean
        get() = !shift && !alt && !control

    operator fun plus(other: TerminalKeyModifiers): TerminalKeyModifiers =
        TerminalKeyModifiers(shift || other.shift, alt || other.alt, control || other.control)

    fun withoutShift(): TerminalKeyModifiers = copy(shift = false)

    companion object {
        val NONE = TerminalKeyModifiers()
        val SHIFT = TerminalKeyModifiers(shift = true)
        val ALT = TerminalKeyModifiers(alt = true)
        val CONTROL = TerminalKeyModifiers(control = true)
    }
}

data class TerminalKeyStroke(
    val key: TerminalKey,
    val modifiers: TerminalKeyModifiers = TerminalKeyModifiers.NONE,
)
