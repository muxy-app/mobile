import CoreGraphics
import Testing
@testable import Muxy

struct KeyboardOverlapTests {
    private let bounds = CGRect(x: 0, y: 0, width: 390, height: 600)

    @Test func noKeyboardMeansNoOverlap() {
        #expect(TerminalViewportState.keyboardOverlap(in: bounds, keyboardFrame: .zero) == 0)
    }

    @Test func keyboardBelowTheTerminalDoesNotOverlap() {
        let keyboard = CGRect(x: 0, y: 620, width: 390, height: 300)

        #expect(TerminalViewportState.keyboardOverlap(in: bounds, keyboardFrame: keyboard) == 0)
    }

    @Test func overlapIncludesTheGapAndContentInset() {
        let keyboard = CGRect(x: 0, y: 500, width: 390, height: 300)

        #expect(TerminalViewportState.keyboardOverlap(in: bounds, keyboardFrame: keyboard) == 100 + TerminalViewportState.keyboardSpacing)
    }

    @Test func overlapNeverExceedsTheTerminalHeight() {
        let keyboard = CGRect(x: 0, y: -50, width: 390, height: 900)

        #expect(TerminalViewportState.keyboardOverlap(in: bounds, keyboardFrame: keyboard) == bounds.height)
    }
}
