import Foundation

protocol WorkspaceSelectionStore {
    func load(connectionID: UUID) -> UUID?
    func save(_ workspaceID: UUID?, connectionID: UUID)
}

final class UserDefaultsWorkspaceSelectionStore: WorkspaceSelectionStore {
    private let defaults: UserDefaults
    private let keyPrefix: String

    init(defaults: UserDefaults = .standard, keyPrefix: String = "muxy.selectedWorkspace") {
        self.defaults = defaults
        self.keyPrefix = keyPrefix
    }

    func load(connectionID: UUID) -> UUID? {
        guard let value = defaults.string(forKey: key(connectionID: connectionID)) else { return nil }
        return UUID(uuidString: value)
    }

    func save(_ workspaceID: UUID?, connectionID: UUID) {
        let storageKey = key(connectionID: connectionID)
        guard let workspaceID else {
            defaults.removeObject(forKey: storageKey)
            return
        }
        defaults.set(workspaceID.uuidString, forKey: storageKey)
    }

    private func key(connectionID: UUID) -> String {
        "\(keyPrefix).\(connectionID.uuidString)"
    }
}
