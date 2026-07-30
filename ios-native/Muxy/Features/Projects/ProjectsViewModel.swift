import Foundation
import Observation
import OSLog

@MainActor
@Observable
final class ProjectsViewModel {
    let connection: Connection
    private(set) var state: ConnectionState = .idle
    private(set) var projects: [Project] = []
    private(set) var logos: [Project.ID: Data] = [:]
    private(set) var loadFailed = false

    var selectedWorkspaceID: UUID? {
        didSet {
            guard selectedWorkspaceID != oldValue else { return }
            workspaceSelectionStore.save(selectedWorkspaceID, connectionID: connection.id)
        }
    }

    private let keychain: KeychainStore
    private let connectionManager: ConnectionManager
    private let workspaceSelectionStore: WorkspaceSelectionStore
    private var observationTask: Task<Void, Never>?
    private var eventsTask: Task<Void, Never>?

    init(
        connection: Connection,
        keychain: KeychainStore,
        connectionManager: ConnectionManager,
        workspaceSelectionStore: WorkspaceSelectionStore = UserDefaultsWorkspaceSelectionStore()
    ) {
        self.connection = connection
        self.keychain = keychain
        self.connectionManager = connectionManager
        self.workspaceSelectionStore = workspaceSelectionStore
        selectedWorkspaceID = workspaceSelectionStore.load(connectionID: connection.id)
    }

    func connect() async {
        observeState()
        subscribeToEvents()
        guard let token = loadToken() else {
            state = .failed(.missingToken)
            return
        }
        await connectionManager.ensureConnected(connection: connection, token: token)
    }

    func reconnect() async {
        observeState()
        subscribeToEvents()
        guard let token = loadToken() else {
            state = .failed(.missingToken)
            return
        }
        await connectionManager.connect(to: connection, token: token)
    }

    func disconnect() async {
        observationTask?.cancel()
        observationTask = nil
        eventsTask?.cancel()
        eventsTask = nil
    }

    private func observeState() {
        guard observationTask == nil else { return }
        observationTask = Task { [weak self, connectionManager] in
            var previous: ConnectionState?
            for await state in await connectionManager.stateUpdates() {
                guard let self else { return }
                self.state = state
                if state == .connected, previous != .connected {
                    await self.loadProjects()
                }
                previous = state
            }
        }
    }

    private func subscribeToEvents() {
        guard eventsTask == nil else { return }
        eventsTask = Task { [weak self, connectionManager] in
            for await event in await connectionManager.events() {
                guard let self else { return }
                guard event.event == EventName.projectsChanged else { continue }
                self.applyProjectsEvent(event)
            }
        }
    }

    var workspaces: [ProjectWorkspace] {
        var seen = Set<UUID>()
        var result: [ProjectWorkspace] = []
        for project in projects {
            guard let id = project.workspaceID, let name = project.workspaceName else { continue }
            guard seen.insert(id).inserted else { continue }
            result.append(ProjectWorkspace(id: id, name: name))
        }
        return result
    }

    var filteredProjects: [Project] {
        guard let selectedWorkspaceID, workspaces.contains(where: { $0.id == selectedWorkspaceID }) else {
            return projects
        }
        return projects.filter { $0.workspaceID == selectedWorkspaceID }
    }

    func logoData(for project: Project) -> Data? {
        logos[project.id]
    }

    private func pruneSelectedWorkspaceIfMissing() {
        guard let selectedWorkspaceID, !projects.isEmpty else { return }
        guard !projects.contains(where: { $0.workspaceID == selectedWorkspaceID }) else { return }
        self.selectedWorkspaceID = nil
    }

    private func loadProjects() async {
        do {
            let result = try await connectionManager.request(.listProjects)
            guard result.type == ResultType.projects else { return }
            projects = try result.decode(ProjectsResult.self).projects.sorted { $0.sortOrder < $1.sortOrder }
            pruneSelectedWorkspaceIfMissing()
            loadFailed = false
            await loadLogos()
        } catch {
            Log.client.error("Failed to load projects: \(String(describing: error), privacy: .public)")
            loadFailed = true
        }
    }

    private func applyProjectsEvent(_ event: EventEnvelope) {
        guard let data = event.data, data.type == EventType.projects else { return }
        do {
            projects = try data.decode(ProjectsResult.self).projects.sorted { $0.sortOrder < $1.sortOrder }
            pruneSelectedWorkspaceIfMissing()
        } catch {
            Log.client.error("Failed to decode projects event: \(error.localizedDescription, privacy: .public)")
            return
        }
        Task { await loadLogos() }
    }

    private func loadLogos() async {
        for project in projects where project.logo != nil && logos[project.id] == nil {
            await loadLogo(for: project)
        }
    }

    private func loadLogo(for project: Project) async {
        do {
            let params = GetProjectLogoParams(projectID: project.id.uuidString)
            let result = try await connectionManager.request(.getProjectLogo, params: params)
            guard result.type == ResultType.projectLogo else { return }
            let logo = try result.decode(ProjectLogoResult.self)
            guard let data = Data(base64Encoded: logo.pngData) else { return }
            logos[project.id] = data
        } catch let error as ProtocolError where error.code == .notFound {
            return
        } catch {
            Log.client.error("Failed to load project logo: \(String(describing: error), privacy: .public)")
        }
    }

    private func loadToken() -> String? {
        do {
            return try keychain.token(for: connection.id)
        } catch {
            Log.connection.error("Failed to load token: \(error.localizedDescription, privacy: .public)")
            return nil
        }
    }
}
