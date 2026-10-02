import Foundation
import MuxyMobile
import OSLog

@MainActor
final class ServerDirectory {
    private let credentials: CredentialStore
    private let connector: ServerConnector
    private var controllers: [String: ServerController] = [:]
    private var activeServerID: String?
    private var isForeground = true

    init(credentials: CredentialStore, connector: ServerConnector) {
        self.credentials = credentials
        self.connector = connector
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
        guard let credential = storedCredential(for: serverID) else { return nil }
        let credentials = credentials
        return ServerController(
            serverID: serverID,
            serverName: credential.serverName,
            connector: connector,
            credentialProvider: { Self.credential(for: serverID, in: credentials) }
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
