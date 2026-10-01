package com.muxy.app.features.terminal.rendering

import com.muxy.app.features.terminal.TerminalLine
import com.muxy.app.features.terminal.TerminalSpan
import com.muxy.app.features.terminal.TerminalStyle

data class TerminalCellContent(
    val text: String,
    val width: Int,
    val style: TerminalStyle,
)

object TerminalLineCells {
    fun cell(
        column: Int,
        line: TerminalLine,
    ): TerminalCellContent? {
        var start = 0
        for (span in line.spans) {
            val width = span.width
            if (column >= start && column < start + width) {
                return when (span) {
                    is TerminalSpan.Run -> TerminalCellContent(span.text[column - start].toString(), 1, span.style)
                    is TerminalSpan.Cluster -> TerminalCellContent(span.text, width, span.style)
                }
            }
            start += width
        }
        return null
    }

    fun text(
        line: TerminalLine,
        columns: IntRange,
    ): String {
        val result = StringBuilder()
        var start = 0
        for (span in line.spans) {
            val end = start + span.width
            val overlaps = start <= columns.last && end > columns.first
            if (overlaps) {
                when (span) {
                    is TerminalSpan.Run -> {
                        result.append(span.text, maxOf(columns.first, start) - start, minOf(columns.last + 1, end) - start)
                    }

                    is TerminalSpan.Cluster -> {
                        result.append(span.text)
                    }
                }
            }
            start = end
        }
        return result.toString()
    }
}
