package com.muxy.app.features.terminal.rendering

import com.muxy.app.features.terminal.TerminalCellPosition
import com.muxy.app.testing.lines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TerminalSelectionTest {
    private fun selection(
        anchor: Pair<Int, Int>,
        head: Pair<Int, Int>,
    ) = TerminalSelection(TerminalCellPosition(anchor.first, anchor.second), TerminalCellPosition(head.first, head.second))

    @Test
    fun aSingleRowSelectionCoversItsColumns() {
        assertEquals(2 until 6, selection(0 to 2, 0 to 5).columns(0, 80))
    }

    @Test
    fun draggingBackwardsSelectsTheSameCells() {
        assertEquals(2 until 6, selection(0 to 5, 0 to 2).columns(0, 80))
    }

    @Test
    fun middleRowsAreSelectedEdgeToEdge() {
        val selected = selection(1 to 10, 3 to 4)
        assertEquals(10 until 80, selected.columns(1, 80))
        assertEquals(0 until 80, selected.columns(2, 80))
        assertEquals(0 until 5, selected.columns(3, 80))
        assertNull(selected.columns(4, 80))
    }

    @Test
    fun aSelectionPastANarrowerGridSelectsNothingInsteadOfCrashing() {
        assertNull(selection(0 to 80, 0 to 85).columns(0, 45))
    }

    @Test
    fun aSelectionEndingPastANarrowerGridIsClamped() {
        assertEquals(40 until 45, selection(0 to 40, 0 to 85).columns(0, 45))
    }

    @Test
    fun copiedTextJoinsRowsAndTrimsTrailingSpaces() {
        assertEquals("hello\nworld", selection(0 to 0, 1 to 4).text(lines("hello   ", "world"), 8))
    }

    @Test
    fun rowsPastTheDocumentAreIgnored() {
        assertEquals("only", selection(0 to 0, 5 to 3).text(lines("only"), 8))
    }

    @Test
    fun shiftingMovesBothEnds() {
        assertEquals(selection(3 to 1, 5 to 2), selection(1 to 1, 3 to 2).shifted(2))
    }
}
