import UIKit

struct TerminalViewportState {
    private(set) var keyboardOffset: CGFloat = 0
    private(set) var viewportOffset: CGFloat = 0

    private var viewportOffsetLimit: CGFloat = 0

    mutating func updateKeyboardOffset(_ offset: CGFloat) {
        keyboardOffset = max(0, offset)
        if keyboardOffset > 0 {
            viewportOffsetLimit = max(viewportOffset, keyboardOffset)
            return
        }
        if viewportOffset == 0 {
            viewportOffsetLimit = 0
        }
    }

    mutating func captureRenderedOffset(_ offset: CGFloat) {
        viewportOffset = min(max(0, offset), viewportOffsetLimit)
    }

    mutating func consume(_ delta: CGFloat) -> CGFloat {
        guard viewportOffsetLimit > 0, delta != 0 else { return delta }
        let nextOffset = min(max(0, viewportOffset + delta), viewportOffsetLimit)
        let consumed = nextOffset - viewportOffset
        guard consumed != 0 else { return delta }
        viewportOffset = nextOffset
        reconcileViewportOffsetLimit()
        return delta - consumed
    }

    private mutating func reconcileViewportOffsetLimit() {
        if keyboardOffset > 0, viewportOffset <= keyboardOffset {
            viewportOffsetLimit = keyboardOffset
            return
        }
        if keyboardOffset == 0, viewportOffset == 0 {
            viewportOffsetLimit = 0
        }
    }
}
