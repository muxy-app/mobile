import CoreGraphics

nonisolated enum TerminalScrollMath {
    static func offset(revealing rect: CGRect, visibleSize: CGSize, contentSize: CGSize, current: CGPoint) -> CGPoint {
        var x = current.x
        var y = current.y
        if rect.minX < x {
            x = rect.minX
        } else if rect.maxX > x + visibleSize.width {
            x = rect.maxX - visibleSize.width
        }
        if rect.minY < y {
            y = rect.minY
        } else if rect.maxY > y + visibleSize.height {
            y = rect.maxY - visibleSize.height
        }
        return clamped(CGPoint(x: x, y: y), visibleSize: visibleSize, contentSize: contentSize)
    }

    static func bottomOffset(visibleSize: CGSize, contentSize: CGSize, current: CGPoint) -> CGPoint {
        clamped(CGPoint(x: current.x, y: contentSize.height - visibleSize.height), visibleSize: visibleSize, contentSize: contentSize)
    }

    static func isAtBottom(offset: CGPoint, visibleSize: CGSize, contentSize: CGSize, tolerance: CGFloat) -> Bool {
        offset.y >= max(0, contentSize.height - visibleSize.height) - tolerance
    }

    static func isVisible(_ rect: CGRect, offset: CGPoint, visibleSize: CGSize, tolerance: CGSize) -> Bool {
        CGRect(origin: offset, size: visibleSize)
            .insetBy(dx: -tolerance.width, dy: -tolerance.height)
            .contains(rect)
    }

    private static func clamped(_ point: CGPoint, visibleSize: CGSize, contentSize: CGSize) -> CGPoint {
        let maxX = max(0, contentSize.width - visibleSize.width)
        let maxY = max(0, contentSize.height - visibleSize.height)
        return CGPoint(x: min(max(0, point.x), maxX), y: min(max(0, point.y), maxY))
    }
}
