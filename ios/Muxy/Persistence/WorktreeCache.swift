import Foundation
import OSLog

protocol WorktreeCache {
    func load(connectionID: UUID?, projectID: UUID) -> [Worktree]?
    func save(_ worktrees: [Worktree], connectionID: UUID?, projectID: UUID)
}

final class UserDefaultsWorktreeCache: WorktreeCache {
    private let defaults: UserDefaults
    private let keyPrefix: String

    init(defaults: UserDefaults = .standard, keyPrefix: String = "muxy.worktrees") {
        self.defaults = defaults
        self.keyPrefix = keyPrefix
    }

    func load(connectionID: UUID?, projectID: UUID) -> [Worktree]? {
        guard let data = defaults.data(forKey: key(connectionID: connectionID, projectID: projectID)) else { return nil }
        do {
            return try JSONDecoder().decode([Worktree].self, from: data)
        } catch {
            Log.persistence.error("Failed to decode cached worktrees: \(error.localizedDescription, privacy: .public)")
            return nil
        }
    }

    func save(_ worktrees: [Worktree], connectionID: UUID?, projectID: UUID) {
        do {
            let data = try JSONEncoder().encode(worktrees)
            defaults.set(data, forKey: key(connectionID: connectionID, projectID: projectID))
        } catch {
            Log.persistence.error("Failed to encode cached worktrees: \(error.localizedDescription, privacy: .public)")
        }
    }

    private func key(connectionID: UUID?, projectID: UUID) -> String {
        let scope = connectionID?.uuidString ?? "global"
        return "\(keyPrefix).\(scope).\(projectID.uuidString)"
    }
}
