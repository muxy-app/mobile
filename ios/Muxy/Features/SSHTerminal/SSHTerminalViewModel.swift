import Foundation
import Observation

@MainActor
@Observable
final class SSHTerminalViewModel {
    let connection: Connection
    private(set) var tabs: [Tab] = []
    var selectedTabID: UUID?

    private let sessionStore: SSHTerminalSessionStore

    init(connection: Connection, keychain: KeychainStore) {
        self.connection = connection
        sessionStore = SSHTerminalSessionStore(connection: connection, keychain: keychain)
    }

    var connectionName: String {
        connection.name
    }

    func start() {
        guard tabs.isEmpty else { return }
        createTab()
    }

    func terminalSession(for tab: Tab) -> SSHTerminalSession {
        sessionStore.session(for: tab.id)
    }

    func createTab() {
        let tab = Tab(id: UUID(), kind: .terminal, title: "Terminal", isPinned: false, paneID: nil)
        tabs.append(tab)
        select(tab)
    }

    func closeTab(_ tab: Tab) {
        tabs.removeAll { $0.id == tab.id }
        sessionStore.close(tab.id)
        guard selectedTabID == tab.id else { return }
        selectedTabID = tabs.last?.id
        sessionStore.selectionChanged(to: selectedTabID)
    }

    func select(_ tab: Tab) {
        selectedTabID = tab.id
        sessionStore.selectionChanged(to: tab.id)
    }

    func teardown() {
        sessionStore.teardown()
    }
}
