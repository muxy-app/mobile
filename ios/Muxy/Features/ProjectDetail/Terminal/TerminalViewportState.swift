import UIKit

struct TerminalViewportState {
    private(set) var keyboardOffset: CGFloat = 0
    private(set) var viewportOffset: CGFloat = 0

    private var followsCursor = false

    var needsCursorPlacement: Bool {
        followsCursor && keyboardOffset > 0
    }

    static let contentInset: CGFloat = 8
    static let keyboardGap: CGFloat = 10
    static let keyboardSpacing = keyboardGap + contentInset
    static let pageOutset = PageMargin.horizontal + contentInset

    static func keyboardOverlap(in bounds: CGRect, keyboardFrame: CGRect) -> CGFloat {
        guard keyboardFrame != .zero else { return 0 }
        let overlap = bounds.maxY - keyboardFrame.minY
        guard overlap > 0 else { return 0 }
        return min(bounds.height, overlap + keyboardSpacing)
    }

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
        let raisedOffset = wasAboveKeyboard && !followsCursor ? keyboardOffset : viewportOffset
        viewportOffset = min(raisedOffset, keyboardOffset)
    }

    mutating func placeCursor(in frame: CGRect?, viewportHeight: CGFloat) {
        guard needsCursorPlacement, let frame, viewportHeight > 0 else { return }
        viewportOffset = min(viewportOffset, keyboardOffset)
        guard frame.maxY > 0, frame.minY < viewportHeight else { return }
        viewportOffset = Self.revealingOffset(
            for: frame,
            current: viewportOffset,
            visibleHeight: viewportHeight - keyboardOffset,
            limit: keyboardOffset
        )
    }

    static func revealingOffset(for frame: CGRect, current: CGFloat, visibleHeight: CGFloat, limit: CGFloat) -> CGFloat {
        let clearingKeyboard = max(current, frame.maxY - visibleHeight)
        let showingTop = min(clearingKeyboard, frame.minY)
        return min(max(0, showingTop), limit)
    }

    mutating func followCursor() {
        followsCursor = true
    }

    mutating func stopFollowingCursor() {
        followsCursor = false
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
