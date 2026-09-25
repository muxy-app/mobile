import Foundation
import Observation
import OSLog

@MainActor
@Observable
final class ConnectionsListViewModel {
    private(set) var connections: [Connection] = []

    private let store: ConnectionStore
    private let keychain: KeychainStore
    private let credentials: CredentialStore
    private let directory: ServerDirectory

    init(store: ConnectionStore, keychain: KeychainStore, credentials: CredentialStore, directory: ServerDirectory) {
        self.store = store
        self.keychain = keychain
        self.credentials = credentials
        self.directory = directory
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
        forgetServer(of: connection)
        load()
    }

    private func forgetServer(of connection: Connection) {
        guard connection.kind == .server, let serverID = connection.serverID else { return }
        directory.forget(serverID)
        do {
            try credentials.delete(serverId: serverID)
        } catch {
            Log.persistence.error("Failed to delete server credential: \(error.localizedDescription, privacy: .public)")
        }
    }

    func delete(at offsets: IndexSet) {
        let targets = offsets.map { connections[$0] }
        targets.forEach(delete)
    }
}
