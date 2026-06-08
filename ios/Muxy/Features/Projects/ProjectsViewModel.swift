import Foundation
import Observation
import OSLog

@MainActor
@Observable
final class ProjectsViewModel {
    let device: Device
    private(set) var state: ConnectionState = .idle
    private(set) var projects: [Project] = []
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
        await connect()
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

    private func loadProjects() async {
        do {
            let result = try await connectionManager.request(.listProjects)
            guard result.type == ResultType.projects else { return }
            projects = try result.decode(ProjectsResult.self).projects.sorted { $0.sortOrder < $1.sortOrder }
            loadFailed = false
        } catch {
            Log.client.error("Failed to load projects: \(error.localizedDescription, privacy: .public)")
            loadFailed = true
        }
    }

    private func applyProjectsEvent(_ event: EventEnvelope) {
        guard let data = event.data, data.type == EventType.projects else { return }
        do {
            projects = try data.decode(ProjectsResult.self).projects.sorted { $0.sortOrder < $1.sortOrder }
        } catch {
            Log.client.error("Failed to decode projects event: \(error.localizedDescription, privacy: .public)")
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
