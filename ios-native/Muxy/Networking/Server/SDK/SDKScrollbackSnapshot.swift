import Foundation
import MuxyMobile

nonisolated final class SDKScrollbackSnapshot: ScrollbackSnapshot {
    private let scrollback: MuxyMobile.Scrollback
    private let lanes: SDKLanes

    init(scrollback: MuxyMobile.Scrollback, lanes: SDKLanes) {
        self.scrollback = scrollback
        self.lanes = lanes
    }

    var historyRows: UInt64 {
        scrollback.historyRows()
    }

    func lines() -> [Line] {
        scrollback.lines()
    }

    func loadOlder(maxRows: UInt16) async throws -> [Line] {
        try await lanes.request { [scrollback] in
            try scrollback.loadOlder(maxRows: maxRows)
        }
    }
}
