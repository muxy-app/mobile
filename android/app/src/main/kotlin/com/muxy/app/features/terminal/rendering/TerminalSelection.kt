package com.muxy.app.features.terminal.rendering

import com.muxy.app.features.terminal.TerminalCellPosition
import com.muxy.app.features.terminal.TerminalLine

data class TerminalSelection(
    val anchor: TerminalCellPosition,
    val head: TerminalCellPosition,
) {
    val start: TerminalCellPosition
        get() = minOf(anchor, head)

    val end: TerminalCellPosition
        get() = maxOf(anchor, head)

    val rows: IntRange
        get() = start.row..end.row

    fun columns(
        row: Int,
        columnCount: Int,
    ): IntRange? {
        if (row !in rows || columnCount <= 0) return null
        val lower = maxOf(0, if (row == start.row) start.column else 0)
        val upper = minOf(columnCount, if (row == end.row) end.column + 1 else columnCount)
        if (lower >= upper) return null
        return lower until upper
    }

    fun text(
        lines: List<TerminalLine>,
        columnCount: Int,
    ): String =
        rows
            .filter { it in lines.indices }
            .joinToString("\n") { row ->
                val columns = columns(row, columnCount) ?: return@joinToString ""
                TerminalLineCells.text(lines[row], columns).trimEnd(' ')
            }

    fun shifted(rows: Int): TerminalSelection = TerminalSelection(anchor.copy(row = anchor.row + rows), head.copy(row = head.row + rows))
}
