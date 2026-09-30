package com.muxy.app.features.projects

object ProjectIconColor {
    fun rgb(
        token: String?,
        isDark: Boolean,
    ): Int? {
        if (token.isNullOrEmpty()) return null
        if (token.startsWith('#')) return hexRgb(token.substring(1))
        val color = palette[token.lowercase()] ?: return null
        return if (isDark) color.dark else color.light
    }

    private fun hexRgb(hex: String): Int? {
        val expanded = if (hex.length == SHORT_HEX_LENGTH) hex.map { "$it$it" }.joinToString("") else hex
        if (expanded.length != HEX_LENGTH || !expanded.all(::isHexDigit)) return null
        return expanded.toIntOrNull(HEX_RADIX)
    }

    private fun isHexDigit(character: Char): Boolean = character in '0'..'9' || character.lowercaseChar() in 'a'..'f'

    private class SystemColor(
        val light: Int,
        val dark: Int,
    )

    private const val SHORT_HEX_LENGTH = 3
    private const val HEX_LENGTH = 6
    private const val HEX_RADIX = 16

    private val purple = SystemColor(0xAF52DE, 0xBF5AF2)
    private val gray = SystemColor(0x8E8E93, 0x8E8E93)

    private val palette: Map<String, SystemColor> =
        mapOf(
            "red" to SystemColor(0xFF3B30, 0xFF453A),
            "orange" to SystemColor(0xFF9500, 0xFF9F0A),
            "yellow" to SystemColor(0xFFCC00, 0xFFD60A),
            "green" to SystemColor(0x34C759, 0x30D158),
            "mint" to SystemColor(0x00C7BE, 0x63E6E2),
            "teal" to SystemColor(0x30B0C7, 0x40CBE0),
            "cyan" to SystemColor(0x32ADE6, 0x64D2FF),
            "blue" to SystemColor(0x007AFF, 0x0A84FF),
            "indigo" to SystemColor(0x5856D6, 0x5E5CE6),
            "violet" to purple,
            "purple" to purple,
            "pink" to SystemColor(0xFF2D55, 0xFF375F),
            "brown" to SystemColor(0xA2845E, 0xAC8E68),
            "gray" to gray,
            "grey" to gray,
        )
}
