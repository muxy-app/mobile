import CoreText
import UIKit

nonisolated struct TerminalMetrics {
    static let minimumColumns = 10
    static let minimumRows = 3

    let fonts: TerminalFont.Faces
    let cellWidth: CGFloat
    let cellHeight: CGFloat
    let baseline: CGFloat
    let underlineOffset: CGFloat
    let lineThickness: CGFloat
    let strikeOffset: CGFloat

    init(fontSize: CGFloat, useNerdFont: Bool, scale: CGFloat) {
        fonts = TerminalFont.faces(size: fontSize, useNerdFont: useNerdFont)
        let font = fonts.normal as CTFont
        let ascent = CTFontGetAscent(font)
        let descent = CTFontGetDescent(font)
        cellWidth = Self.roundToPixel(Self.advance(of: "M", in: font), scale: scale)
        cellHeight = Self.ceilToPixel(ascent + descent, scale: scale)
        baseline = Self.roundToPixel(ascent + (cellHeight - ascent - descent) / 2, scale: scale)
        underlineOffset = max(1 / scale, -CTFontGetUnderlinePosition(font))
        lineThickness = max(1 / scale, CTFontGetUnderlineThickness(font))
        strikeOffset = CTFontGetXHeight(font) / 2
    }

    func gridSize(fitting size: CGSize) -> TerminalGridSize? {
        guard cellWidth > 0, cellHeight > 0 else { return nil }
        let columns = Int(size.width / cellWidth)
        let rows = Int(size.height / cellHeight)
        guard columns >= Self.minimumColumns, rows >= Self.minimumRows else { return nil }
        return TerminalGridSize(
            columns: UInt16(min(columns, Int(UInt16.max))),
            rows: UInt16(min(rows, Int(UInt16.max)))
        )
    }

    func cellRect(row: Int, column: Int, width: Int = 1) -> CGRect {
        CGRect(
            x: CGFloat(column) * cellWidth,
            y: CGFloat(row) * cellHeight,
            width: CGFloat(width) * cellWidth,
            height: cellHeight
        )
    }

    private static func advance(of character: Character, in font: CTFont) -> CGFloat {
        var characters = Array(String(character).utf16)
        var glyphs = [CGGlyph](repeating: 0, count: characters.count)
        guard CTFontGetGlyphsForCharacters(font, &characters, &glyphs, characters.count) else {
            return CTFontGetSize(font) * 0.6
        }
        var advance = CGSize.zero
        CTFontGetAdvancesForGlyphs(font, .horizontal, &glyphs, &advance, 1)
        return advance.width
    }

    private static func roundToPixel(_ value: CGFloat, scale: CGFloat) -> CGFloat {
        (value * scale).rounded() / scale
    }

    private static func ceilToPixel(_ value: CGFloat, scale: CGFloat) -> CGFloat {
        (value * scale).rounded(.up) / scale
    }
}
