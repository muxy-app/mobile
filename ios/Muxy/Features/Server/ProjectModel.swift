import Foundation
import MuxyMobile
import Observation
import OSLog

@MainActor
@Observable
final class ProjectModel {
    let projectID: String
    private(set) var tabs: [TerminalController] = []
    private(set) var selectedTabID: UUID?
    private(set) var hasLoadedSessions = false
    private(set) var sessionsLoadFailed = false

    @ObservationIgnored private(set) var isVisible = false
    @ObservationIgnored var isListed = false
    @ObservationIgnored private weak var server: ServerController?
    @ObservationIgnored private var endedSessionIDs: Set<UInt64> = []
    @ObservationIgnored private var sessionsRevision = 0

    init(projectID: String, server: ServerController) {
        self.projectID = projectID
        self.server = server
    }

    var selectedTab: TerminalController? {
        tabs.first { $0.id == selectedTabID }
    }

    var needsSessions: Bool {
        isVisible || isListed
    }

    func setVisible(_ visible: Bool) {
        guard isVisible != visible else { return }
        isVisible = visible
        server?.updateVisibleTerminal()
        guard visible else { return }
        server?.refreshSessions(of: self)
    }

    @discardableResult
    func createTab() -> TerminalController? {
        guard let server else { return nil }
        let tab = TerminalController(projectID: projectID, sessionID: nil, directory: server.project(for: projectID)?.directory ?? "", server: server)
        tabs.append(tab)
        select(tab)
        return tab
    }

    func select(_ tab: TerminalController) {
        guard selectedTabID != tab.id else { return }
        selectedTabID = tab.id
        server?.updateVisibleTerminal()
    }

    func close(_ tab: TerminalController) {
        remove(tab)
        guard let sessionID = tab.sessionID, let server else { return }
        endedSessionIDs.insert(sessionID)
        Task {
            do {
                try await server.endSession(sessionID)
            } catch {
                Log.connection.error("Ending a session failed: \(String(describing: ServerFailure(error)), privacy: .public)")
                endedSessionIDs.remove(sessionID)
            }
        }
    }

    func sessionDidEnd(_ sessionID: UInt64) {
        endedSessionIDs.insert(sessionID)
        guard let tab = tabs.first(where: { $0.sessionID == sessionID }) else { return }
        tab.markEnded()
        remove(tab)
    }

    func didRegister(_ terminal: TerminalController) {
        guard let sessionID = terminal.sessionID else { return }
        let duplicates = tabs.filter { $0.id != terminal.id && $0.sessionID == sessionID }
        duplicates.forEach(remove)
    }

    func refreshSessions(using connection: any ServerConnection) async {
        sessionsRevision += 1
        let revision = sessionsRevision
        let knownBeforeRequest = Set(tabs.compactMap(\.sessionID))
        do {
            let listed = try await connection.sessions(projectId: projectID)
            guard revision == sessionsRevision, server?.connection === connection, !Task.isCancelled else { return }
            let live = listed.filter { $0.status == .live || $0.status == .starting }
            hasLoadedSessions = true
            sessionsLoadFailed = false
            reconcile(with: live, knownBeforeRequest: knownBeforeRequest)
        } catch {
            guard revision == sessionsRevision, server?.connection === connection, !Task.isCancelled else { return }
            sessionsLoadFailed = true
            Log.connection.error("Loading sessions failed: \(String(describing: ServerFailure(error)), privacy: .public)")
        }
    }

    func removeAllTabs() {
        sessionsRevision += 1
        tabs.forEach(retire)
        tabs.removeAll()
        selectedTabID = nil
        server?.updateVisibleTerminal()
    }

    private func reconcile(with sessions: [Session], knownBeforeRequest: Set<UInt64>) {
        let liveIDs = Set(sessions.map(\.id))
        endedSessionIDs.formIntersection(liveIDs)
        pruneEnded(knownBeforeRequest.subtracting(liveIDs))
        appendNew(sessions)
        guard selectedTab == nil, let first = tabs.first else { return }
        select(first)
    }

    private func pruneEnded(_ endedIDs: Set<UInt64>) {
        let ended = tabs.filter { tab in tab.sessionID.map(endedIDs.contains) ?? false }
        for tab in ended {
            tab.markEnded()
            remove(tab)
        }
    }

    private func appendNew(_ sessions: [Session]) {
        guard let server else { return }
        let open = Set(tabs.compactMap(\.sessionID))
        let new = sessions
            .filter { !open.contains($0.id) && !endedSessionIDs.contains($0.id) }
            .sorted { $0.id < $1.id }
        for session in new {
            let tab = TerminalController(projectID: projectID, sessionID: session.id, directory: session.directory, server: server)
            server.register(tab, sessionID: session.id)
            tabs.append(tab)
        }
    }

    private func remove(_ tab: TerminalController) {
        guard let index = tabs.firstIndex(where: { $0.id == tab.id }) else { return }
        tabs.remove(at: index)
        retire(tab)
        if selectedTabID == tab.id {
            selectedTabID = tabs.indices.contains(index) ? tabs[index].id : tabs.last?.id
        }
        server?.updateVisibleTerminal()
    }

    private func retire(_ tab: TerminalController) {
        server?.retire(tab)
    }
}
