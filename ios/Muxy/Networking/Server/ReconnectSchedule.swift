import Foundation

nonisolated struct ReconnectSchedule: Sendable {
    let afterRestart: Duration

    private let initialDelays: [Duration]
    private let steadyDelay: Duration
    private(set) var attempt = 0

    init(
        initialDelays: [Duration] = [.seconds(1), .seconds(2), .seconds(5)],
        steadyDelay: Duration = .seconds(10),
        afterRestart: Duration = .milliseconds(1500)
    ) {
        self.initialDelays = initialDelays
        self.steadyDelay = steadyDelay
        self.afterRestart = afterRestart
    }

    mutating func nextDelay() -> Duration {
        defer { attempt += 1 }
        guard attempt < initialDelays.count else { return steadyDelay }
        return initialDelays[attempt]
    }

    mutating func reset() {
        attempt = 0
    }
}
