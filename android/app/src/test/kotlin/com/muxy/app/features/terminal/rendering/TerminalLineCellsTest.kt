package com.muxy.app.features.terminal.rendering

import com.muxy.app.features.terminal.TerminalLine
import com.muxy.app.features.terminal.TerminalSpan
import com.muxy.app.features.terminal.TerminalStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalLineCellsTest {
    private val bold = TerminalStyle(bold = true)
    private val line =
        TerminalLine(
            listOf(
                TerminalSpan.Run("ab ", TerminalStyle.PLAIN),
                TerminalSpan.Cluster("你", 2, bold),
                TerminalSpan.Run("cd", TerminalStyle.PLAIN),
            ),
        )

    @Test
    fun runCellsHoldOneCharacter() {
        val cell = TerminalLineCells.cell(1, line)
        assertEquals("b", cell?.text)
        assertEquals(1, cell?.width)
    }

    @Test
    fun wideCharactersCoverBothCells() {
        val first = TerminalLineCells.cell(3, line)
        assertEquals("你", first?.text)
        assertEquals(2, first?.width)
        assertEquals(first, TerminalLineCells.cell(4, line))
        assertEquals(bold, first?.style)
    }

    @Test
    fun aSurrogatePairClusterStaysWhole() {
        val emoji = TerminalLine(listOf(TerminalSpan.Cluster("😀", 2, TerminalStyle.PLAIN)))
        assertEquals("😀", TerminalLineCells.cell(1, emoji)?.text)
        assertEquals("😀", TerminalLineCells.text(emoji, 1..1))
    }

    @Test
    fun cellsPastTheEndOfTheLineAreEmpty() {
        assertNull(TerminalLineCells.cell(7, line))
    }

    @Test
    fun textOfAColumnRangeIncludesPartlyCoveredWideCharacters() {
        assertEquals("b 你", TerminalLineCells.text(line, 1 until 4))
        assertEquals("你cd", TerminalLineCells.text(line, 4 until 7))
    }

    @Test
    fun textOfAnEmptyRangeIsEmpty() {
        assertTrue(TerminalLineCells.text(line, 9 until 12).isEmpty())
    }
}
