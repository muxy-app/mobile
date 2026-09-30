package com.muxy.app.design.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RowPositionTest {
    @Test
    fun aLoneRowIsSingle() {
        assertEquals(RowPosition.SINGLE, RowPosition.of(index = 0, count = 1))
    }

    @Test
    fun rowsTakeTheirPlaceInTheSection() {
        assertEquals(
            listOf(RowPosition.FIRST, RowPosition.MIDDLE, RowPosition.MIDDLE, RowPosition.LAST),
            (0 until 4).map { RowPosition.of(index = it, count = 4) },
        )
    }

    @Test
    fun twoRowsAreFirstAndLast() {
        assertEquals(listOf(RowPosition.FIRST, RowPosition.LAST), (0 until 2).map { RowPosition.of(index = it, count = 2) })
    }

    @Test
    fun onlyRowsWithARowBelowHaveASeparator() {
        assertTrue(RowPosition.FIRST.hasSeparator)
        assertTrue(RowPosition.MIDDLE.hasSeparator)
        assertFalse(RowPosition.LAST.hasSeparator)
        assertFalse(RowPosition.SINGLE.hasSeparator)
    }
}
