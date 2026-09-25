import MuxyMobile
import UIKit

final class TerminalCanvasView: UIView {
    var renderer: TerminalRenderer {
        didSet {
            backgroundColor = UIColor(cgColor: renderer.backgroundColor)
            setNeedsDisplay()
        }
    }

    var origin: CGPoint = .zero {
        didSet {
            guard origin != oldValue else { return }
            setNeedsDisplay()
        }
    }

    var selection: TerminalSelection? {
        didSet {
            guard selection != oldValue else { return }
            setNeedsDisplay()
        }
    }

    var markedText: String? {
        didSet {
            guard markedText != oldValue, let cursor else { return }
            invalidate(row: cursor.row)
        }
    }

    private(set) var lines: [Line] = []
    private(set) var columnCount = 0
    private(set) var cursor: TerminalCursorMark?

    init(renderer: TerminalRenderer) {
        self.renderer = renderer
        super.init(frame: .zero)
        isOpaque = true
        isUserInteractionEnabled = false
        contentMode = .redraw
        backgroundColor = UIColor(cgColor: renderer.backgroundColor)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        nil
    }

    func update(lines: [Line], columnCount: Int, cursor: TerminalCursorMark?) {
        let previousLines = self.lines
        let previousCursor = self.cursor
        self.lines = lines
        self.columnCount = columnCount
        self.cursor = cursor

        guard previousLines.count == lines.count else {
            setNeedsDisplay()
            return
        }
        for row in lines.indices where previousLines[row] != lines[row] {
            invalidate(row: row)
        }
        guard previousCursor != cursor else { return }
        if let previousCursor {
            invalidate(row: previousCursor.row)
        }
        if let cursor {
            invalidate(row: cursor.row)
        }
    }

    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext() else { return }
        context.setFillColor(renderer.backgroundColor)
        context.fill(rect)

        let rows = renderer.rows(in: rect, origin: origin, rowCount: lines.count)
        for row in rows {
            renderer.drawBackgrounds(of: lines[row], row: row, origin: origin, in: context)
        }
        if let selection {
            for row in rows {
                guard let columns = selection.columns(inRow: row, columnCount: columnCount) else { continue }
                renderer.drawSelection(columns: columns, row: row, origin: origin, in: context)
            }
        }
        for row in rows {
            renderer.drawText(of: lines[row], row: row, origin: origin, in: context)
        }
        drawCursor(in: rect, context: context)
    }

    private func drawCursor(in rect: CGRect, context: CGContext) {
        guard let cursor else { return }
        guard renderer.rowRect(cursor.row, origin: origin, width: bounds.width).intersects(rect) else { return }
        if let markedText, !markedText.isEmpty {
            renderer.drawMarkedText(markedText, at: cursor, origin: origin, in: context)
            return
        }
        let line = lines.indices.contains(cursor.row) ? lines[cursor.row] : nil
        renderer.drawCursor(cursor, on: line, origin: origin, in: context)
    }

    private func invalidate(row: Int) {
        setNeedsDisplay(renderer.rowRect(row, origin: origin, width: bounds.width))
    }
}
