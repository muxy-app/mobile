import CoreGraphics

nonisolated struct TerminalWheelAccumulator {
    private var pending: CGFloat = 0

    mutating func reset() {
        pending = 0
    }

    mutating func steps(for delta: CGFloat, rowHeight: CGFloat) -> Int {
        guard rowHeight > 0 else { return 0 }
        pending += delta
        let steps = Int(pending / rowHeight)
        pending -= CGFloat(steps) * rowHeight
        return steps
    }
}
