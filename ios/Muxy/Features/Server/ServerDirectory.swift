import Foundation
import MuxyMobile
import OSLog

@MainActor
final class ServerDirectory {
    private let credentials: CredentialStore
    private let connector: ServerConnector
    private let connections: ConnectionStore?
    private let keychain: KeychainStore
    private var controllers: [String: ServerController] = [:]
    private var activeServerID: String?
    private var isForeground = true

    init(
        credentials: CredentialStore,
        connector: ServerConnector,
        connections: ConnectionStore? = nil,
        keychain: KeychainStore = KeychainTokenStore()
    ) {
        self.credentials = credentials
        self.connector = connector
        self.connections = connections
        self.keychain = keychain
    }

    func controller(for serverID: String) -> ServerController? {
        if let existing = controllers[serverID] { return existing }
        guard let controller = makeController(for: serverID) else { return nil }
        controllers[serverID] = controller
        updateConnections()
        return controller
    }

    func setActiveServer(_ serverID: String?) {
        guard activeServerID != serverID else { return }
        activeServerID = serverID
        updateConnections()
    }

    func setForeground(_ foreground: Bool) {
        guard isForeground != foreground else { return }
        isForeground = foreground
        updateConnections()
    }

    func forget(_ serverID: String) {
        controllers.removeValue(forKey: serverID)?.setWantsConnection(false)
    }

    func credentialDidChange(for serverID: String) {
        controllers[serverID]?.credentialDidChange()
    }

    private func makeController(for serverID: String) -> ServerController? {
        if let saved = connections?.load().first(where: { $0.serverRouteID == serverID && $0.serverTransport == .ssh }) {
            return makeSSHController(for: saved)
        }
        guard let credential = storedCredential(for: serverID) else { return nil }
        let credentials = credentials
        return ServerController(
            serverID: serverID,
            serverName: credential.serverName,
            connector: connector,
            credentialProvider: { Self.credential(for: serverID, in: credentials) }
        )
    }

    private func makeSSHController(for saved: Connection) -> ServerController {
        let connections = connections
        let connector = SDKSSHServerConnector(keychain: keychain)
        return ServerController(
            serverID: saved.serverRouteID ?? saved.id.uuidString,
            serverName: saved.name,
            nameProvider: { connections?.load().first { $0.id == saved.id }?.name },
            openConnection: { events in
                guard let current = connections?.load().first(where: { $0.id == saved.id }) else {
                    throw SSHError.missingCredentials
                }
                return try await connector.connect(to: current, events: events)
            }
        )
    }

    private func storedCredential(for serverID: String) -> ServerCredential? {
        Self.credential(for: serverID, in: credentials)
    }

    private static func credential(for serverID: String, in store: CredentialStore) -> ServerCredential? {
        do {
            return try store.credential(serverId: serverID)
        } catch {
            Log.persistence.error("Reading a paired computer failed: \(error.localizedDescription, privacy: .public)")
            return nil
        }
    }

    private func updateConnections() {
        for (serverID, controller) in controllers {
            controller.setWantsConnection(isForeground && serverID == activeServerID)
        }
    }
}
