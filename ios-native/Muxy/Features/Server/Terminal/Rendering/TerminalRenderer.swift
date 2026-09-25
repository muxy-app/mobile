import CoreText
import MuxyMobile
import UIKit

nonisolated struct TerminalCursorMark: Equatable {
    let row: Int
    let column: Int
    let shape: CursorShape
}

final class TerminalRenderer {
    let metrics: TerminalMetrics
    let resolver: TerminalColorResolver

    private var colors: [UInt32: CGColor] = [:]
    private var clusterLines: [ClusterKey: CTLine] = [:]

    private static let clusterCacheLimit = 2048
    private static let colorCacheLimit = 4096

    init(metrics: TerminalMetrics, theme: ThemePalette) {
        self.metrics = metrics
        resolver = TerminalColorResolver(theme: theme)
    }

    var backgroundColor: CGColor {
        color(resolver.theme.background)
    }

    func rows(in rect: CGRect, origin: CGPoint, rowCount: Int) -> Range<Int> {
        guard rowCount > 0, metrics.cellHeight > 0 else { return 0..<0 }
        let first = max(0, Int(((rect.minY + origin.y) / metrics.cellHeight).rounded(.down)))
        let last = min(rowCount, Int(((rect.maxY + origin.y) / metrics.cellHeight).rounded(.up)))
        return first < last ? first..<last : 0..<0
    }

    func rowRect(_ row: Int, origin: CGPoint, width: CGFloat) -> CGRect {
        CGRect(x: 0, y: CGFloat(row) * metrics.cellHeight - origin.y, width: width, height: metrics.cellHeight)
    }

    func drawBackgrounds(of line: Line, row: Int, origin: CGPoint, in context: CGContext) {
        forEachSpan(of: line, row: row, origin: origin) { span, rect in
            let style = resolver.resolve(span.style)
            guard style.background != resolver.theme.background else { return }
            context.setFillColor(color(style.background))
            context.fill(rect)
        }
    }

    func drawText(of line: Line, row: Int, origin: CGPoint, in context: CGContext) {
        forEachSpan(of: line, row: row, origin: origin) { span, rect in
            let style = resolver.resolve(span.style)
            guard style.drawsText else { return }
            if !Self.isBlank(span.text) {
                let paint = TextPaint(font: font(for: span.style), color: color(style.foreground))
                draw(span.text, cells: Int(span.width), in: rect, paint: paint, context: context)
            }
            drawDecorations(of: span.style, in: rect, color: color(style.decoration), context: context)
        }
    }

    func drawSelection(columns: Range<Int>, row: Int, origin: CGPoint, in context: CGContext) {
        let rect = metrics.cellRect(row: row, column: columns.lowerBound, width: columns.count)
            .offsetBy(dx: -origin.x, dy: -origin.y)
        context.setFillColor(color(resolver.theme.selectionBackground).copy(alpha: 0.35) ?? color(resolver.theme.selectionBackground))
        context.fill(rect)
    }

    func drawCursor(_ cursor: TerminalCursorMark, on line: Line?, origin: CGPoint, in context: CGContext) {
        let cell = line.flatMap { TerminalLineCells.cell(at: cursor.column, in: $0) }
        let rect = metrics.cellRect(row: cursor.row, column: cursor.column, width: cell?.width ?? 1)
            .offsetBy(dx: -origin.x, dy: -origin.y)
        let cursorColor = color(resolver.theme.cursor)
        context.setFillColor(cursorColor)
        switch cursor.shape {
        case .block:
            context.fill(rect)
            guard let cell, !Self.isBlank(cell.text) else { return }
            let paint = TextPaint(font: font(for: cell.style), color: color(resolver.theme.cursorText))
            draw(cell.text, cells: cell.width, in: rect, paint: paint, context: context)
        case .bar:
            context.fill(CGRect(x: rect.minX, y: rect.minY, width: max(2, metrics.cellWidth * 0.15), height: rect.height))
        case .underline:
            context.fill(CGRect(x: rect.minX, y: rect.maxY - 2, width: rect.width, height: 2))
        case .hollow:
            context.setStrokeColor(cursorColor)
            context.setLineWidth(1)
            context.stroke(rect.insetBy(dx: 0.5, dy: 0.5))
        }
    }

