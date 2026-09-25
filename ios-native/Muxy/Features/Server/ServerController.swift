import Foundation
import MuxyMobile
import Observation
import OSLog

@MainActor
@Observable
final class ServerController {
    enum Phase: Equatable {
        case idle
        case connecting
        case connected
        case reconnecting(ReconnectReason)
        case failed(ServerFailure)
    }

    enum ReconnectReason: Equatable {
        case serverRestarting
        case lost(ServerFailure, attempt: Int)
    }

    let serverID: String
    private(set) var serverName: String
    private(set) var phase: Phase = .idle
    private(set) var projects: [ServerProject] = []
    private(set) var hasLoadedProjects = false
    private(set) var projectsLoadFailed = false

    @ObservationIgnored private(set) var connection: (any ServerConnection)?
    @ObservationIgnored private let connector: ServerConnector
    @ObservationIgnored private let credentialProvider: () -> ServerCredential?
    @ObservationIgnored private let attachment = AttachmentCoordinator()
    @ObservationIgnored private var projectModels: [String: ProjectModel] = [:]
    @ObservationIgnored private var terminalsBySession: [UInt64: TerminalController] = [:]
    @ObservationIgnored private var generation = 0
    @ObservationIgnored private var wantsConnection = false
    @ObservationIgnored private var isConnecting = false
    @ObservationIgnored private var restartPending = false
    @ObservationIgnored private var schedule: ReconnectSchedule
    @ObservationIgnored private var reconnectTask: Task<Void, Never>?
    @ObservationIgnored private let projectsRefresh = CoalescedRefresh()
    @ObservationIgnored private let sessionsRefresh = CoalescedRefresh()

    init(
        serverID: String,
        serverName: String,
        connector: ServerConnector,
        credentialProvider: @escaping () -> ServerCredential?,
        schedule: ReconnectSchedule = ReconnectSchedule()
    ) {
        self.serverID = serverID
        self.serverName = serverName
        self.connector = connector
        self.credentialProvider = credentialProvider
        self.schedule = schedule
        attachment.server = self
    }

    var projectRows: [ProjectRow] {
        ProjectTree.rows(from: projects)
    }

    var isConnectionLost: Bool {
        switch phase {
        case .reconnecting, .failed:
            return true
        case .idle, .connecting, .connected:
            return false
        }
    }

    func project(for projectID: String) -> ServerProject? {
        projects.first { $0.id == projectID }
    }

    func projectModel(for projectID: String) -> ProjectModel {
        if let existing = projectModels[projectID] { return existing }
        let model = ProjectModel(projectID: projectID, server: self)
        projectModels[projectID] = model
        return model
    }

    func setWantsConnection(_ wants: Bool) {
        guard wantsConnection != wants else { return }
        wantsConnection = wants
        if wants {
            connect()
        } else {
            closeConnection()
            resetPhaseUnlessFailed()
        }
    }

    func retryNow() {
        if case .failed = phase {
            phase = .idle
        }
        schedule.reset()
        reconnectTask?.cancel()
        reconnectTask = nil
        connect()
    }

    func credentialDidChange() {
        closeConnection()
        phase = .idle
        schedule.reset()
        if let credential = credentialProvider() {
            serverName = credential.serverName
        }
        connect()
    }

    func endSession(_ sessionID: UInt64) async throws {
        guard let connection else { throw MobileError.Disconnected }
        try await connection.endSession(sessionID)
        sessionDidEnd(sessionID)
    }

    func reloadProjects() {
        guard connection != nil else { return }
        projectsRefresh.request { [weak self] in await self?.loadProjects() }
    }

    func register(_ terminal: TerminalController, sessionID: UInt64) {
        terminal.assign(sessionID: sessionID)
        terminalsBySession[sessionID] = terminal
        projectModels[terminal.projectID]?.didRegister(terminal)
    }

    func retire(_ terminal: TerminalController) {
        terminal.markRetired()
        if let sessionID = terminal.sessionID, terminalsBySession[sessionID] === terminal {
            terminalsBySession[sessionID] = nil
        }
        attachment.retire(terminal)
    }

    func updateVisibleTerminal() {
        attachment.show(projectModels.values.first(where: \.isVisible)?.selectedTab)
    }

    func viewportDidChange(of terminal: TerminalController) {
        attachment.viewportDidChange(of: terminal)
    }

    func attachDidFail(for terminal: TerminalController) {
        guard let model = projectModels[terminal.projectID] else { return }
        refreshSessions(of: model)
    }

    func refreshSessions(of model: ProjectModel) {
        guard let connection else { return }
        Task { await model.refreshSessions(using: connection) }
    }

    private func connect() {
        guard wantsConnection, connection == nil, !isConnecting else { return }
        if case .failed = phase { return }
        guard let credential = credentialProvider() else {
            phase = .failed(.invalidCredential)
            return
        }
        serverName = credential.serverName
        reconnectTask?.cancel()
        reconnectTask = nil
        generation += 1
        let attempt = generation
        isConnecting = true
        if phase == .idle {
            phase = .connecting
        }
        let events = eventHandler(for: attempt)
        Task {
            do {
                let connection = try await connector.connect(to: credential, events: events)
                didConnect(connection, generation: attempt)
            } catch {
                didFailToConnect(ServerFailure(error), generation: attempt)
            }
        }
    }

