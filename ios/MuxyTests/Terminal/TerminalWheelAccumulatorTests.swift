import CoreGraphics
import Testing
@testable import Muxy

struct TerminalWheelAccumulatorTests {
    @Test func aDragShorterThanARowSendsNothing() {
        var wheel = TerminalWheelAccumulator()
        #expect(wheel.steps(for: 9, rowHeight: 10) == 0)
    }

    @Test func eachRowOfDragIsOneStep() {
        var wheel = TerminalWheelAccumulator()
        #expect(wheel.steps(for: 35, rowHeight: 10) == 3)
        #expect(wheel.steps(for: -42, rowHeight: 10) == -3)
    }

    @Test func partialRowsAddUpAcrossMoves() {
        var wheel = TerminalWheelAccumulator()
        #expect(wheel.steps(for: 6, rowHeight: 10) == 0)
        #expect(wheel.steps(for: 6, rowHeight: 10) == 1)
        #expect(wheel.steps(for: 8, rowHeight: 10) == 1)
    }

    @Test func resetDropsThePartialRow() {
        var wheel = TerminalWheelAccumulator()
        _ = wheel.steps(for: 9, rowHeight: 10)
        wheel.reset()
        #expect(wheel.steps(for: 2, rowHeight: 10) == 0)
    }

    @Test func anUnmeasuredRowSendsNothing() {
        var wheel = TerminalWheelAccumulator()
        #expect(wheel.steps(for: 100, rowHeight: 0) == 0)
    }
}
