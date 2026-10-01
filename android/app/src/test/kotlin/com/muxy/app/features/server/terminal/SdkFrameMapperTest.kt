package com.muxy.app.features.server.terminal

import com.muxy.app.features.terminal.CursorShape
import com.muxy.app.features.terminal.TerminalColor
import com.muxy.app.features.terminal.TerminalSpan
import com.muxy.app.features.terminal.TerminalStyle
import com.muxy.app.features.terminal.UnderlineStyle
import com.muxy.app.testing.sdkLine
import com.muxy.app.testing.sdkScreen
import com.muxy.app.testing.sdkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.muxy_mobile.Cursor
import uniffi.muxy_mobile.Line
import uniffi.muxy_mobile.Span
import uniffi.muxy_mobile.Underline
import uniffi.muxy_mobile.CursorShape as SdkCursorShape
import uniffi.muxy_mobile.TerminalColor as SdkTerminalColor

class SdkFrameMapperTest {
    private fun line(vararg spans: Pair<String, Int>): Line = Line(spans.map { (text, width) -> Span(text, width.toUShort(), sdkStyle()) })

    @Test
    fun theScreenSizeTitleModesAndHistoryCarryOver() {
        val frame = SdkFrameMapper.frame(sdkScreen(title = "vim", columns = 100, rows = 30, historyRows = 42, mouseTracking = true))
        assertEquals(100, frame.columns)
        assertEquals(30, frame.rows)
        assertEquals("vim", frame.title)
        assertEquals(42, frame.historyRows)
        assertTrue(frame.modes.mouseTracking)
    }

    @Test
    fun theCursorKeepsItsPositionAndShape() {
        val screen = sdkScreen().copy(cursor = Cursor(row = 3u, column = 7u, visible = false, shape = SdkCursorShape.BAR))
        val cursor = SdkFrameMapper.frame(screen).cursor
        assertEquals(3, cursor.row)
        assertEquals(7, cursor.column)
        assertEquals(false, cursor.visible)
        assertEquals(CursorShape.BAR, cursor.shape)
    }

    @Test
    fun plainTextIsOneRun() {
        assertEquals(listOf(TerminalSpan.Run("ls -la", TerminalStyle.PLAIN)), SdkFrameMapper.line(sdkLine("ls -la")).spans)
    }

    @Test
    fun wideCharactersTakeTwoCellsEach() {
        val spans = SdkFrameMapper.line(line("a中b" to 4)).spans
        assertEquals(
            listOf(
                TerminalSpan.Run("a", TerminalStyle.PLAIN),
                TerminalSpan.Cluster("中", 2, TerminalStyle.PLAIN),
                TerminalSpan.Run("b", TerminalStyle.PLAIN),
            ),
            spans,
        )
        assertEquals(4, spans.sumOf { it.width })
    }

    @Test
    fun surrogatePairsAreNeverSplit() {
        val icon = "󰀀"
        val spans = SdkFrameMapper.line(line("$icon x" to 3)).spans
        assertEquals(TerminalSpan.Cluster(icon, 1, TerminalStyle.PLAIN), spans.first())
        assertEquals(3, spans.sumOf { it.width })
    }

    @Test
    fun combiningMarksStayWithTheirLetter() {
        val spans = SdkFrameMapper.line(line("éx" to 2)).spans
        assertEquals(listOf(TerminalSpan.Cluster("é", 1, TerminalStyle.PLAIN), TerminalSpan.Run("x", TerminalStyle.PLAIN)), spans)
    }

    @Test
    fun aSpanWhoseCellsDisagreeKeepsTheServersWidth() {
        assertEquals(listOf(TerminalSpan.Cluster("ab", 5, TerminalStyle.PLAIN)), SdkFrameMapper.line(line("ab" to 5)).spans)
    }

    @Test
    fun emptySpansAreDropped() {
        assertEquals(emptyList<TerminalSpan>(), SdkFrameMapper.line(line("" to 0, "x" to 0)).spans)
    }

    @Test
    fun colorsAndStylesCarryOver() {
        val style = sdkStyle(foreground = SdkTerminalColor.Rgb(0x12u, 0x34u, 0x56u), inverse = true, underline = Underline.CURLY)
        val styled = Line(listOf(Span("x", 1u, style), Span("y", 1u, sdkStyle(foreground = SdkTerminalColor.Indexed(9u)))))
        val spans = SdkFrameMapper.line(styled).spans
        assertEquals(TerminalColor.Rgb(0x123456), spans[0].style.foreground)
        assertTrue(spans[0].style.inverse)
        assertEquals(UnderlineStyle.CURLY, spans[0].style.underline)
        assertEquals(TerminalColor.Indexed(9), spans[1].style.foreground)
    }
}
