import Foundation
import MuxyMobile
import OSLog

@MainActor
final class HistoryDocument {
    private let snapshot: any ScrollbackSnapshot
    private(set) var lines: [Line]
    private(set) var reachedStart = false
    private(set) var isLoading = false
    let screenRows: Int

    init(snapshot: any ScrollbackSnapshot, screenRows: Int) {
        self.snapshot = snapshot
        lines = snapshot.lines()
        self.screenRows = min(screenRows, lines.count)
        reachedStart = snapshot.historyRows == 0
    }

    var historyRowCount: Int {
        lines.count - screenRows
    }

    func loadOlder(maxRows: UInt16) async -> Int {
        guard !isLoading, !reachedStart else { return 0 }
        isLoading = true
        defer { isLoading = false }
        do {
            let older = try await snapshot.loadOlder(maxRows: maxRows)
            guard !older.isEmpty else {
                reachedStart = true
                return 0
            }
            lines.insert(contentsOf: older, at: 0)
            return older.count
        } catch {
            Log.terminal.error("Loading older history failed: \(String(describing: ServerFailure(error)), privacy: .public)")
            return 0
        }
    }
}
