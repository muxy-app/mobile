import Foundation
import SwiftTerm
import UIKit

@MainActor
struct TerminalOutputFeeder {
    private static let followEpsilon = 0.001

    static func isFollowingBottom(_ position: Double) -> Bool {
        position >= 1 - followEpsilon
    }

    static func applySnapshot(_ bytes: Data, into view: TerminalView) {
        view.getTerminal().resetToInitialState()
        feed(bytes, into: view)
        view.scroll(toPosition: 1)
    }

    @discardableResult
    static func feedFollowingBottom(_ bytes: Data, into view: TerminalView, isFollowingBottom: Bool) -> Bool {
        let shouldFollowBottom = isFollowingBottom && TerminalScrollOffset.isAtBottom(view)
        if shouldFollowBottom {
            feed(bytes, into: view)
            view.scroll(toPosition: 1)
            return true
        }

        let scrollOffset = TerminalScrollOffset.capture(from: view)
        feed(bytes, into: view)
        if !TerminalScrollOffset.isInteracting(with: view) {
            scrollOffset.restore(on: view)
        }
        return false
    }

    static func scrollToBottom(_ view: TerminalView) {
        TerminalScrollOffset.scrollToBottom(view)
    }

    private static func feed(_ bytes: Data, into view: TerminalView) {
        if let view = view as? FollowAwareTerminalView {
            view.preserveInteractiveOffsetDuringTerminalUpdate {
                view.feed(byteArray: ArraySlice(bytes))
            }
            view.scheduleCaretVisibilityCheck()
            return
        }
        view.feed(byteArray: ArraySlice(bytes))
    }
}

@MainActor
struct TerminalScrollOffset {
    private static let bottomTolerance: CGFloat = 2
    private static let minimumDetachedDistance: CGFloat = 0

    let y: CGFloat

    static func capture(from view: TerminalView) -> TerminalScrollOffset {
        let maxOffset = TerminalScrollGeometry.maxOffsetY(for: view)
        let detachedY = min(view.contentOffset.y, max(0, maxOffset - minimumDetachedDistance))
        return TerminalScrollOffset(y: max(0, detachedY))
    }

    static func isAtBottom(_ view: TerminalView) -> Bool {
        view.contentOffset.y >= TerminalScrollGeometry.maxOffsetY(for: view) - bottomTolerance
    }

    static func scrollToBottom(_ view: TerminalView) {
        (view as? FollowAwareTerminalView)?.resumeCaretFollow()
        view.scroll(toPosition: 1)
        view.contentOffset = CGPoint(x: view.contentOffset.x, y: TerminalScrollGeometry.maxOffsetY(for: view))
    }

    static func isInteracting(with view: TerminalView) -> Bool {
        view.isTracking || view.isDragging || view.isDecelerating
    }

    func restore(on view: TerminalView) {
        let maxOffset = TerminalScrollGeometry.maxOffsetY(for: view)
        guard maxOffset > 0 else { return }
        let y = min(y, maxOffset)
        let position = Double(y / maxOffset)
        view.scroll(toPosition: position)
        view.contentOffset = CGPoint(x: view.contentOffset.x, y: y)
    }
}
