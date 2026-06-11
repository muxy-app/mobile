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
    private var gitStatuses: [UUID: VCSStatus] = [:]
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
        gitStatuses = [
            muxyProjectID: Self.makeStatus(branch: "main", hasChanges: true),
            webProjectID: Self.makeStatus(branch: "feature/native-git", hasChanges: false)
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
                deviceName: DemoConnection.connection.name,
                themeFg: Int(currentTheme.fg),
                themeBg: Int(currentTheme.bg),
                themePalette: currentTheme.palette?.map(Int.init)
            )
        )
    }

    func request<P: Codable & Sendable>(_ method: Method, params: P?) throws -> RawTagged {
        if let result = try handleProject(method, params: params) { return result }
        if let result = try handleTab(method, params: params) { return result }
        if let result = try handleTerminal(method) { return result }
        if let result = try handleVCS(method, params: params) { return result }
        throw DemoError.notFound
    }

    private func handleProject<P: Codable & Sendable>(_ method: Method, params: P?) throws -> RawTagged? {
        switch method {
        case .authenticateDevice:
            return try authenticate()
        case .listProjects:
            return try tagged(ResultType.projects, ProjectsResult(projects: projects))
        case .selectProject, .selectWorktree:
            return try tagged(ResultType.ok, EmptyDemoResult())
        case .listWorktrees:
            let params = try decode(ListWorktreesParams.self, from: params)
            return try tagged(ResultType.worktrees, worktrees(for: projectID(from: params.projectID)))
        case .getWorkspace:
            let params = try decode(GetWorkspaceParams.self, from: params)
            let id = try projectID(from: params.projectID)
            guard let workspace = workspaces[id] else { throw DemoError.notFound }
            return try tagged(ResultType.workspace, workspace)
        default:
            return nil
        }
    }

    private func handleTab<P: Codable & Sendable>(_ method: Method, params: P?) throws -> RawTagged? {
        switch method {
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
        default:
            return nil
        }
    }

    private func handleTerminal(_ method: Method) throws -> RawTagged? {
        switch method {
        case .takeOverPane, .terminalResize, .terminalScroll, .setClientTheme, .releasePane, .terminalInput:
            return try tagged(ResultType.ok, EmptyDemoResult())
        case .getProjectLogo:
            throw DemoError.notFound
        default:
            return nil
        }
    }

    private func handleVCS<P: Codable & Sendable>(_ method: Method, params: P?) throws -> RawTagged? {
        switch method {
        case .vcsRefresh:
            let params = try decode(VCSProjectParams.self, from: params)
            return try tagged(ResultType.vcsStatus, gitStatus(for: projectID(from: params.projectID)))
        case .vcsCommit:
            let params = try decode(VCSCommitParams.self, from: params)
            let projectID = try projectID(from: params.projectID)
            gitStatuses[projectID] = Self.makeStatus(branch: gitStatus(for: projectID).branch, hasChanges: false)
            return try tagged(ResultType.ok, EmptyDemoResult())
        case .vcsPush, .vcsPull, .vcsMergePullRequest, .vcsRemoveWorktree:
            return try tagged(ResultType.ok, EmptyDemoResult())
        case .vcsListBranches:
            let params = try decode(VCSProjectParams.self, from: params)
            let status = gitStatus(for: try projectID(from: params.projectID))
            return try tagged(ResultType.vcsBranches, VCSBranches(current: status.branch, locals: ["main", "feature/native-git"], defaultBranch: "main"))
        case .vcsSwitchBranch:
            let params = try decode(VCSBranchParams.self, from: params)
            let projectID = try projectID(from: params.projectID)
            gitStatuses[projectID] = Self.makeStatus(branch: params.branch, hasChanges: gitStatus(for: projectID).changedFiles.isEmpty == false)
            return try tagged(ResultType.ok, EmptyDemoResult())
        case .vcsCreateBranch:
            let params = try decode(VCSCreateBranchParams.self, from: params)
            let projectID = try projectID(from: params.projectID)
            gitStatuses[projectID] = Self.makeStatus(branch: params.name, hasChanges: gitStatus(for: projectID).changedFiles.isEmpty == false)
            return try tagged(ResultType.ok, EmptyDemoResult())
        case .vcsCreatePR:
            return try tagged(ResultType.vcsPRCreated, VCSPRCreated(url: "https://github.com/muxy-app/demo/pull/42", number: 42))
        case .vcsAddWorktree:
            let params = try decode(VCSAddWorktreeParams.self, from: params)
            return try tagged(ResultType.worktrees, worktrees(for: projectID(from: params.projectID)))
        case .vcsGetDiff:
            let params = try decode(VCSGetDiffParams.self, from: params)
            return try tagged(ResultType.vcsDiff, Self.makeDiff(filePath: params.filePath, truncated: !params.forceFull))
        default:
            return nil
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

    private func gitStatus(for projectID: UUID) -> VCSStatus {
        gitStatuses[projectID] ?? Self.makeStatus(branch: "main", hasChanges: false)
    }

    private func worktrees(for projectID: UUID) -> [Worktree] {
        if projectID == webProjectID {
            return [
                Worktree(
                    id: webWorktreeID,
                    name: "Web App",
                    path: "/Users/demo/Projects/web-app",
                    branch: "feature/native-git",
                    isPrimary: true,
                    canBeRemoved: false,
                    createdAt: "2026-06-08T00:00:00.000Z"
                )
            ]
        }
        return [
            Worktree(
                id: muxyWorktreeID,
                name: "Muxy",
                path: "/Users/demo/Projects/muxy",
                branch: "main",
                isPrimary: true,
                canBeRemoved: false,
                createdAt: "2026-06-08T00:00:00.000Z"
            )
        ]
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

    private static func makeStatus(branch: String, hasChanges: Bool) -> VCSStatus {
        VCSStatus(
            branch: branch,
            aheadCount: branch == "main" ? 0 : 2,
            behindCount: 0,
            hasUpstream: true,
            stagedFiles: hasChanges ? [GitFile(path: "ios/Muxy/Features/Git/GitSheetView.swift", status: .added, isUntracked: false)] : [],
            changedFiles: hasChanges ? [GitFile(path: "ios/Muxy/Networking/Protocol/Methods.swift", status: .modified, isUntracked: false)] : [],
            defaultBranch: "main",
            pullRequest: branch == "main" ? nil : VCSPullRequest(
                url: "https://github.com/muxy-app/demo/pull/42",
                number: 42,
                state: "OPEN",
                isDraft: false,
                baseBranch: "main",
                mergeable: true,
                mergeStateStatus: .clean,
                checks: VCSPRChecks(status: .success, passing: 4, failing: 0, pending: 0, total: 4)
            )
        )
    }

    private static func makeDiff(filePath: String, truncated: Bool) -> VCSDiff {
        VCSDiff(
            filePath: filePath,
            rows: [
                VCSDiffRow(kind: .hunk, oldLineNumber: nil, newLineNumber: nil, text: "@@ -1,3 +1,4 @@"),
                VCSDiffRow(kind: .context, oldLineNumber: 1, newLineNumber: 1, text: " import SwiftUI"),
                VCSDiffRow(kind: .addition, oldLineNumber: nil, newLineNumber: 2, text: "+struct GitOverviewView: View {"),
                VCSDiffRow(kind: .addition, oldLineNumber: nil, newLineNumber: 3, text: "+    let viewModel: GitViewModel"),
                VCSDiffRow(kind: .deletion, oldLineNumber: 2, newLineNumber: nil, text: "-struct PlaceholderView: View {"),
                VCSDiffRow(kind: .context, oldLineNumber: 3, newLineNumber: 4, text: " }")
            ],
            additions: 2,
            deletions: 1,
            truncated: truncated,
            isBinary: false
        )
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
