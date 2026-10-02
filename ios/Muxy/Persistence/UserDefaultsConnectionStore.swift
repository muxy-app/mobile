import Foundation
import OSLog

final class UserDefaultsConnectionStore: ConnectionStore {
    private let defaults: UserDefaults
    private let key: String

    init(defaults: UserDefaults = .standard, key: String = "muxy.devices") {
        self.defaults = defaults
        self.key = key
    }

    func load() -> [Connection] {
        guard let data = defaults.data(forKey: key) else { return [] }
        do {
            return try JSONDecoder().decode([Connection].self, from: data)
        } catch {
            Log.persistence.error("Failed to decode connections: \(error.localizedDescription, privacy: .public)")
            return []
        }
    }

    func save(_ connections: [Connection]) {
        do {
            let data = try JSONEncoder().encode(connections)
            defaults.set(data, forKey: key)
        } catch {
            Log.persistence.error("Failed to encode connections: \(error.localizedDescription, privacy: .public)")
        }
    }

    func upsert(_ connection: Connection) {
        var connections = load()
        if let index = connections.firstIndex(where: { $0.id == connection.id }) {
            connections[index] = connection
        } else {
            connections.append(connection)
        }
        save(connections)
    }

    func delete(id: Connection.ID) {
        var connections = load()
        connections.removeAll { $0.id == id }
        save(connections)
    }
}
