import CoreGraphics
import Testing
@testable import Muxy

@MainActor
struct TerminalViewportStateTests {
    private let bottomCursor = CGRect(x: 0, y: 580, width: 10, height: 20)

    @Test(arguments: [CGFloat(0), 100, 280])
    func aCursorAboveTheKeyboardDoesNotMoveTheViewport(y: CGFloat) {
        var state = TerminalViewportState()
        state.updateKeyboardOffset(300)
        state.placeCursor(in: CGRect(x: 0, y: y, width: 10, height: 20), viewportHeight: 600)
        #expect(state.viewportOffset == 0)
        #expect(!state.needsCursorPlacement)
    }

    @Test func aCoveredCursorShiftsTheViewportByTheKeyboardOverlap() {
        var state = TerminalViewportState()
        state.updateKeyboardOffset(300)
        state.placeCursor(in: bottomCursor, viewportHeight: 600)
        #expect(state.viewportOffset == 300)
    }

    @Test func cursorPlacementNeverPushesTheCursorAboveTheViewport() {
        var state = TerminalViewportState()
        state.updateKeyboardOffset(590)
        state.placeCursor(in: CGRect(x: 0, y: 5, width: 10, height: 20), viewportHeight: 600)
        #expect(state.viewportOffset == 5)
    }

    @Test(arguments: [CGFloat(-20), 600])
    func anOffscreenCursorDoesNotMoveTheViewport(y: CGFloat) {
        var state = TerminalViewportState()
        state.updateKeyboardOffset(300)
        state.placeCursor(in: CGRect(x: 0, y: y, width: 10, height: 20), viewportHeight: 600)
        #expect(state.viewportOffset == 0)
    }

    @Test func placementWaitsUntilTheCursorIsAvailable() {
        var state = TerminalViewportState()
        state.updateKeyboardOffset(300)
        state.placeCursor(in: nil, viewportHeight: 600)
        #expect(state.needsCursorPlacement)
        state.placeCursor(in: bottomCursor, viewportHeight: 600)
        #expect(state.viewportOffset == 300)
        #expect(!state.needsCursorPlacement)
    }

    @Test func hidingTheKeyboardResetsTheViewport() {
        var state = TerminalViewportState()
        state.updateKeyboardOffset(300)
        state.placeCursor(in: bottomCursor, viewportHeight: 600)
        state.updateKeyboardOffset(0)
        #expect(state.viewportOffset == 0)
        #expect(!state.needsCursorPlacement)
    }

    @Test func scrollingMovesTheViewportBeforeForwardingTheRemainder() {
        var state = TerminalViewportState()
        state.updateKeyboardOffset(300)
        #expect(state.consume(200) == 0)
        #expect(state.viewportOffset == 200)
        #expect(state.consume(150) == 50)
        #expect(state.viewportOffset == 300)
        #expect(state.consume(-350) == -50)
        #expect(state.viewportOffset == 0)
    }

    @Test func manualScrollingPreventsAutomaticCursorPlacement() {
        var state = TerminalViewportState()
        state.updateKeyboardOffset(300)
        state.placeCursor(in: bottomCursor, viewportHeight: 600)
        _ = state.consume(-200)
        state.placeCursor(in: bottomCursor, viewportHeight: 600)
        #expect(state.viewportOffset == 100)
        #expect(!state.needsCursorPlacement)
    }

    @Test func followingAgainRestoresCursorPlacementAfterManualScrolling() {
        var state = TerminalViewportState()
        state.updateKeyboardOffset(300)
        state.placeCursor(in: bottomCursor, viewportHeight: 600)
        _ = state.consume(-200)
        state.followCursor()
        state.placeCursor(in: bottomCursor, viewportHeight: 600)
        #expect(state.viewportOffset == 300)
    }

    @Test func reopeningTheKeyboardPlacesTheCursorAgain() {
        var state = TerminalViewportState()
        state.updateKeyboardOffset(300)
        _ = state.consume(100)
        state.updateKeyboardOffset(0)
        state.updateKeyboardOffset(300)
        #expect(state.needsCursorPlacement)
        state.placeCursor(in: bottomCursor, viewportHeight: 600)
        #expect(state.viewportOffset == 300)
    }

    @Test func keyboardHeightChangesPreserveAManualOffset() {
        var state = TerminalViewportState()
        state.updateKeyboardOffset(300)
        _ = state.consume(100)
        state.updateKeyboardOffset(350)
        #expect(state.viewportOffset == 100)
        #expect(!state.needsCursorPlacement)
        state.updateKeyboardOffset(50)
        #expect(state.viewportOffset == 50)
    }

    @Test func aFullyRaisedViewportTracksKeyboardHeightChanges() {
        var state = TerminalViewportState()
        state.updateKeyboardOffset(300)
        _ = state.consume(300)
        state.updateKeyboardOffset(350)
        #expect(state.viewportOffset == 350)
    }

    @Test func scrollingWithoutAKeyboardIsForwardedUnchanged() {
        var state = TerminalViewportState()
        #expect(state.consume(100) == 100)
        #expect(state.consume(-100) == -100)
        #expect(state.viewportOffset == 0)
    }
}
