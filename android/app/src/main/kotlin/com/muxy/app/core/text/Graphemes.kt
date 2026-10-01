package com.muxy.app.core.text

import java.text.BreakIterator

object Graphemes {
    fun count(text: String): Int = boundaries(text).size

    fun isSingle(text: String): Boolean = count(text) == 1

    fun prefix(
        text: String,
        count: Int,
    ): String {
        require(count >= 0)
        if (count == 0) return ""
        if (text.length <= count) return text
        val end = iterator(text).next(count)
        return if (end == BreakIterator.DONE) text else text.substring(0, end)
    }

    fun chunks(
        text: String,
        count: Int,
    ): List<String> {
        require(count > 0)
        if (text.length <= count) return listOf(text)
        val iterator = iterator(text)
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = iterator.next(count).takeUnless { it == BreakIterator.DONE } ?: text.length
            chunks += text.substring(start, end)
            start = end
        }
        return chunks
    }

    fun commonPrefixLength(
        first: String,
        second: String,
    ): Int {
        val secondBoundaries = boundaries(second).toSet()
        return boundaries(first).lastOrNull { end -> end in secondBoundaries && first.regionMatches(0, second, 0, end) } ?: 0
    }

    private fun boundaries(text: String): List<Int> {
        if (text.isEmpty()) return emptyList()
        val iterator = iterator(text)
        val ends = mutableListOf<Int>()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            ends += end
            end = iterator.next()
        }
        return ends
    }

    private fun iterator(text: String): BreakIterator = BreakIterator.getCharacterInstance().apply { setText(text) }
}
