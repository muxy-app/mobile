import Foundation
import MuxyMobile
import Observation
import OSLog

@MainActor
@Observable
final class TabsHomeViewModel {
    private(set) var connections: [Connection] = []
    private(set) var isCreating = false
    var notice: String?

    private var deviceCatalogs: [UUID: DeviceTabCatalog] = [:]
    private var servers: [String: ServerController] = [:]
    @ObservationIgnored private let store: ConnectionStore
    @ObservationIgnored private let keychain: KeychainStore
    @ObservationIgnored private let directory: ServerDirectory
    @ObservationIgnored private let makeManager: () -> ConnectionManager
    @ObservationIgnored private var isVisible = false
    @ObservationIgnored private var isForeground = true

    init(
        store: ConnectionStore,
        keychain: KeychainStore,
        directory: ServerDirectory,
        makeManager: @escaping () -> ConnectionManager
    ) {
        self.store = store
        self.keychain = keychain
        self.directory = directory
        self.makeManager = makeManager
    }

    var devices: [TabsHomeDevice] {
        connections.map { connection in
            switch connection.kind {
            case .device: deviceListing(connection)
            case .server: serverListing(connection)
            case .ssh:
                TabsHomeDevice(
                    id: connection.id,
                    name: connection.name,
                    systemImage: "terminal",
                    status: .ssh,
                    projects: [],
                    canEdit: true
                )
            }
        }
    }

    func load() {
        let saved = store.load()
        for (id, catalog) in deviceCatalogs where !saved.contains(catalog.connection) {
            catalog.stop()
            deviceCatalogs[id] = nil
        }
        connections = saved
        for connection in saved where connection.kind == .device && deviceCatalogs[connection.id] == nil {
            deviceCatalogs[connection.id] = DeviceTabCatalog(connection: connection, keychain: keychain, makeManager: makeManager)
        }
        let serverIDs = Set(saved.compactMap(\.serverRouteID))
        servers = servers.filter { serverIDs.contains($0.key) }
        for serverID in serverIDs {
            servers[serverID] = directory.controller(for: serverID)
        }
        updateActivity()
    }

    func setVisible(_ visible: Bool) {
        isVisible = visible
        if visible {
            load()
            return
        }
        updateActivity()
    }

    func setForeground(_ foreground: Bool) {
        isForeground = foreground
        updateActivity()
    }

    func connection(_ id: UUID) -> Connection? {
        connections.first { $0.id == id }
    }

    func route(deviceID: UUID, projectID: String, tabID: UUID) -> AppRoute? {
        guard let connection = connection(deviceID) else { return nil }
        switch connection.kind {
        case .device:
            guard let project = deviceCatalogs[deviceID]?.projects.first(where: { $0.id.uuidString == projectID }) else { return nil }
            return .projectDetail(connection: connection, project: project, tabID: tabID)
        case .server:
            return .serverProject(connection: connection, projectID: projectID, tabID: tabID)
        case .ssh:
            return .sshTerminal(connection)
        }
    }

    func createTab(deviceID: UUID, projectID: String) async -> AppRoute? {
        guard isVisible, isForeground, !isCreating, let connection = connection(deviceID) else { return nil }
        isCreating = true
        defer { isCreating = false }
        do {
            switch connection.kind {
            case .device:
                guard let catalog = deviceCatalogs[deviceID],
                      let project = catalog.projects.first(where: { $0.id.uuidString == projectID }) else { return nil }
                let tab = try await catalog.createTab(in: project.id)
                guard isVisible, isForeground, self.connection(deviceID) == connection else { return nil }
                return .projectDetail(connection: connection, project: project, tabID: tab.id)
            case .server:
                guard let serverID = connection.serverRouteID,
                      let server = servers[serverID], server.phase == .connected,
                      server.project(for: projectID) != nil,
                      let tab = server.projectModel(for: projectID).createTab() else {
                    throw ConnectionError.notConnected
                }
                return .serverProject(connection: connection, projectID: projectID, tabID: tab.id)
            case .ssh:
                return .sshTerminal(connection)
            }
        } catch is CancellationError {
            return nil
        } catch {
            notice = "The tab could not be created. Check the device connection and try again."
            Log.client.error("Creating a tab from home failed: \(String(describing: error), privacy: .public)")
            return nil
        }
    }

