import Foundation

public struct PushServer: Codable, Equatable, Sendable {
    public let serverId: String
    public var name: String
    public var isEnabled: Bool
    public var status: String?

    public init(serverId: String, name: String, isEnabled: Bool = true, status: String? = nil) {
        self.serverId = serverId
        self.name = name
        self.isEnabled = isEnabled
        self.status = status
    }
}

public final class PushServerDirectory: Sendable {
    private static let storageKey = "muxy.push.servers"

    private nonisolated(unsafe) let defaults: UserDefaults

    public init(defaults: UserDefaults) {
        self.defaults = defaults
    }

    public func servers() -> [PushServer] {
        guard let data = defaults.data(forKey: Self.storageKey),
              let stored = try? JSONDecoder().decode([PushServer].self, from: data) else { return [] }
        return stored
    }

    public func server(_ serverId: String) -> PushServer? {
        servers().first { $0.serverId == serverId }
    }

    public func isEnabled(_ serverId: String) -> Bool {
        server(serverId)?.isEnabled ?? true
    }

    public func save(_ server: PushServer) {
        var stored = servers().filter { $0.serverId != server.serverId }
        stored.append(server)
        write(stored)
    }

    public func remove(_ serverId: String) {
        write(servers().filter { $0.serverId != serverId })
    }

    private func write(_ servers: [PushServer]) {
        guard let data = try? JSONEncoder().encode(servers.sorted { $0.serverId < $1.serverId }) else { return }
        defaults.set(data, forKey: Self.storageKey)
    }
}
