import Foundation

final class InMemoryConnectionStore: ConnectionStore, @unchecked Sendable {
    private let lock = NSLock()
    private var connections: [Connection]

    init(connections: [Connection] = []) {
        self.connections = connections
    }

    func load() -> [Connection] {
        lock.lock()
        defer { lock.unlock() }
        return connections
    }

    func save(_ connections: [Connection]) {
        lock.lock()
        self.connections = connections
        lock.unlock()
    }

    func upsert(_ connection: Connection) {
        lock.lock()
        if let index = connections.firstIndex(where: { $0.id == connection.id }) {
            connections[index] = connection
        } else {
            connections.append(connection)
        }
        lock.unlock()
    }

    func delete(id: Connection.ID) {
        lock.lock()
        connections.removeAll { $0.id == id }
        lock.unlock()
    }
}

final class InMemoryKeychainStore: KeychainStore, @unchecked Sendable {
    private let lock = NSLock()
    private var secrets: [String: String] = [:]

    func setSecret(_ value: String, _ secret: KeychainSecret, for connectionID: Connection.ID) throws {
        lock.lock()
        secrets[key(secret, connectionID)] = value
        lock.unlock()
    }

    func secret(_ secret: KeychainSecret, for connectionID: Connection.ID) throws -> String? {
        lock.lock()
        defer { lock.unlock() }
        return secrets[key(secret, connectionID)]
    }

    func deleteSecrets(for connectionID: Connection.ID) throws {
        lock.lock()
        for secret in KeychainSecret.allCases {
            secrets[key(secret, connectionID)] = nil
        }
        lock.unlock()
    }

    private func key(_ secret: KeychainSecret, _ connectionID: Connection.ID) -> String {
        "\(connectionID.uuidString).\(secret.rawValue)"
    }
}
