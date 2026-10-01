package com.muxy.app.features.terminal.emulator

import com.muxy.app.features.terminal.TerminalCellPosition
import com.muxy.app.features.terminal.TerminalGridSize
import com.muxy.app.features.terminal.TerminalScrollDirection
import com.muxy.app.features.terminalkit.TerminalKey
import com.muxy.app.features.terminalkit.TerminalKeyModifiers
import com.muxy.app.features.terminalkit.TerminalKeyStroke
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulatorTerminalSourceTest {
    private val writes = mutableListOf<String>()
    private val resizes = mutableListOf<TerminalGridSize>()
    private val source =
        EmulatorTerminalSource(
            sink = { writes += it.decodeToString() },
            clipboard = {},
            onResize = { resizes += it },
        ).apply { resize(TerminalGridSize(20, 4)) }

    private fun press(
        key: TerminalKey,
        modifiers: TerminalKeyModifiers = TerminalKeyModifiers.NONE,
    ): String {
        writes.clear()
        source.send(TerminalKeyStroke(key, modifiers))
        return writes.joinToString("")
    }

    private fun feed(text: String) {
        source.feed(text.toByteArray())
        writes.clear()
    }

    @Test
    fun controlMapsToTheControlCode() {
        assertEquals("\u0003", press(TerminalKey.Character("c"), TerminalKeyModifiers.CONTROL))
        assertEquals("\u0003", press(TerminalKey.Character("C"), TerminalKeyModifiers.CONTROL))
        assertEquals("\u0000", press(TerminalKey.Character(" "), TerminalKeyModifiers.CONTROL))
    }

    @Test
    fun altPrependsEscapeAndShiftUppercases() {
        assertEquals("\u001Ba", press(TerminalKey.Character("a"), TerminalKeyModifiers.ALT))
        assertEquals("A", press(TerminalKey.Character("a"), TerminalKeyModifiers.SHIFT))
    }

    @Test
    fun unsupportedControlInputIsSentUnchanged() {
        assertEquals("1", press(TerminalKey.Character("1"), TerminalKeyModifiers.CONTROL))
    }

    @Test
    fun arrowsFollowTheCursorMode() {
        assertEquals("\u001B[A", press(TerminalKey.Up))
        assertEquals("\u001B[D", press(TerminalKey.Left))
        feed("\u001B[?1h")
        assertEquals("\u001BOA", press(TerminalKey.Up))
        assertEquals("\u001BOD", press(TerminalKey.Left))
    }

    @Test
    fun accessoryKeysEncodeToTheirBytes() {
        assertEquals("\u001B", press(TerminalKey.Escape))
        assertEquals("\t", press(TerminalKey.Tab))
        assertEquals("\r", press(TerminalKey.Enter))
        assertEquals("\u007F", press(TerminalKey.Backspace))
        assertEquals("~", press(TerminalKey.Character("~")))
    }

    @Test
    fun hardwareKeysUseXtermSequences() {
        assertEquals("\u001B[Z", press(TerminalKey.BackTab))
        assertEquals("\u001B[5~", press(TerminalKey.PageUp))
        assertEquals("\u001B[5;2~", press(TerminalKey.PageUp, TerminalKeyModifiers.SHIFT))
        assertEquals("\u001B[6;5~", press(TerminalKey.PageDown, TerminalKeyModifiers.CONTROL))
        assertEquals("\u001BOP", press(TerminalKey.Function(1)))
        assertEquals("\u001B[1;5C", press(TerminalKey.Right, TerminalKeyModifiers.CONTROL))
        assertEquals("\u001B\u001B", press(TerminalKey.Escape, TerminalKeyModifiers.ALT))
    }

    @Test
    fun textLineBreaksBecomeReturns() {
        source.sendText("a\nb\r\nc")
        assertEquals(listOf("a\rb\rc"), writes)
    }

    @Test
    fun pasteIsBracketedInOneWriteWhenTheProgramAsks() {
        feed("\u001B[?2004h")
        source.paste("a\nb")
        assertEquals(listOf("\u001B[200~a\rb\u001B[201~"), writes)
    }

    @Test
    fun pasteIsPlainOtherwise() {
        source.paste("a\nb")
        assertEquals(listOf("a\rb"), writes)
    }

    @Test
    fun repliesToTerminalQueriesAreSentOnce() {
        source.feed("ab\u001B[6n".toByteArray())
        assertEquals(listOf("\u001B[1;3R"), writes)
    }

    @Test
    fun scrollingSendsArrowsOnTheAlternateScreen() {
        feed("\u001B[?1049h")
        source.scroll(TerminalScrollDirection.UP, TerminalCellPosition(0, 0))
        source.scroll(TerminalScrollDirection.DOWN, TerminalCellPosition(0, 0))
        assertEquals(listOf("\u001B[A", "\u001B[B"), writes)
    }

    @Test
    fun scrollingAndClicksBecomeMouseEventsWhenTracked() {
        feed("\u001B[?1000h\u001B[?1006h")
        source.scroll(TerminalScrollDirection.DOWN, TerminalCellPosition(row = 1, column = 2))
        source.click(TerminalCellPosition(row = 1, column = 2))
        assertEquals(listOf("\u001B[<65;3;2M", "\u001B[<0;3;2M\u001B[<0;3;2m"), writes)
    }

    @Test
    fun resizingReportsTheGridAndRefitsTheScreen() {
        source.resize(TerminalGridSize(30, 6))
        assertEquals(TerminalGridSize(30, 6), resizes.last())
        assertEquals(30, source.frame()?.columns)
        assertEquals(6, source.frame()?.rows)
    }

    @Test
    fun restartingClearsTheScreenBeforeTheSnapshot() {
        feed("old")
        source.restart("new".toByteArray())
        assertTrue(
            source
                .frame()
                ?.lines
                ?.first()
                ?.spans
                ?.first()
                ?.text
                ?.startsWith("new") == true,
        )
    }

    @Test
    fun scrollbackIsAFrozenCopyServedInPages() =
        runTest {
            feed((1..10).joinToString("\r\n"))
            val scrollback = checkNotNull(source.scrollback(maxRows = 4))
            feed("\r\nlater")
            assertEquals(6, scrollback.historyRows)
            assertEquals(
                listOf("3", "4", "5", "6", "7", "8", "9", "10"),
                scrollback.lines().map {
                    it.spans
                        .first()
                        .text
                        .trim()
                },
            )
            assertEquals(
                listOf("1", "2"),
                scrollback.loadOlder(500).map {
                    it.spans
                        .first()
                        .text
                        .trim()
                },
            )
            assertTrue(scrollback.loadOlder(500).isEmpty())
        }
}
