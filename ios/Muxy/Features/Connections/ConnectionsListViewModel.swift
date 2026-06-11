import Foundation
import Observation
import OSLog

@MainActor
@Observable
final class ConnectionsListViewModel {
    private(set) var connections: [Connection] = []

    private let store: ConnectionStore
    private let keychain: KeychainStore

    init(store: ConnectionStore, keychain: KeychainStore) {
        self.store = store
        self.keychain = keychain
    }

    func load() {
        connections = store.load()
    }

    func delete(_ connection: Connection) {
        store.delete(id: connection.id)
        do {
            try keychain.deleteSecrets(for: connection.id)
        } catch {
            Log.persistence.error("Failed to delete secrets: \(error.localizedDescription, privacy: .public)")
        }
        load()
    }

    func delete(at offsets: IndexSet) {
        let targets = offsets.map { connections[$0] }
        targets.forEach(delete)
    }
}
