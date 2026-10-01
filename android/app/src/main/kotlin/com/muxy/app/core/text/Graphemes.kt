package com.muxy.app.core.text

import java.text.BreakIterator

object Graphemes {
    fun count(text: String): Int = boundaries(text).size

    fun isSingle(text: String): Boolean = count(text) == 1

    fun commonPrefixLength(
        first: String,
        second: String,
    ): Int {
        val secondBoundaries = boundaries(second).toSet()
        return boundaries(first).lastOrNull { end -> end in secondBoundaries && first.regionMatches(0, second, 0, end) } ?: 0
    }

    fun previousBoundary(
        text: String,
        offset: Int,
    ): Int {
        if (offset <= 0 || text.isEmpty()) return 0
        return iterator(text).preceding(offset.coerceAtMost(text.length)).coerceAtLeast(0)
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
