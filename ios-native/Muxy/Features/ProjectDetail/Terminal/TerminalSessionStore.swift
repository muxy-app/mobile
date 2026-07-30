import Foundation
import Observation

@MainActor
@Observable
final class TerminalSessionStore {
    @ObservationIgnored private let channel: TerminalChannel
    @ObservationIgnored private var sessions: [UUID: TerminalSession] = [:]
    @ObservationIgnored private var activePaneID: UUID?

    init(channel: TerminalChannel) {
        self.channel = channel
    }

    func session(for tab: Tab) -> TerminalSession? {
        guard tab.kind == .terminal, let paneID = tab.paneID else { return nil }
        return makeSession(paneID: paneID)
    }

    func selectionChanged(to selectedTabID: UUID?, tabs: [Tab]) {
        let paneID = selectedTabID.flatMap { id in
            tabs.first(where: { $0.id == id })
        }.flatMap(paneID(of:))

        guard paneID != activePaneID else { return }

        if let activePaneID {
            sessions[activePaneID]?.deactivate()
        }
        activePaneID = paneID

        guard let paneID else { return }
        makeSession(paneID: paneID).activate()
    }

    private func makeSession(paneID: UUID) -> TerminalSession {
        if let existing = sessions[paneID] { return existing }
        let session = TerminalSession(paneID: paneID, channel: channel)
        sessions[paneID] = session
        return session
    }

    func connectionStateChanged(_ state: ConnectionState) {
        guard let activePaneID, let session = sessions[activePaneID] else { return }
        session.handleConnectionState(state)
    }

    func tabsChanged(_ tabs: [Tab]) {
        let livePaneIDs = Set(tabs.compactMap(paneID(of:)))
        for (paneID, session) in sessions where !livePaneIDs.contains(paneID) {
            session.deactivate()
            sessions[paneID] = nil
            if activePaneID == paneID {
                activePaneID = nil
            }
        }
    }

    func teardown() {
        for session in sessions.values {
            session.deactivate()
        }
        sessions.removeAll()
        activePaneID = nil
    }

    private func paneID(of tab: Tab) -> UUID? {
        guard tab.kind == .terminal else { return nil }
        return tab.paneID
    }
}