    func retry(_ deviceID: UUID) {
        guard isVisible, isForeground, let connection = connection(deviceID) else { return }
        if let catalog = deviceCatalogs[deviceID] {
            catalog.retry()
            return
        }
        guard let serverID = connection.serverRouteID, let server = servers[serverID] else { return }
        if server.phase == .connected {
            server.reloadProjects()
            return
        }
        server.retryNow()
    }

    private func updateActivity() {
        let active = isVisible && isForeground
        directory.setBrowsingServers(active ? Set(servers.keys) : [])
        for catalog in deviceCatalogs.values {
            if active {
                catalog.start()
            } else {
                catalog.stop()
            }
        }
    }

    private func deviceListing(_ connection: Connection) -> TabsHomeDevice {
        let catalog = deviceCatalogs[connection.id]
        let status = deviceStatus(catalog)
        let projects: [TabsHomeProject] = catalog?.projects.map { project in
            let areas = catalog?.workspaces[project.id].map(WorkspaceFlattening.tabAreas(in:)) ?? []
            return TabsHomeProject(
                id: project.id.uuidString,
                name: project.name,
                path: project.path,
                color: project.iconColor,
                tabs: areas.flatMap { area in area.tabs.map { Self.listing(of: $0, in: area) } },
                canCreate: status == .connected && catalog?.loadFailed == false
            )
        } ?? []
        return TabsHomeDevice(
            id: connection.id,
            name: connection.name,
            systemImage: "desktopcomputer",
            status: status,
            projects: projects,
            canEdit: connection.id != DemoConnection.id
        )
    }

    private static func listing(of tab: Tab, in area: TabArea) -> TabsHomeTab {
        let title = TerminalTitle.sanitized(tab.title)
        return TabsHomeTab(
            id: tab.id,
            title: title.isEmpty ? "Terminal" : title,
            detail: tab.kind == .vcs ? "Source control" : DisplayPath.abbreviated(area.projectPath),
            kind: tab.tileKind,
            activity: .none
        )
    }

    private static func listing(of terminal: TerminalController) -> TabsHomeTab {
        let activity = terminal.activity
        let location = DisplayPath.abbreviated(terminal.directory)
        let detail = activity.isAgent ? [activity.statusLabel, activity.state == .idle ? nil : activity.providerName].compactMap { $0 }.joined(separator: " · ") : location
        return TabsHomeTab(id: terminal.id, title: terminal.title, detail: detail, kind: terminal.tileKind, activity: activity)
    }

    private func deviceStatus(_ catalog: DeviceTabCatalog?) -> TabsHomeStatus {
        guard let catalog else { return .loading }
        switch catalog.state {
        case .idle, .connecting, .authenticating: return .loading
        case .connected: return catalog.hasLoaded || catalog.loadFailed ? .connected : .loading
        case .failed(.missingToken), .failed(.authenticationFailed): return .needsPairing
        case .disconnected, .failed: return .offline
        }
    }


    private func serverListing(_ connection: Connection) -> TabsHomeDevice {
        let server = connection.serverRouteID.flatMap { servers[$0] }
        let status = serverStatus(server)
        let projects: [TabsHomeProject] = server?.projectRows.map { row in
            let project = row.project
            let model = server?.listedProjectModel(for: project.id)
            return TabsHomeProject(
                id: project.id,
                name: project.name,
                path: project.directory,
                color: project.color,
                tabs: model?.tabs.map(Self.listing(of:)) ?? [],
                canCreate: status == .connected && model?.hasLoadedSessions == true && model?.sessionsLoadFailed == false
            )
        } ?? []
        return TabsHomeDevice(
            id: connection.id,
            name: connection.name,
            systemImage: "desktopcomputer",
            status: status,
            projects: projects,
            canEdit: true
        )
    }

    private func serverStatus(_ server: ServerController?) -> TabsHomeStatus {
        guard let server else { return .needsPairing }
        switch server.phase {
        case .idle, .connecting, .reconnecting(.serverRestarting): return .loading
        case .failed, .reconnecting(.lost): return .offline
        case .connected:
            if server.projectsLoadFailed { return .connected }
            guard server.hasLoadedProjects else { return .loading }
            let loading = server.projects.contains { project in
                guard let model = server.listedProjectModel(for: project.id) else { return true }
                return !model.hasLoadedSessions && !model.sessionsLoadFailed
            }
            return loading ? .loading : .connected
        }
    }
}
