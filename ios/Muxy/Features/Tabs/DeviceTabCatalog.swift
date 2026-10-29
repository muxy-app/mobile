import Foundation
import Observation
import OSLog

@MainActor
@Observable
final class DeviceTabCatalog {
    let connection: Connection
    private(set) var state: ConnectionState = .idle
    private(set) var projects: [Project] = []
    private(set) var workspaces: [UUID: Workspace] = [:]
    private(set) var hasLoaded = false
    private(set) var loadFailed = false

    @ObservationIgnored private let keychain: KeychainStore
    @ObservationIgnored private let makeManager: () -> ConnectionManager
    @ObservationIgnored private var manager: ConnectionManager?
    @ObservationIgnored private var connectionTask: Task<Void, Never>?
    @ObservationIgnored private var stateTask: Task<Void, Never>?
    @ObservationIgnored private var eventsTask: Task<Void, Never>?
    @ObservationIgnored private var generation = 0
    @ObservationIgnored private var workspaceRevisions: [UUID: Int] = [:]
    @ObservationIgnored private let refresh = CoalescedRefresh()

    init(connection: Connection, keychain: KeychainStore, makeManager: @escaping () -> ConnectionManager) {
        self.connection = connection
        self.keychain = keychain
        self.makeManager = makeManager
    }

    func start() {
        guard manager == nil else { return }
        let token: String
        do {
            guard let stored = try keychain.token(for: connection.id) else {
                state = .failed(.missingToken)
                return
            }
            token = stored
        } catch {
            state = .failed(.missingToken)
            Log.connection.error("Reading tab catalog credentials failed: \(error.localizedDescription, privacy: .public)")
            return
        }
        let manager = makeManager()
        self.manager = manager
        generation += 1
        let current = generation
        state = .connecting
        stateTask = Task { [weak self] in
            for await state in await manager.stateUpdates() {
                guard let self, !Task.isCancelled, current == self.generation else { return }
                self.state = state
                if state == .connected {
                    self.reload()
                }
            }
        }
        eventsTask = Task { [weak self] in
            for await event in await manager.events() {
                guard let self, !Task.isCancelled, current == self.generation else { return }
                self.receive(event)
            }
        }
        connectionTask = Task { [weak self, connection] in
            guard !Task.isCancelled else { return }
            await manager.ensureConnected(connection: connection, token: token)
            guard !Task.isCancelled, self?.generation == current else {
                await manager.disconnect()
                return
            }
        }
    }

    func stop() {
        generation += 1
        connectionTask?.cancel()
        connectionTask = nil
        stateTask?.cancel()
        stateTask = nil
        eventsTask?.cancel()
        eventsTask = nil
        refresh.cancel()
        let closing = manager
        manager = nil
        state = .idle
        Task { await closing?.disconnect() }
    }

    func retry() {
        stop()
        start()
    }

    func reload() {
        guard let manager, state == .connected else { return }
        let current = generation
        refresh.request { [weak self] in
            await self?.load(using: manager, generation: current)
        }
    }

    func createTab(in projectID: UUID) async throws -> Tab {
        guard let manager, state == .connected, projects.contains(where: { $0.id == projectID }) else {
            throw ConnectionError.notConnected
        }
        let current = generation
        let workspaceParams = GetWorkspaceParams(projectID: projectID.uuidString)
        let workspaceResult: RawTagged
        var initializedWorkspace = false
        do {
            workspaceResult = try await manager.request(.getWorkspace, params: workspaceParams)
        } catch let error as ProtocolError where error.code == .notFound {
            guard current == generation, !Task.isCancelled else { throw CancellationError() }
            _ = try await manager.request(.selectProject, params: SelectProjectParams(projectID: projectID.uuidString))
            guard current == generation, !Task.isCancelled else { throw CancellationError() }
            workspaceResult = try await manager.request(.getWorkspace, params: workspaceParams)
            initializedWorkspace = true
        }
        guard current == generation, !Task.isCancelled else { throw CancellationError() }
        let workspace = try workspaceResult.decode(Workspace.self)
        guard workspace.projectID == projectID else {
            throw ProtocolError(ProtocolErrorBody(code: ErrorCode.internalError.rawValue, message: "The device returned a different project's workspace."))
        }
        let area = WorkspaceFlattening.focusedTabArea(in: workspace)
        if initializedWorkspace, let tab = area?.tabs.first(where: { $0.kind == .terminal }) {
            reload()
            return tab
        }
        let params = CreateTabParams(projectID: projectID.uuidString, areaID: area?.id.uuidString, kind: TabKind.terminal.rawValue)
        let result = try await manager.request(.createTab, params: params)
        guard current == generation, !Task.isCancelled else { throw CancellationError() }
        let tab = try result.decode(Tab.self)
        reload()
        return tab
    }

