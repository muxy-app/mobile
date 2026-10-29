package com.muxy.app.models

import java.text.BreakIterator

object TerminalTitle {
    fun sanitized(title: String): String {
        val trimmed = title.trim()
        if (trimmed.all { it.code < 0x80 }) return trimmed
        return graphemes(trimmed).filterNot(::isDecoration).joinToString("").trim()
    }

    private fun graphemes(text: String): List<String> {
        val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
        val result = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            result += text.substring(start, end)
            start = end
            end = iterator.next()
        }
        return result
    }

    private fun isDecoration(grapheme: String): Boolean {
        val codePoints = grapheme.codePoints().toArray()
        if (codePoints.all(::isInvisibleModifier)) return true
        return codePoints.any(::isDecorationCodePoint)
    }

    private fun isDecorationCodePoint(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        if (type == Character.OTHER_SYMBOL.toInt() || type == Character.PRIVATE_USE.toInt()) return true
        return codePoint == KEYCAP || codePoint == EMOJI_VARIATION || codePoint in SKIN_TONES
    }

    private fun isInvisibleModifier(codePoint: Int): Boolean =
        codePoint == ZERO_WIDTH_JOINER || codePoint == TEXT_VARIATION || codePoint == EMOJI_VARIATION || codePoint in TAGS

    private const val KEYCAP = 0x20E3
    private const val ZERO_WIDTH_JOINER = 0x200D
    private const val TEXT_VARIATION = 0xFE0E
    private const val EMOJI_VARIATION = 0xFE0F
    private val SKIN_TONES = 0x1F3FB..0x1F3FF
    private val TAGS = 0xE0020..0xE007F
}
