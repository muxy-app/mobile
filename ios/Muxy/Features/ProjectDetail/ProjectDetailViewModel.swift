import Foundation
import Observation
import OSLog

@MainActor
@Observable
final class ProjectDetailViewModel {
    let device: Device
    let project: Project

    private(set) var state: ConnectionState = .idle
    private(set) var workspace: Workspace?
    private(set) var area: TabArea?
    private(set) var hasLoaded = false
    var selectedTabID: UUID?

    private let keychain: KeychainStore
    private let connectionManager: ConnectionManager
    private let sessionStore: TerminalSessionStore
    private var observationTask: Task<Void, Never>?
    private var eventsTask: Task<Void, Never>?

    init(
        device: Device,
        project: Project,
        keychain: KeychainStore,
        connectionManager: ConnectionManager,
        sessionStore: TerminalSessionStore
    ) {
        self.device = device
        self.project = project
        self.keychain = keychain
        self.connectionManager = connectionManager
        self.sessionStore = sessionStore
    }

    func terminalSession(for tab: Tab) -> TerminalSession? {
        sessionStore.session(for: tab)
    }

    var tabs: [Tab] {
        area?.tabs ?? []
    }

    var projectName: String {
        project.name
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
        sessionStore.teardown()
    }

    func select(_ tab: Tab) {
        guard selectedTabID != tab.id else { return }
        selectedTabID = tab.id
        sessionStore.selectionChanged(to: selectedTabID, tabs: tabs)
        guard let areaID = area?.id else { return }
        sendSelectTab(areaID: areaID, tabID: tab.id)
    }

    func createTab() {
        let params = CreateTabParams(
            projectID: project.id.uuidString,
            areaID: area?.id.uuidString,
            kind: TabKind.terminal.rawValue
        )
        Task { [connectionManager] in
            do {
                let result = try await connectionManager.request(.createTab, params: params)
                guard result.type == ResultType.tab else { return }
                let tab = try result.decode(Tab.self)
                await self.applyCreatedTab(tab)
            } catch {
                Log.client.error("Failed to create tab: \(error.localizedDescription, privacy: .public)")
            }
        }
    }

    func closeTab(_ tab: Tab) {
        guard let areaID = area?.id else { return }
        applyOptimisticClose(of: tab.id)
        let params = CloseTabParams(
            projectID: project.id.uuidString,
            areaID: areaID.uuidString,
            tabID: tab.id.uuidString
        )
        Task { [connectionManager] in
            do {
                _ = try await connectionManager.request(.closeTab, params: params)
            } catch {
                Log.client.error("Failed to close tab: \(error.localizedDescription, privacy: .public)")
            }
        }
    }

    private func observeState() {
        guard observationTask == nil else { return }
        observationTask = Task { [weak self, connectionManager] in
            var previous: ConnectionState?
            for await state in await connectionManager.stateUpdates() {
                guard let self else { return }
                self.state = state
                self.sessionStore.connectionStateChanged(state)
                if state == .connected, previous != .connected {
                    await self.loadWorkspace()
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
                guard event.event == EventName.workspaceChanged else { continue }
                self.applyWorkspaceEvent(event)
            }
        }
    }

    private func loadWorkspace() async {
        let selectParams = SelectProjectParams(projectID: project.id.uuidString)
        _ = try? await connectionManager.request(.selectProject, params: selectParams)

        do {
            let params = GetWorkspaceParams(projectID: project.id.uuidString)
            let result = try await connectionManager.request(.getWorkspace, params: params)
            guard result.type == ResultType.workspace else { return }
            apply(try result.decode(Workspace.self))
            hasLoaded = true
        } catch let error as ProtocolError where error.code == .notFound {
            workspace = nil
            area = nil
            hasLoaded = true
        } catch {
            Log.client.error("Failed to load workspace: \(String(describing: error), privacy: .public)")
        }
    }

    private func applyWorkspaceEvent(_ event: EventEnvelope) {
        guard let data = event.data, data.type == EventType.workspace else { return }
        do {
            apply(try data.decode(Workspace.self))
            hasLoaded = true
        } catch {
            Log.client.error("Failed to decode workspace event: \(error.localizedDescription, privacy: .public)")
        }
    }

    private func apply(_ workspace: Workspace) {
        guard workspace.projectID == project.id else { return }
        self.workspace = workspace
        area = WorkspaceFlattening.focusedTabArea(in: workspace)
        reconcileSelection()
        sessionStore.tabsChanged(tabs)
        sessionStore.selectionChanged(to: selectedTabID, tabs: tabs)
    }

    private func applyCreatedTab(_ tab: Tab) {
        selectedTabID = tab.id
    }

    private func applyOptimisticClose(of tabID: UUID) {
        guard let current = area else { return }
        let remaining = current.tabs.filter { $0.id != tabID }
        let nextActive = current.activeTabID == tabID ? remaining.first?.id : current.activeTabID
        area = TabArea(
            id: current.id,
            projectPath: current.projectPath,
            tabs: remaining,
            activeTabID: nextActive
        )
        if selectedTabID == tabID {
            selectedTabID = remaining.first?.id
        }
        sessionStore.tabsChanged(tabs)
        sessionStore.selectionChanged(to: selectedTabID, tabs: tabs)
    }

    private func reconcileSelection() {
        let ids = tabs.map(\.id)
        if let selectedTabID, ids.contains(selectedTabID) { return }
        selectedTabID = area?.activeTabID ?? tabs.first?.id
    }

    private func sendSelectTab(areaID: UUID, tabID: UUID) {
        let params = SelectTabParams(
            projectID: project.id.uuidString,
            areaID: areaID.uuidString,
            tabID: tabID.uuidString
        )
        Task { [connectionManager] in
            do {
                _ = try await connectionManager.request(.selectTab, params: params)
            } catch {
                Log.client.error("Failed to select tab: \(error.localizedDescription, privacy: .public)")
            }
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