    private func eventHandler(for attempt: Int) -> @Sendable (ConnectionEvent) -> Void {
        { [weak self] event in
            DispatchQueue.main.async { [weak self] in
                MainActor.assumeIsolated {
                    self?.handle(event, generation: attempt)
                }
            }
        }
    }

    private func didConnect(_ connection: any ServerConnection, generation attempt: Int) {
        guard attempt == generation else {
            connection.disconnect()
            return
        }
        isConnecting = false
        self.connection = connection
        phase = .connected
        schedule.reset()
        restartPending = false
        Log.connection.info("Connected to Muxy \(connection.serverVersion, privacy: .public)")
        refreshEverything()
        attachment.connectionDidOpen()
    }

    private func didFailToConnect(_ failure: ServerFailure, generation attempt: Int) {
        guard attempt == generation else { return }
        isConnecting = false
        Log.connection.error("Connecting failed: \(String(describing: failure), privacy: .public)")
        guard !failure.isFatal else {
            phase = .failed(failure)
            return
        }
        scheduleReconnect(after: schedule.nextDelay(), reason: .lost(failure, attempt: schedule.attempt))
    }

    private func handle(_ event: ConnectionEvent, generation eventGeneration: Int) {
        guard eventGeneration == generation else { return }
        switch event {
        case let .screenChanged(sessionId):
            terminalsBySession[sessionId]?.screenDidChange()
        case let .metadataChanged(sessionId):
            terminalsBySession[sessionId]?.metadataDidChange()
        case let .sessionEnded(sessionId):
            sessionDidEnd(sessionId)
        case .catalogChanged:
            guard connection != nil else { return }
            projectsRefresh.request { [weak self] in await self?.loadProjects() }
        case .sessionsChanged:
            guard connection != nil else { return }
            sessionsRefresh.request { [weak self] in await self?.loadSessions() }
        case .activityChanged:
            break
        case .serverRestarting:
            restartPending = true
            phase = .reconnecting(.serverRestarting)
        case .disconnected:
            connectionDidClose()
        }
    }

    private func connectionDidClose() {
        let restarting = restartPending
        closeConnection()
        guard wantsConnection else {
            resetPhaseUnlessFailed()
            return
        }
        if restarting {
            scheduleReconnect(after: schedule.afterRestart, reason: .serverRestarting)
            return
        }
        scheduleReconnect(after: schedule.nextDelay(), reason: .lost(.disconnected, attempt: schedule.attempt))
    }

    private func closeConnection() {
        reconnectTask?.cancel()
        reconnectTask = nil
        generation += 1
        isConnecting = false
        restartPending = false
        let closing = connection
        connection = nil
        attachment.connectionDidClose()
        projectModels.values.flatMap(\.tabs).forEach { $0.connectionDidClose() }
        projectsRefresh.cancel()
        sessionsRefresh.cancel()
        closing?.disconnect()
    }

    private func resetPhaseUnlessFailed() {
        if case .failed = phase { return }
        phase = .idle
        schedule.reset()
    }

    private func scheduleReconnect(after delay: Duration, reason: ReconnectReason) {
        phase = .reconnecting(reason)
        reconnectTask?.cancel()
        reconnectTask = Task { [weak self] in
            try? await Task.sleep(for: delay)
            guard !Task.isCancelled else { return }
            self?.connect()
        }
    }

    private func sessionDidEnd(_ sessionID: UInt64) {
        let terminal = terminalsBySession.removeValue(forKey: sessionID)
        if let terminal {
            projectModels[terminal.projectID]?.sessionDidEnd(sessionID)
        }
        guard connection != nil else { return }
        sessionsRefresh.request { [weak self] in await self?.loadSessions() }
    }

    private func refreshEverything() {
        projectsRefresh.request { [weak self] in await self?.loadProjects() }
        sessionsRefresh.request { [weak self] in await self?.loadSessions() }
    }

    private func loadProjects() async {
        guard let connection else { return }
        do {
            projects = try await connection.projects()
            hasLoadedProjects = true
            projectsLoadFailed = false
            dropRemovedProjects()
        } catch {
            projectsLoadFailed = true
            Log.connection.error("Loading projects failed: \(String(describing: ServerFailure(error)), privacy: .public)")
        }
    }

    private func loadSessions() async {
        guard let connection else { return }
        for model in projectModels.values where model.needsSessions {
            await model.refreshSessions(using: connection)
        }
    }

    private func dropRemovedProjects() {
        let existing = Set(projects.map(\.id))
        for (projectID, model) in projectModels where !existing.contains(projectID) {
            model.removeAllTabs()
            projectModels[projectID] = nil
        }
    }
}
