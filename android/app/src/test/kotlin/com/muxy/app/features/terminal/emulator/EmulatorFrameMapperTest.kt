package com.muxy.app.features.terminal.emulator

import com.muxy.app.features.terminal.CursorShape
import com.muxy.app.features.terminal.TerminalColor
import com.muxy.app.features.terminal.TerminalFrame
import com.muxy.app.features.terminal.TerminalGridSize
import com.muxy.app.features.terminal.TerminalLine
import com.muxy.app.features.terminal.TerminalSpan
import com.muxy.app.features.terminal.UnderlineStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulatorFrameMapperTest {
    private val source = EmulatorTerminalSource(sink = {}, clipboard = {}).apply { resize(TerminalGridSize(20, 4)) }

    private fun frame(output: String): TerminalFrame {
        source.feed(output.toByteArray())
        return checkNotNull(source.frame())
    }

    private fun TerminalLine.text(): String = spans.joinToString("") { it.text }

    @Test
    fun plainTextIsOneRunPerStyle() {
        val first = frame("hello").lines[0]
        assertEquals(listOf(TerminalSpan.Run("hello" + " ".repeat(15), first.spans[0].style)), first.spans)
        assertEquals(TerminalColor.Default, first.spans[0].style.foreground)
        assertEquals(20, first.spans.sumOf { it.width })
    }

    @Test
    fun everyRowSpansTheFullWidth() {
        frame("a\r\nbb\r\n").lines.forEach { assertEquals(20, it.spans.sumOf { span -> span.width }) }
    }

    @Test
    fun colorsAndEffectsAreMapped() {
        val spans = frame("\u001B[1;3;4;9;31;48;5;200mA\u001B[0m\u001B[2;7;8;38;2;1;2;3mB").lines[0].spans
        val styled = spans[0].style
        assertEquals("A", spans[0].text)
        assertTrue(styled.bold && styled.italic && styled.strikethrough)
        assertEquals(UnderlineStyle.SINGLE, styled.underline)
        assertEquals(TerminalColor.Indexed(1), styled.foreground)
        assertEquals(TerminalColor.Indexed(200), styled.background)
        val second = spans[1].style
        assertTrue(second.faint && second.inverse && second.invisible)
        assertEquals(TerminalColor.Rgb(0x010203), second.foreground)
    }

    @Test
    fun wideCharactersAreTwoCellClusters() {
        val spans = frame("a你b").lines[0].spans
        assertEquals(TerminalSpan.Run("a", spans[0].style), spans[0])
        assertEquals(TerminalSpan.Cluster("你", 2, spans[1].style), spans[1])
        assertTrue(spans[2].text.startsWith("b"))
    }

    @Test
    fun surrogatePairsAndCombiningMarksStayInOneCell() {
        val spans = frame("😀éx").lines[0].spans
        assertEquals(TerminalSpan.Cluster("😀", 2, spans[0].style), spans[0])
        assertEquals(TerminalSpan.Cluster("é", 1, spans[1].style), spans[1])
        assertTrue(spans[2] is TerminalSpan.Run)
    }

    @Test
    fun theCursorFollowsTheOutput() {
        val cursor = frame("ab\r\ncd").cursor
        assertEquals(1, cursor.row)
        assertEquals(2, cursor.column)
        assertTrue(cursor.visible)
        assertEquals(CursorShape.BLOCK, cursor.shape)
    }

    @Test
    fun theCursorCanBeHiddenAndReshaped() {
        assertFalse(frame("\u001B[?25l").cursor.visible)
        assertEquals(CursorShape.BAR, frame("\u001B[?25h\u001B[6 q").cursor.shape)
        assertEquals(CursorShape.UNDERLINE, frame("\u001B[4 q").cursor.shape)
    }

    @Test
    fun modesReportMouseTrackingAndTheAlternateScreen() {
        assertFalse(frame("").modes.mouseTracking)
        assertTrue(frame("\u001B[?1000h").modes.mouseTracking)
        assertTrue(frame("\u001B[?1049h").modes.alternateScroll)
        assertFalse(frame("\u001B[?1049l").modes.alternateScroll)
    }

    @Test
    fun reverseVideoInvertsEverySpan() {
        assertTrue(frame("\u001B[?5hx").lines.all { line -> line.spans.all { it.style.inverse } })
    }

    @Test
    fun titleAndHistoryRowsAreReported() {
        val mapped = frame("\u001B]0;build\u0007" + (1..10).joinToString("\r\n"))
        assertEquals("build", mapped.title)
        assertEquals(6, mapped.historyRows)
        assertEquals("10", mapped.lines[3].text().trim())
    }
}
