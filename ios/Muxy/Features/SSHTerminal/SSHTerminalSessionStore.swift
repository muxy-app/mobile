import Foundation
import Observation

@MainActor
@Observable
final class SSHTerminalSessionStore {
    @ObservationIgnored private let connection: Connection
    @ObservationIgnored private let keychain: KeychainStore
    @ObservationIgnored private var sessions: [UUID: SSHTerminalSession] = [:]
    @ObservationIgnored private var activeTabID: UUID?

    init(connection: Connection, keychain: KeychainStore) {
        self.connection = connection
        self.keychain = keychain
    }

    func session(for tabID: UUID) -> SSHTerminalSession {
        if let existing = sessions[tabID] { return existing }
        let session = SSHTerminalSession(tabID: tabID, session: SSHSession(connection: connection, keychain: keychain))
        sessions[tabID] = session
        return session
    }

    func selectionChanged(to tabID: UUID?) {
        guard tabID != activeTabID else { return }
        activeTabID = tabID
        guard let tabID else { return }
        session(for: tabID).activate()
    }

    func close(_ tabID: UUID) {
        sessions[tabID]?.teardown()
        sessions[tabID] = nil
        if activeTabID == tabID {
            activeTabID = nil
        }
    }

    func teardown() {
        for session in sessions.values {
            session.teardown()
        }
        sessions.removeAll()
        activeTabID = nil
    }
}
