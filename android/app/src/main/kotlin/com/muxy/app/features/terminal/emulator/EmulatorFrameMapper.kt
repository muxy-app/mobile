package com.muxy.app.features.terminal.emulator

import com.muxy.app.features.terminal.CursorShape
import com.muxy.app.features.terminal.TerminalColor
import com.muxy.app.features.terminal.TerminalCursor
import com.muxy.app.features.terminal.TerminalFrame
import com.muxy.app.features.terminal.TerminalLine
import com.muxy.app.features.terminal.TerminalModes
import com.muxy.app.features.terminal.TerminalSpan
import com.muxy.app.features.terminal.TerminalStyle
import com.muxy.app.features.terminal.UnderlineStyle
import com.termux.terminal.TerminalBuffer
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalRow
import com.termux.terminal.TextStyle
import com.termux.terminal.WcWidth

object EmulatorFrameMapper {
    private const val TRUECOLOR = 0xFF000000.toInt()
    private const val RGB_MASK = 0xFFFFFF
    private const val INDEXED_COLORS = 256

    fun frame(emulator: TerminalEmulator): TerminalFrame {
        val screen = emulator.screen
        val columns = emulator.mColumns
        val reverse = emulator.isReverseVideo
        return TerminalFrame(
            columns = columns,
            rows = emulator.mRows,
            lines = (0 until emulator.mRows).map { line(screen, it, columns, reverse) },
            cursor = cursor(emulator, columns),
            modes = TerminalModes(mouseTracking = emulator.isMouseTrackingActive, alternateScroll = emulator.isAlternateBufferActive),
            title = emulator.title.orEmpty(),
            historyRows = screen.activeTranscriptRows,
        )
    }

    fun line(
        screen: TerminalBuffer,
        externalRow: Int,
        columns: Int,
        reverse: Boolean,
    ): TerminalLine = line(screen.allocateFullLineIfNecessary(screen.externalToInternalRow(externalRow)), columns, reverse)

    private fun line(
        row: TerminalRow,
        columns: Int,
        reverse: Boolean,
    ): TerminalLine {
        val chars = row.mText
        val used = row.spaceUsed
        val spans = ArrayList<TerminalSpan>()
        val run = StringBuilder()
        var runStyle = 0L
        var column = 0
        var index = 0

        fun flushRun() {
            if (run.isEmpty()) return
            spans += TerminalSpan.Run(run.toString(), style(runStyle, reverse))
            run.setLength(0)
        }

        while (column < columns && index < used) {
            val start = index
            val codePoint = Character.codePointAt(chars, index, used)
            index += Character.charCount(codePoint)
            while (index < used && WcWidth.width(chars, index) <= 0) {
                index += Character.charCount(Character.codePointAt(chars, index, used))
            }
            val width = WcWidth.width(codePoint).coerceIn(1, 2).coerceAtMost(columns - column)
            val styleBits = row.getStyle(column)
            val simple = width == 1 && index - start == 1
            if (simple && run.isNotEmpty() && styleBits == runStyle) {
                run.append(chars[start])
            } else {
                flushRun()
                if (simple) {
                    run.append(chars[start])
                    runStyle = styleBits
                } else {
                    spans += TerminalSpan.Cluster(String(chars, start, index - start), width, style(styleBits, reverse))
                }
            }
            column += width
        }
        flushRun()
        if (column < columns) spans += TerminalSpan.Run(" ".repeat(columns - column), TerminalStyle(inverse = reverse))
        return TerminalLine(spans)
    }

    private fun cursor(
        emulator: TerminalEmulator,
        columns: Int,
    ): TerminalCursor =
        TerminalCursor(
            row = emulator.cursorRow,
            column = emulator.cursorCol.coerceIn(0, (columns - 1).coerceAtLeast(0)),
            visible = emulator.shouldCursorBeVisible(),
            shape =
                when (emulator.cursorStyle) {
                    TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR -> CursorShape.BAR
                    TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE -> CursorShape.UNDERLINE
                    else -> CursorShape.BLOCK
                },
        )

    private fun style(
        bits: Long,
        reverse: Boolean,
    ): TerminalStyle {
        val effect = TextStyle.decodeEffect(bits)

        fun has(attribute: Int): Boolean = effect and attribute != 0
        return TerminalStyle(
            foreground = color(TextStyle.decodeForeColor(bits)),
            background = color(TextStyle.decodeBackColor(bits)),
            bold = has(TextStyle.CHARACTER_ATTRIBUTE_BOLD),
            italic = has(TextStyle.CHARACTER_ATTRIBUTE_ITALIC),
            faint = has(TextStyle.CHARACTER_ATTRIBUTE_DIM),
            underline = if (has(TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE)) UnderlineStyle.SINGLE else UnderlineStyle.NONE,
            strikethrough = has(TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH),
            inverse = has(TextStyle.CHARACTER_ATTRIBUTE_INVERSE) != reverse,
            invisible = has(TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE),
        )
    }

    private fun color(value: Int): TerminalColor {
        if (value and TRUECOLOR == TRUECOLOR) return TerminalColor.Rgb(value and RGB_MASK)
        if (value in 0 until INDEXED_COLORS) return TerminalColor.Indexed(value)
        return TerminalColor.Default
    }
}
