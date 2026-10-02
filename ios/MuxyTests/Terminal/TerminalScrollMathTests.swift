import CoreGraphics
import Testing
@testable import Muxy

struct TerminalScrollMathTests {
    private let visible = CGSize(width: 100, height: 100)
    private let content = CGSize(width: 400, height: 300)

    @Test func aVisibleRectDoesNotScroll() {
        let offset = TerminalScrollMath.offset(revealing: CGRect(x: 10, y: 10, width: 8, height: 16), visibleSize: visible, contentSize: content, current: .zero)
        #expect(offset == .zero)
    }

    @Test func aRectBelowScrollsJustEnough() {
        let offset = TerminalScrollMath.offset(revealing: CGRect(x: 0, y: 150, width: 8, height: 16), visibleSize: visible, contentSize: content, current: .zero)
        #expect(offset == CGPoint(x: 0, y: 66))
    }

    @Test func aRectToTheRightScrollsSideways() {
        let offset = TerminalScrollMath.offset(revealing: CGRect(x: 250, y: 0, width: 8, height: 16), visibleSize: visible, contentSize: content, current: .zero)
        #expect(offset == CGPoint(x: 158, y: 0))
    }

    @Test func aRectAboveScrollsBackUp() {
        let offset = TerminalScrollMath.offset(revealing: CGRect(x: 0, y: 20, width: 8, height: 16), visibleSize: visible, contentSize: content, current: CGPoint(x: 0, y: 120))
        #expect(offset == CGPoint(x: 0, y: 20))
    }

    @Test func contentSmallerThanTheViewportNeverScrolls() {
        let small = CGSize(width: 50, height: 50)
        let offset = TerminalScrollMath.offset(revealing: CGRect(x: 40, y: 40, width: 8, height: 16), visibleSize: visible, contentSize: small, current: .zero)
        #expect(offset == .zero)
    }

    @Test func bottomOffsetKeepsTheHorizontalPosition() {
        let offset = TerminalScrollMath.bottomOffset(visibleSize: visible, contentSize: content, current: CGPoint(x: 30, y: 0))
        #expect(offset == CGPoint(x: 30, y: 200))
    }

    @Test func visibilityAllowsATolerance() {
        let rect = CGRect(x: 0, y: 95, width: 8, height: 10)
        #expect(!TerminalScrollMath.isVisible(rect, offset: .zero, visibleSize: visible, tolerance: .zero))
        #expect(TerminalScrollMath.isVisible(rect, offset: .zero, visibleSize: visible, tolerance: CGSize(width: 4, height: 8)))
    }

    @Test func theLastRowCountsAsVisibleDespiteRounding() {
        let cellHeight: CGFloat = 47.0 / 3.0
        let rows: CGFloat = 22
        let content = CGSize(width: 300, height: rows * cellHeight)
        let viewport = CGSize(width: 300, height: cellHeight * 10)
        let bottom = TerminalScrollMath.bottomOffset(visibleSize: viewport, contentSize: content, current: .zero)
        let lastRow = CGRect(x: 0, y: (rows - 1) * cellHeight, width: 8, height: cellHeight)
        #expect(TerminalScrollMath.isVisible(lastRow, offset: bottom, visibleSize: viewport, tolerance: CGSize(width: 4, height: cellHeight / 2)))
    }

    @Test func atBottomAllowsATolerance() {
        #expect(TerminalScrollMath.isAtBottom(offset: CGPoint(x: 0, y: 195), visibleSize: visible, contentSize: content, tolerance: 8))
        #expect(!TerminalScrollMath.isAtBottom(offset: CGPoint(x: 0, y: 150), visibleSize: visible, contentSize: content, tolerance: 8))
    }
}