    func drawMarkedText(_ text: String, at cursor: TerminalCursorMark, origin: CGPoint, in context: CGContext) {
        let line = clusterLine(for: text, font: metrics.fonts.normal as CTFont)
        let width = max(metrics.cellWidth, CGFloat(CTLineGetTypographicBounds(line, nil, nil, nil)))
        let cell = metrics.cellRect(row: cursor.row, column: cursor.column).offsetBy(dx: -origin.x, dy: -origin.y)
        let rect = CGRect(x: cell.minX, y: cell.minY, width: width, height: cell.height)
        context.setFillColor(backgroundColor)
        context.fill(rect)
        let foreground = color(resolver.theme.foreground)
        context.saveGState()
        context.setFillColor(foreground)
        context.textMatrix = .identity
        context.translateBy(x: rect.minX, y: rect.minY + metrics.baseline)
        context.scaleBy(x: 1, y: -1)
        context.textPosition = .zero
        CTLineDraw(line, context)
        context.restoreGState()
        strokeLine(y: rect.minY + metrics.baseline + metrics.underlineOffset, from: rect.minX, to: rect.maxX, color: foreground, context: context)
    }

    private func forEachSpan(of line: Line, row: Int, origin: CGPoint, _ body: (Span, CGRect) -> Void) {
        var column = 0
        for span in line.spans {
            let width = Int(span.width)
            let rect = metrics.cellRect(row: row, column: column, width: width).offsetBy(dx: -origin.x, dy: -origin.y)
            column += width
            guard width > 0 else { continue }
            body(span, rect)
        }
    }

    private func draw(_ text: String, cells: Int, in rect: CGRect, paint: TextPaint, context: CGContext) {
        if text.utf16.count == cells, text.allSatisfy(\.isASCII), drawGlyphs(text, in: rect, paint: paint, context: context) {
            return
        }
        drawCluster(text, in: rect, paint: paint, context: context)
    }

    private func drawGlyphs(_ text: String, in rect: CGRect, paint: TextPaint, context: CGContext) -> Bool {
        var characters = Array(text.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: characters.count)
        guard CTFontGetGlyphsForCharacters(paint.font, &characters, &glyphs, characters.count) else { return false }
        let positions = characters.indices.map { CGPoint(x: CGFloat($0) * metrics.cellWidth, y: 0) }
        context.saveGState()
        context.setFillColor(paint.color)
        context.textMatrix = .identity
        context.translateBy(x: rect.minX, y: rect.minY + metrics.baseline)
        context.scaleBy(x: 1, y: -1)
        CTFontDrawGlyphs(paint.font, glyphs, positions, glyphs.count, context)
        context.restoreGState()
        return true
    }

    private func drawCluster(_ text: String, in rect: CGRect, paint: TextPaint, context: CGContext) {
        let line = clusterLine(for: text, font: paint.font)
        let width = CGFloat(CTLineGetTypographicBounds(line, nil, nil, nil))
        let scale = width > rect.width && width > 0 ? rect.width / width : 1
        context.saveGState()
        context.clip(to: rect)
        context.setFillColor(paint.color)
        context.textMatrix = .identity
        context.translateBy(x: rect.minX + (rect.width - width * scale) / 2, y: rect.minY + metrics.baseline)
        context.scaleBy(x: scale, y: -scale)
        context.textPosition = .zero
        CTLineDraw(line, context)
        context.restoreGState()
    }

