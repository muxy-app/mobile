package com.muxy.app.core.text

import java.text.Collator
import java.util.Locale

class NaturalOrder(
    locale: Locale = Locale.getDefault(),
) : Comparator<String> {
    private val loose = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    private val strict = Collator.getInstance(locale).apply { strength = Collator.TERTIARY }

    override fun compare(
        first: String,
        second: String,
    ): Int {
        val firstChunks = chunks(first)
        val secondChunks = chunks(second)
        for (index in 0 until minOf(firstChunks.size, secondChunks.size)) {
            val order = compareChunks(firstChunks[index], secondChunks[index])
            if (order != 0) return order
        }
        if (firstChunks.size != secondChunks.size) return firstChunks.size.compareTo(secondChunks.size)
        return strict.compare(first, second)
    }

    private fun compareChunks(
        first: String,
        second: String,
    ): Int {
        if (first.first().isDigit() && second.first().isDigit()) return compareNumbers(first, second)
        return loose.compare(first, second)
    }

    private fun compareNumbers(
        first: String,
        second: String,
    ): Int {
        val firstDigits = first.trimStart('0')
        val secondDigits = second.trimStart('0')
        if (firstDigits.length != secondDigits.length) return firstDigits.length.compareTo(secondDigits.length)
        return firstDigits.compareTo(secondDigits)
    }

    private fun chunks(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var start = 0
        for (index in 1..text.length) {
            val boundary = index == text.length || text[index].isDigit() != text[index - 1].isDigit()
            if (!boundary) continue
            result += text.substring(start, index)
            start = index
        }
        return result
    }
}
