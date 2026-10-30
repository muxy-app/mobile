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

    @Test func aPartlyCoveredCursorShiftsOnlyEnoughToClearTheKeyboard() {
        var state = TerminalViewportState()
        state.updateKeyboardOffset(300)
        state.placeCursor(in: CGRect(x: 0, y: 320, width: 10, height: 20), viewportHeight: 600)
        #expect(state.viewportOffset == 40)
    }

    @Test func aVisibleCursorKeepsTheCurrentOffset() {
        var state = TerminalViewportState()
        state.updateKeyboardOffset(300)
        state.placeCursor(in: CGRect(x: 0, y: 320, width: 10, height: 20), viewportHeight: 600)
        state.placeCursor(in: CGRect(x: 0, y: 200, width: 10, height: 20), viewportHeight: 600)
        #expect(state.viewportOffset == 40)
    }

    @Test func aCursorAboveTheVisibleAreaScrollsDownJustEnough() {
        var state = TerminalViewportState()
        state.updateKeyboardOffset(300)
        state.placeCursor(in: bottomCursor, viewportHeight: 600)
        state.placeCursor(in: CGRect(x: 0, y: 100, width: 10, height: 20), viewportHeight: 600)
        #expect(state.viewportOffset == 100)
    }

    @Test func steppingTheCursorPastTheKeyboardMovesOneRowAtATime() {
        var state = TerminalViewportState()
        state.updateKeyboardOffset(300)
        let offsets = (14...17).map { row -> CGFloat in
            state.placeCursor(in: CGRect(x: 0, y: CGFloat(row) * 20, width: 10, height: 20), viewportHeight: 600)
            return state.viewportOffset
        }
        #expect(offsets == [0, 20, 40, 60])
        state.placeCursor(in: CGRect(x: 0, y: 280, width: 10, height: 20), viewportHeight: 600)
        #expect(state.viewportOffset == 60)
    }

    @Test func followingKeepsTheCursorVisibleAcrossScreenUpdates() {
        var state = TerminalViewportState()
        state.updateKeyboardOffset(300)
        state.placeCursor(in: CGRect(x: 0, y: 320, width: 10, height: 20), viewportHeight: 600)
        #expect(state.needsCursorPlacement)
        state.placeCursor(in: bottomCursor, viewportHeight: 600)
        #expect(state.viewportOffset == 300)
    }

    @Test func aShrinkingKeyboardClampsTheOffsetWhileFollowing() {
        var state = TerminalViewportState()
        state.updateKeyboardOffset(300)
        state.placeCursor(in: bottomCursor, viewportHeight: 600)
        state.updateKeyboardOffset(100)
        #expect(state.viewportOffset == 100)
    }
}
