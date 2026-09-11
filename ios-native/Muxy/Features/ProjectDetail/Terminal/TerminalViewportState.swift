import UIKit

struct TerminalViewportState {
    private(set) var keyboardOffset: CGFloat = 0
    private(set) var viewportOffset: CGFloat = 0
    private(set) var needsCursorPlacement = false

    private var followsCursor = false

    mutating func updateKeyboardOffset(_ offset: CGFloat) {
        let previousKeyboardOffset = keyboardOffset
        let wasAboveKeyboard = previousKeyboardOffset > 0
            && abs(viewportOffset - previousKeyboardOffset) <= 0.5
        keyboardOffset = max(0, offset)
        guard keyboardOffset > 0 else {
            viewportOffset = 0
            stopFollowingCursor()
            return
        }
        if previousKeyboardOffset == 0 {
            followCursor()
        }
        needsCursorPlacement = followsCursor
        guard !followsCursor else { return }
        viewportOffset = wasAboveKeyboard ? keyboardOffset : min(viewportOffset, keyboardOffset)
    }

    mutating func placeCursor(in frame: CGRect?, viewportHeight: CGFloat) {
        guard needsCursorPlacement, let frame, viewportHeight > 0 else { return }
        needsCursorPlacement = false
        viewportOffset = 0
        guard frame.maxY > 0, frame.minY < viewportHeight else { return }
        guard frame.maxY > viewportHeight - keyboardOffset else { return }
        viewportOffset = min(keyboardOffset, max(0, frame.minY))
    }

    mutating func followCursor() {
        followsCursor = true
        needsCursorPlacement = keyboardOffset > 0
    }

    mutating func stopFollowingCursor() {
        followsCursor = false
        needsCursorPlacement = false
    }

    mutating func captureRenderedOffset(_ offset: CGFloat) {
        viewportOffset = min(max(0, offset), keyboardOffset)
    }

    mutating func consume(_ delta: CGFloat) -> CGFloat {
        guard delta != 0 else { return delta }
        stopFollowingCursor()
        guard keyboardOffset > 0 else { return delta }
        let nextOffset = min(max(0, viewportOffset + delta), keyboardOffset)
        let consumed = nextOffset - viewportOffset
        guard consumed != 0 else { return delta }
        viewportOffset = nextOffset
        return delta - consumed
    }
}