    private func drawDecorations(of style: Style, in rect: CGRect, color: CGColor, context: CGContext) {
        let underlineY = rect.minY + metrics.baseline + metrics.underlineOffset
        switch style.underline {
        case .none:
            break
        case .single:
            strokeLine(y: underlineY, from: rect.minX, to: rect.maxX, color: color, context: context)
        case .double:
            strokeLine(y: underlineY - metrics.lineThickness, from: rect.minX, to: rect.maxX, color: color, context: context)
            strokeLine(y: underlineY + metrics.lineThickness, from: rect.minX, to: rect.maxX, color: color, context: context)
        case .curly:
            strokeCurl(y: underlineY, from: rect.minX, to: rect.maxX, color: color, context: context)
        case .dotted:
            strokeLine(y: underlineY, from: rect.minX, to: rect.maxX, color: color, context: context, dash: [metrics.lineThickness, metrics.lineThickness * 2])
        case .dashed:
            strokeLine(y: underlineY, from: rect.minX, to: rect.maxX, color: color, context: context, dash: [metrics.lineThickness * 4, metrics.lineThickness * 2])
        }
        if style.strikethrough {
            strokeLine(y: rect.minY + metrics.baseline - metrics.strikeOffset, from: rect.minX, to: rect.maxX, color: color, context: context)
        }
        if style.overline {
            strokeLine(y: rect.minY + metrics.lineThickness / 2, from: rect.minX, to: rect.maxX, color: color, context: context)
        }
    }

    private func strokeLine(y: CGFloat, from start: CGFloat, to end: CGFloat, color: CGColor, context: CGContext, dash: [CGFloat] = []) {
        context.saveGState()
        context.setStrokeColor(color)
        context.setLineWidth(metrics.lineThickness)
        context.setLineDash(phase: 0, lengths: dash)
        context.move(to: CGPoint(x: start, y: y))
        context.addLine(to: CGPoint(x: end, y: y))
        context.strokePath()
        context.restoreGState()
    }

    private func strokeCurl(y: CGFloat, from start: CGFloat, to end: CGFloat, color: CGColor, context: CGContext) {
        let amplitude = max(metrics.lineThickness, 1.5)
        let wavelength = max(metrics.cellWidth / 2, 3)
        context.saveGState()
        context.setStrokeColor(color)
        context.setLineWidth(metrics.lineThickness)
        context.move(to: CGPoint(x: start, y: y))
        var x = start
        var direction: CGFloat = -1
        while x < end {
            let next = min(x + wavelength, end)
            context.addQuadCurve(to: CGPoint(x: next, y: y), control: CGPoint(x: (x + next) / 2, y: y + amplitude * direction))
            direction = -direction
            x = next
        }
        context.strokePath()
        context.restoreGState()
    }

    private func font(for style: Style) -> CTFont {
        switch (style.bold, style.italic) {
        case (true, true):
            return metrics.fonts.boldItalic as CTFont
        case (true, false):
            return metrics.fonts.bold as CTFont
        case (false, true):
            return metrics.fonts.italic as CTFont
        case (false, false):
            return metrics.fonts.normal as CTFont
        }
    }

    private func color(_ rgb: UInt32) -> CGColor {
        if let cached = colors[rgb] { return cached }
        if colors.count >= Self.colorCacheLimit {
            colors.removeAll(keepingCapacity: true)
        }
        let created = CGColor(
            srgbRed: CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255,
            alpha: 1
        )
        colors[rgb] = created
        return created
    }

    private func clusterLine(for text: String, font: CTFont) -> CTLine {
        let key = ClusterKey(text: text, fontName: CTFontCopyPostScriptName(font) as String)
        if let cached = clusterLines[key] { return cached }
        if clusterLines.count >= Self.clusterCacheLimit {
            clusterLines.removeAll(keepingCapacity: true)
        }
        let attributes: [NSAttributedString.Key: Any] = [
            NSAttributedString.Key(kCTFontAttributeName as String): font,
            NSAttributedString.Key(kCTForegroundColorFromContextAttributeName as String): true,
            NSAttributedString.Key(kCTLigatureAttributeName as String): 0,
        ]
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: attributes))
        clusterLines[key] = line
        return line
    }

    private static func isBlank(_ text: String) -> Bool {
        text.allSatisfy { $0 == " " }
    }

    private struct TextPaint {
        let font: CTFont
        let color: CGColor
    }

    private struct ClusterKey: Hashable {
        let text: String
        let fontName: String
    }
}
