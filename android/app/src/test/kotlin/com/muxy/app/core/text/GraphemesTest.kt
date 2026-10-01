package com.muxy.app.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class GraphemesTest {
    @Test
    fun chunksPreserveEmptyAndShortStrings() {
        assertEquals(listOf(""), Graphemes.chunks("", 2))
        assertEquals(listOf("ab"), Graphemes.chunks("ab", 2))
        assertEquals(listOf("ab", "cd", "e"), Graphemes.chunks("abcde", 2))
    }

    @Test
    fun chunksDoNotSplitSurrogatePairsOrCombiningSequences() {
        assertEquals(listOf("🙂", "e\u0301", "z"), Graphemes.chunks("🙂e\u0301z", 1))
        assertEquals(listOf("🙂e\u0301", "z"), Graphemes.chunks("🙂e\u0301z", 2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun chunksRequireAPositiveSize() {
        Graphemes.chunks("text", 0)
    }
}
