import UIKit

struct TerminalViewportState {
    private(set) var keyboardOffset: CGFloat = 0
    private(set) var viewportOffset: CGFloat = 0

    private var preferredViewportOffset: CGFloat = 0
    private var followsKeyboardTop = true

    mutating func updateKeyboardOffset(_ offset: CGFloat) {
        keyboardOffset = max(0, offset)
        viewportOffset = followsKeyboardTop
            ? keyboardOffset
            : min(preferredViewportOffset, keyboardOffset)
    }

    mutating func captureRenderedOffset(_ offset: CGFloat) {
        viewportOffset = min(max(0, offset), keyboardOffset)
        preferredViewportOffset = viewportOffset
        followsKeyboardTop = keyboardOffset > 0 && abs(viewportOffset - keyboardOffset) <= 0.5
    }

    mutating func consume(_ delta: CGFloat) -> CGFloat {
        guard keyboardOffset > 0, delta != 0 else { return delta }
        let nextOffset = min(max(0, viewportOffset + delta), keyboardOffset)
        let consumed = nextOffset - viewportOffset
        guard consumed != 0 else { return delta }
        viewportOffset = nextOffset
        preferredViewportOffset = nextOffset
        followsKeyboardTop = abs(nextOffset - keyboardOffset) <= 0.5
        return delta - consumed
    }

    mutating func resetAfterKeyboardHide() {
        followsKeyboardTop = true
        preferredViewportOffset = 0
        viewportOffset = keyboardOffset
    }
}
