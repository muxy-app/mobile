import Foundation
import OSLog

@MainActor
final class ScrollbackRequest {
    private(set) var snapshot: (any ScrollbackSnapshot)?
    private var response: Task<Void, Never>?

    init(channel: any ServerTerminalChannel, maxRows: UInt16) {
        response = Task { [weak self] in
            do {
                let snapshot = try await channel.scrollback(maxRows: maxRows)
                self?.snapshot = snapshot
            } catch {
                Log.terminal.error("Scrollback failed: \(String(describing: ServerFailure(error)), privacy: .public)")
            }
        }
    }

    func waitForSnapshot() async -> (any ScrollbackSnapshot)? {
        await response?.value
        return snapshot
    }
}
