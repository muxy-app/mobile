import Foundation

actor DemoBackend {
    private let clientID = UUID(uuidString: "00000000-0000-4000-8000-000000000101")!
    private let muxyProjectID = UUID(uuidString: "00000000-0000-4000-8000-000000000201")!
    private let webProjectID = UUID(uuidString: "00000000-0000-4000-8000-000000000202")!
    private let muxyWorktreeID = UUID(uuidString: "00000000-0000-4000-8000-000000000301")!
    private let webWorktreeID = UUID(uuidString: "00000000-0000-4000-8000-000000000302")!
    private let muxyAreaID = UUID(uuidString: "00000000-0000-4000-8000-000000000401")!
    private let webAreaID = UUID(uuidString: "00000000-0000-4000-8000-000000000402")!
    private let muxyTabID = UUID(uuidString: "00000000-0000-4000-8000-000000000501")!
    private let webTabID = UUID(uuidString: "00000000-0000-4000-8000-000000000502")!
    private let muxyPaneID = UUID(uuidString: "00000000-0000-4000-8000-000000000601")!
    private let webPaneID = UUID(uuidString: "00000000-0000-4000-8000-000000000602")!
    private var workspaces: [UUID: Workspace] = [:]
    private var tabCounter = 2

    init() {
        workspaces = [
            muxyProjectID: Self.makeWorkspace(
                projectID: muxyProjectID,
                worktreeID: muxyWorktreeID,
                areaID: muxyAreaID,
                path: "/Users/demo/Projects/muxy",
                tab: Tab(id: muxyTabID, kind: .terminal, title: "zsh", isPinned: false, paneID: muxyPaneID)
            ),
            webProjectID: Self.makeWorkspace(
                projectID: webProjectID,
                worktreeID: webWorktreeID,
                areaID: webAreaID,
                path: "/Users/demo/Projects/web-app",
                tab: Tab(id: webTabID, kind: .terminal, title: "dev", isPinned: false, paneID: webPaneID)
            )
        ]
    }

    var currentClientID: UUID { clientID }

    var currentTheme: DeviceThemeEvent {
        DeviceThemeEvent(
            fg: 0xe5e7eb,
            bg: 0x101216,
            palette: [
                0x1f2937, 0xef4444, 0x22c55e, 0xeab308,
                0x3b82f6, 0xa855f7, 0x06b6d4, 0xf9fafb,
                0x4b5563, 0xf87171, 0x4ade80, 0xfacc15,
                0x60a5fa, 0xc084fc, 0x22d3ee, 0xffffff
            ]
        )
    }

    func authenticate() throws -> RawTagged {
        try tagged(
            ResultType.pairing,
            PairingResult(
                clientID: clientID.uuidString,
                deviceName: DemoDevice.device.name,
                themeFg: Int(currentTheme.fg),
                themeBg: Int(currentTheme.bg),
                themePalette: currentTheme.palette?.map(Int.init)
            )
        )
    }

    func request<P: Codable & Sendable>(_ method: Method, params: P?) throws -> RawTagged {
        switch method {
        case .authenticateDevice:
            return try authenticate()
        case .listProjects:
            return try tagged(ResultType.projects, ProjectsResult(projects: projects))
        case .selectProject:
            return try tagged(ResultType.ok, EmptyDemoResult())
        case .getWorkspace:
            let params = try decode(GetWorkspaceParams.self, from: params)
            let id = try projectID(from: params.projectID)
            guard let workspace = workspaces[id] else { throw DemoError.notFound }
            return try tagged(ResultType.workspace, workspace)
        case .createTab:
            let params = try decode(CreateTabParams.self, from: params)
            let projectID = try projectID(from: params.projectID)
            let tab = try createTab(projectID: projectID, areaID: params.areaID)
            return try tagged(ResultType.tab, tab)
        case .closeTab:
            let params = try decode(CloseTabParams.self, from: params)
            try closeTab(projectID: projectID(from: params.projectID), areaID: areaID(from: params.areaID), tabID: tabID(from: params.tabID))
            return try tagged(ResultType.ok, EmptyDemoResult())
        case .selectTab:
            let params = try decode(SelectTabParams.self, from: params)
            try selectTab(projectID: projectID(from: params.projectID), areaID: areaID(from: params.areaID), tabID: tabID(from: params.tabID))
            return try tagged(ResultType.ok, EmptyDemoResult())
        case .takeOverPane, .terminalResize, .terminalScroll, .setClientTheme, .releasePane:
            return try tagged(ResultType.ok, EmptyDemoResult())
        case .getProjectLogo:
            throw DemoError.notFound
        case .terminalInput:
            return try tagged(ResultType.ok, EmptyDemoResult())
        case .pairDevice:
            throw DemoError.notFound
        }
    }

    func events<P: Codable & Sendable>(for method: Method, params: P?) throws -> [EventEnvelope] {
        switch method {
        case .createTab:
            let params = try decode(CreateTabParams.self, from: params)
            return try workspaceEvents(projectID: projectID(from: params.projectID))
        case .closeTab:
            let params = try decode(CloseTabParams.self, from: params)
            return try workspaceEvents(projectID: projectID(from: params.projectID))
        case .selectTab:
            let params = try decode(SelectTabParams.self, from: params)
            return try workspaceEvents(projectID: projectID(from: params.projectID))
        case .takeOverPane:
            let params = try decode(TakeOverPaneParams.self, from: params)
            let paneID = try paneID(from: params.paneID)
            return [
                try event(EventName.paneOwnershipChanged, EventType.paneOwnership, PaneOwnershipEvent(
                    paneID: paneID,
                    owner: .remote(deviceID: clientID, deviceName: "iPhone (Demo)")
                )),
                try event(EventName.terminalSnapshot, EventType.terminalSnapshot, TerminalBytesEvent(
                    paneID: paneID,
                    bytes: Data(snapshotText.utf8)
                ))
            ]
        case .terminalInput:
            let params = try decode(TerminalInputParams.self, from: params)
            let text = String(data: params.bytes, encoding: .utf8) ?? ""
            let output = response(for: text)
            return [
                try event(EventName.terminalOutput, EventType.terminalOutput, TerminalBytesEvent(
                    paneID: try paneID(from: params.paneID),
                    bytes: Data(output.utf8)
                ))
            ]
        default:
            return []
        }
    }

    private var projects: [Project] {
        [
            Project(
                id: muxyProjectID,
                name: "Muxy",
                path: "/Users/demo/Projects/muxy",
                sortOrder: 0,
                createdAt: "2026-06-08T00:00:00.000Z",
                icon: "terminal",
                logo: nil,
                iconColor: "#22c55e",
                preferredWorktreeParentPath: "/Users/demo/Projects"
            ),
            Project(
                id: webProjectID,
                name: "Web App",
                path: "/Users/demo/Projects/web-app",
                sortOrder: 1,
                createdAt: "2026-06-08T00:00:00.000Z",
                icon: "globe",
                logo: nil,
                iconColor: "#3b82f6",
                preferredWorktreeParentPath: "/Users/demo/Projects"
            )
        ]
    }

    private var snapshotText: String {
        "\u{001B}[1;32mDemo Mode\u{001B}[0m - this terminal is simulated.\r\nType any command and press Enter to see the demo response.\r\ndemo@muxy ~ % "
    }

    private func response(for text: String) -> String {
        guard text.contains("\r") || text.contains("\n") else { return text }
        let command = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let body = command.isEmpty ? "" : "\r\n\u{001B}[33m[Demo Mode]\u{001B}[0m Commands are not executed in demo mode.\r\n"
        return "\(text)\(body)demo@muxy ~ % "
    }

    private func workspaceEvents(projectID: UUID) throws -> [EventEnvelope] {
        guard let workspace = workspaces[projectID] else { throw DemoError.notFound }
        return [try event(EventName.workspaceChanged, EventType.workspace, workspace)]
    }

    private func createTab(projectID: UUID, areaID: String?) throws -> Tab {
        guard let workspace = workspaces[projectID], case let .tabArea(area) = workspace.root else { throw DemoError.notFound }
        if let areaID, area.id != UUID(uuidString: areaID) { throw DemoError.notFound }
        tabCounter += 1
        let tab = Tab(
            id: UUID(),
            kind: .terminal,
            title: "zsh \(tabCounter)",
            isPinned: false,
            paneID: UUID()
        )
        let updatedArea = TabArea(
            id: area.id,
            projectPath: area.projectPath,
            tabs: area.tabs + [tab],
            activeTabID: tab.id
        )
        workspaces[projectID] = Workspace(
            projectID: workspace.projectID,
            worktreeID: workspace.worktreeID,
            focusedAreaID: updatedArea.id,
            root: .tabArea(updatedArea)
        )
        return tab
    }

    private func closeTab(projectID: UUID, areaID: UUID, tabID: UUID) throws {
        guard let workspace = workspaces[projectID], case let .tabArea(area) = workspace.root, area.id == areaID else { throw DemoError.notFound }
        let tabs = area.tabs.filter { $0.id != tabID }
        let updatedArea = TabArea(
            id: area.id,
            projectPath: area.projectPath,
            tabs: tabs,
            activeTabID: area.activeTabID == tabID ? tabs.first?.id : area.activeTabID
        )
        workspaces[projectID] = Workspace(projectID: projectID, worktreeID: workspace.worktreeID, focusedAreaID: areaID, root: .tabArea(updatedArea))
    }

    private func selectTab(projectID: UUID, areaID: UUID, tabID: UUID) throws {
        guard let workspace = workspaces[projectID], case let .tabArea(area) = workspace.root, area.id == areaID else { throw DemoError.notFound }
        guard area.tabs.contains(where: { $0.id == tabID }) else { throw DemoError.notFound }
        let updatedArea = TabArea(id: area.id, projectPath: area.projectPath, tabs: area.tabs, activeTabID: tabID)
        workspaces[projectID] = Workspace(projectID: projectID, worktreeID: workspace.worktreeID, focusedAreaID: areaID, root: .tabArea(updatedArea))
    }

    private static func makeWorkspace(projectID: UUID, worktreeID: UUID, areaID: UUID, path: String, tab: Tab) -> Workspace {
        let area = TabArea(id: areaID, projectPath: path, tabs: [tab], activeTabID: tab.id)
        return Workspace(projectID: projectID, worktreeID: worktreeID, focusedAreaID: areaID, root: .tabArea(area))
    }

    private func tagged<T: Encodable & Sendable>(_ type: String, _ value: T) throws -> RawTagged {
        try RawTagged(type: type, value: value)
    }

    private func event<T: Encodable & Sendable>(_ name: String, _ type: String, _ value: T) throws -> EventEnvelope {
        try EventEnvelope(event: name, data: RawTagged(type: type, value: value))
    }

    private func decode<T: Decodable, P: Encodable>(_ type: T.Type, from params: P?) throws -> T {
        guard let params else { throw DemoError.invalidParams }
        let data = try JSONEncoder().encode(params)
        return try JSONDecoder().decode(type, from: data)
    }

    private func projectID(from value: String) throws -> UUID {
        guard let id = UUID(uuidString: value) else { throw DemoError.invalidParams }
        return id
    }

    private func areaID(from value: String) throws -> UUID {
        guard let id = UUID(uuidString: value) else { throw DemoError.invalidParams }
        return id
    }

    private func tabID(from value: String) throws -> UUID {
        guard let id = UUID(uuidString: value) else { throw DemoError.invalidParams }
        return id
    }

    private func paneID(from value: String) throws -> UUID {
        guard let id = UUID(uuidString: value) else { throw DemoError.invalidParams }
        return id
    }
}

nonisolated private struct EmptyDemoResult: Codable, Sendable {}

enum DemoError: Error, Sendable {
    case invalidParams
    case notFound
}
