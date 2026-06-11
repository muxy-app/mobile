import Foundation

nonisolated enum DemoConnection {
    static let id = UUID(uuidString: "00000000-0000-4000-8000-000000000001")!
    static let token = "demo-token"

    static var connection: Connection {
        Connection(
            id: id,
            name: "Demo Desktop",
            host: "demo.local",
            port: 4865,
            kind: .device,
            pairingState: .paired,
            serviceName: "Demo Desktop",
            discoverySource: .manual
        )
    }

    @MainActor
    static func apply(enabled: Bool, store: ConnectionStore, keychain: KeychainStore) {
        if enabled {
            store.upsert(connection)
            try? keychain.setToken(token, for: id)
            return
        }
        store.delete(id: id)
        try? keychain.deleteToken(for: id)
    }
}