    func closeTab(_ tabID: UUID, in projectID: UUID) {
        guard let manager, state == .connected,
              let workspace = workspaces[projectID],
              let area = WorkspaceFlattening.area(containing: tabID, in: workspace),
              let updated = WorkspaceFlattening.removingTab(tabID, from: workspace) else { return }
        workspaces[projectID] = updated
        let params = CloseTabParams(projectID: projectID.uuidString, areaID: area.id.uuidString, tabID: tabID.uuidString)
        Task { [weak self] in
            do {
                _ = try await manager.request(.closeTab, params: params)
            } catch {
                Log.client.error("Failed to close tab: \(error.localizedDescription, privacy: .public)")
                self?.reload()
            }
        }
    }

    private func load(using manager: ConnectionManager, generation current: Int) async {
        do {
            let result = try await manager.request(.listProjects)
            let listed = try result.decode(ProjectsResult.self).projects.sorted { $0.sortOrder < $1.sortOrder }
            guard current == generation, !Task.isCancelled else { return }
            projects = listed
            let ids = Set(listed.map(\.id))
            workspaces = workspaces.filter { ids.contains($0.key) }
            workspaceRevisions = workspaceRevisions.filter { ids.contains($0.key) }
            var failed = false
            for project in listed {
                guard current == generation, !Task.isCancelled else { return }
                let succeeded = await loadWorkspace(project.id, using: manager, generation: current)
                failed = failed || !succeeded
            }
            guard current == generation, !Task.isCancelled else { return }
            hasLoaded = true
            loadFailed = failed
        } catch {
            guard current == generation, !Task.isCancelled else { return }
            loadFailed = true
            Log.client.error("Loading the tab catalog failed: \(String(describing: error), privacy: .public)")
        }
    }

    private func loadWorkspace(_ projectID: UUID, using manager: ConnectionManager, generation current: Int) async -> Bool {
        let revision = workspaceRevisions[projectID, default: 0]
        do {
            let result = try await manager.request(.getWorkspace, params: GetWorkspaceParams(projectID: projectID.uuidString))
            let workspace = try result.decode(Workspace.self)
            guard current == generation, !Task.isCancelled else { return false }
            guard workspace.projectID == projectID else { return false }
            if workspaceRevisions[projectID, default: 0] == revision {
                workspaces[projectID] = workspace
            }
            return true
        } catch let error as ProtocolError where error.code == .notFound {
            guard current == generation, !Task.isCancelled else { return false }
            if workspaceRevisions[projectID, default: 0] == revision {
                workspaces[projectID] = nil
            }
            return true
        } catch {
            guard current == generation, !Task.isCancelled else { return false }
            Log.client.error("Loading project tabs failed: \(String(describing: error), privacy: .public)")
            return false
        }
    }

    private func receive(_ event: EventEnvelope) {
        if event.event == EventName.projectsChanged {
            reload()
            return
        }
        guard event.event == EventName.workspaceChanged, let data = event.data, data.type == EventType.workspace else { return }
        do {
            let workspace = try data.decode(Workspace.self)
            guard projects.contains(where: { $0.id == workspace.projectID }) else {
                reload()
                return
            }
            workspaceRevisions[workspace.projectID, default: 0] += 1
            workspaces[workspace.projectID] = workspace
        } catch {
            Log.client.error("Reading a tab catalog update failed: \(error.localizedDescription, privacy: .public)")
        }
    }
}
