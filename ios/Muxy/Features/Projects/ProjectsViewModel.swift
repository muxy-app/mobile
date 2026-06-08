import Foundation
import Observation
import OSLog

@MainActor
@Observable
final class ProjectsViewModel {
    let device: Device
    private(set) var state: ConnectionState = .idle
    private(set) var projects: [Project] = []
    private(set) var logos: [Project.ID: Data] = [:]
    private(set) var loadFailed = false

    private let keychain: KeychainStore
    private let connectionManager: ConnectionManager
    private var observationTask: Task<Void, Never>?
    private var eventsTask: Task<Void, Never>?

    init(device: Device, keychain: KeychainStore, connectionManager: ConnectionManager) {
        self.device = device
        self.keychain = keychain
        self.connectionManager = connectionManager
    }

    func connect() async {
        observeState()
        subscribeToEvents()
        guard let token = loadToken() else {
            state = .failed(.missingToken)
            return
        }
        await connectionManager.ensureConnected(device: device, token: token)
    }

    func reconnect() async {
        observeState()
        subscribeToEvents()
        guard let token = loadToken() else {
            state = .failed(.missingToken)
            return
        }
        await connectionManager.connect(to: device, token: token)
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

    func logoData(for project: Project) -> Data? {
        logos[project.id]
    }

    private func loadProjects() async {
        do {
            let result = try await connectionManager.request(.listProjects)
            guard result.type == ResultType.projects else { return }
            projects = try result.decode(ProjectsResult.self).projects.sorted { $0.sortOrder < $1.sortOrder }
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
            return try keychain.token(for: device.id)
        } catch {
            Log.connection.error("Failed to load token: \(error.localizedDescription, privacy: .public)")
            return nil
        }
    }
}
