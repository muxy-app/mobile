import Testing
@testable import Muxy

struct ReconnectScheduleTests {
    @Test func backsOffThenRetriesEveryTenSeconds() {
        var schedule = ReconnectSchedule()
        let delays = (0..<6).map { _ in schedule.nextDelay() }
        #expect(delays == [.seconds(1), .seconds(2), .seconds(5), .seconds(10), .seconds(10), .seconds(10)])
    }

    @Test func countsAttempts() {
        var schedule = ReconnectSchedule()
        _ = schedule.nextDelay()
        _ = schedule.nextDelay()
        #expect(schedule.attempt == 2)
    }

    @Test func resetStartsOverFromOneSecond() {
        var schedule = ReconnectSchedule()
        _ = schedule.nextDelay()
        _ = schedule.nextDelay()
        schedule.reset()
        #expect(schedule.attempt == 0)
        #expect(schedule.nextDelay() == .seconds(1))
    }

    @Test func restartWaitsASecondAndAHalf() {
        #expect(ReconnectSchedule().afterRestart == .milliseconds(1500))
    }

    @Test func delaysCanBeShortenedForTests() {
        var schedule = ReconnectSchedule(initialDelays: [.milliseconds(1)], steadyDelay: .milliseconds(2), afterRestart: .zero)
        #expect(schedule.nextDelay() == .milliseconds(1))
        #expect(schedule.nextDelay() == .milliseconds(2))
        #expect(schedule.afterRestart == .zero)
    }
}
