import CoreGraphics
import Testing
@testable import Muxy

struct TerminalMetricsTests {
    private let metrics = TerminalMetrics(fontSize: 13, useNerdFont: true, scale: 3)

    @Test func cellsArePixelAligned() {
        #expect(metrics.cellWidth > 0)
        #expect(metrics.cellHeight > metrics.cellWidth)
        #expect((metrics.cellWidth * 3).rounded() == metrics.cellWidth * 3)
        #expect((metrics.cellHeight * 3).rounded() == metrics.cellHeight * 3)
        #expect(metrics.baseline > 0 && metrics.baseline < metrics.cellHeight)
    }

    @Test func gridSizeCountsWholeCells() throws {
        let size = CGSize(width: metrics.cellWidth * 40.6, height: metrics.cellHeight * 20.9)
        let grid = try #require(metrics.gridSize(fitting: size))
        #expect(grid == TerminalGridSize(columns: 40, rows: 20))
    }

    @Test func tooSmallAViewportHasNoGrid() {
        #expect(metrics.gridSize(fitting: CGSize(width: metrics.cellWidth * 9, height: 500)) == nil)
        #expect(metrics.gridSize(fitting: CGSize(width: 500, height: metrics.cellHeight * 2)) == nil)
    }

    @Test func cellRectsSpanTheirWidth() {
        let rect = metrics.cellRect(row: 2, column: 3, width: 2)
        #expect(rect == CGRect(x: metrics.cellWidth * 3, y: metrics.cellHeight * 2, width: metrics.cellWidth * 2, height: metrics.cellHeight))
    }

    @Test func largerFontsMakeLargerCells() {
        let large = TerminalMetrics(fontSize: 20, useNerdFont: true, scale: 3)
        #expect(large.cellWidth > metrics.cellWidth)
        #expect(large.cellHeight > metrics.cellHeight)
    }
}
