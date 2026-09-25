import Foundation
import MuxyMobile

nonisolated struct TerminalCellPosition: Hashable, Comparable, Sendable {
    let row: Int
    let column: Int

    static func < (lhs: TerminalCellPosition, rhs: TerminalCellPosition) -> Bool {
        (lhs.row, lhs.column) < (rhs.row, rhs.column)
    }
}

nonisolated struct TerminalSelection: Equatable, Sendable {
    let anchor: TerminalCellPosition
    var head: TerminalCellPosition

    var start: TerminalCellPosition {
        min(anchor, head)
    }

    var end: TerminalCellPosition {
        max(anchor, head)
    }

    var rows: ClosedRange<Int> {
        start.row...end.row
    }

    func columns(inRow row: Int, columnCount: Int) -> Range<Int>? {
        guard rows.contains(row), columnCount > 0 else { return nil }
        let lower = max(0, row == start.row ? start.column : 0)
        let upper = min(columnCount, row == end.row ? end.column + 1 : columnCount)
        guard lower < upper else { return nil }
        return lower..<upper
    }

    func text(in lines: [Line], columnCount: Int) -> String {
        rows.compactMap { row -> String? in
            guard lines.indices.contains(row), let columns = columns(inRow: row, columnCount: columnCount) else {
                return lines.indices.contains(row) ? "" : nil
            }
            let text = TerminalLineCells.text(of: lines[row], columns: columns)
            return String(text.reversed().drop(while: { $0 == " " }).reversed())
        }
        .joined(separator: "\n")
    }
}
