import Foundation
import MuxyMobile

nonisolated struct TerminalCellContent: Equatable {
    let text: String
    let width: Int
    let style: Style
}

nonisolated enum TerminalLineCells {
    static func cell(at column: Int, in line: Line) -> TerminalCellContent? {
        var start = 0
        for span in line.spans {
            let width = Int(span.width)
            defer { start += width }
            guard column >= start, column < start + width else { continue }
            let characters = Array(span.text)
            guard characters.count == width else {
                return TerminalCellContent(text: span.text, width: width, style: span.style)
            }
            return TerminalCellContent(text: String(characters[column - start]), width: 1, style: span.style)
        }
        return nil
    }

    static func text(of line: Line, columns: Range<Int>) -> String {
        var result = ""
        var start = 0
        for span in line.spans {
            let width = Int(span.width)
            defer { start += width }
            let spanColumns = start..<(start + width)
            guard spanColumns.overlaps(columns) else { continue }
            let characters = Array(span.text)
            guard characters.count == width else {
                result += span.text
                continue
            }
            let lower = max(columns.lowerBound, start) - start
            let upper = min(columns.upperBound, start + width) - start
            result += String(characters[lower..<upper])
        }
        return result
    }
}
