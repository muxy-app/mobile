import UIKit

struct TerminalScrollGeometry {
    static func maxOffsetY(contentHeight: CGFloat, boundsHeight: CGFloat, bottomInset: CGFloat) -> CGFloat {
        max(0, contentHeight + bottomInset - boundsHeight)
    }

    static func maxOffsetY(for scrollView: UIScrollView) -> CGFloat {
        maxOffsetY(
            contentHeight: scrollView.contentSize.height,
            boundsHeight: scrollView.bounds.height,
            bottomInset: scrollView.adjustedContentInset.bottom
        )
    }

    static func keyboardOverlap(viewFrameInWindow: CGRect, keyboardFrameInWindow: CGRect) -> CGFloat {
        let intersection = viewFrameInWindow.intersection(keyboardFrameInWindow)
        guard !intersection.isNull else { return 0 }
        return intersection.height
    }

    static func caretRevealOffsetY(
        currentOffsetY: CGFloat,
        caretFrame: CGRect,
        boundsHeight: CGFloat,
        bottomInset: CGFloat,
        maxOffsetY: CGFloat
    ) -> CGFloat? {
        let visibleHeight = boundsHeight - bottomInset
        guard visibleHeight > 0, !caretFrame.isEmpty else { return nil }
        if caretFrame.minY < currentOffsetY {
            return clampedOffsetY(caretFrame.minY, maxOffsetY: maxOffsetY)
        }
        if caretFrame.maxY > currentOffsetY + visibleHeight {
            return clampedOffsetY(caretFrame.maxY - visibleHeight, maxOffsetY: maxOffsetY)
        }
        return nil
    }

    private static func clampedOffsetY(_ offsetY: CGFloat, maxOffsetY: CGFloat) -> CGFloat {
        min(max(0, offsetY), maxOffsetY)
    }
}
