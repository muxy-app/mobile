import Foundation

enum Method: String, Sendable {
    case authenticateDevice
    case pairDevice
    case listProjects
    case selectProject
    case listWorktrees
    case selectWorktree
    case getWorkspace
    case createTab
    case closeTab
    case selectTab
    case getProjectLogo
    case takeOverPane
    case releasePane
    case setClientTheme
    case terminalInput
    case terminalResize
    case terminalScroll
    case vcsRefresh
    case vcsCommit
    case vcsPush
    case vcsPull
    case vcsListBranches
    case vcsSwitchBranch
    case vcsCreateBranch
    case vcsCreatePR
    case vcsMergePullRequest
    case vcsAddWorktree
    case vcsRemoveWorktree
    case vcsGetDiff
}

nonisolated enum ResultType {
    static let pairing = "pairing"
    static let projects = "projects"
    static let workspace = "workspace"
    static let tab = "tab"
    static let ok = "ok"
    static let projectLogo = "projectLogo"
    static let worktrees = "worktrees"
    static let vcsStatus = "vcsStatus"
    static let vcsBranches = "vcsBranches"
    static let vcsPRCreated = "vcsPRCreated"
    static let vcsDiff = "vcsDiff"
}

nonisolated enum EventName {
    static let workspaceChanged = "workspaceChanged"
    static let projectsChanged = "projectsChanged"
    static let terminalOutput = "terminalOutput"
    static let terminalSnapshot = "terminalSnapshot"
    static let paneOwnershipChanged = "paneOwnershipChanged"
    static let themeChanged = "themeChanged"
}

nonisolated enum EventType {
    static let workspace = "workspace"
    static let projects = "projects"
    static let terminalOutput = "terminalOutput"
    static let terminalSnapshot = "terminalSnapshot"
    static let paneOwnership = "paneOwnership"
    static let deviceTheme = "deviceTheme"
}

nonisolated struct AuthParams: Codable, Sendable {
    let deviceID: String
    let deviceName: String
    let token: String
}

nonisolated struct PairingResult: Codable, Sendable {
    let clientID: String
    let deviceName: String
    let themeFg: Int?
    let themeBg: Int?
    let themePalette: [Int]?

    var pairing: Pairing {
        Pairing(
            clientID: clientID,
            deviceName: deviceName,
            themeForeground: themeFg,
            themeBackground: themeBg,
            themePalette: themePalette
        )
    }

    var deviceTheme: DeviceThemeEvent? {
        guard let themeFg, let themeBg else { return nil }
        return DeviceThemeEvent(
            fg: UInt32(truncatingIfNeeded: themeFg),
            bg: UInt32(truncatingIfNeeded: themeBg),
            palette: themePalette?.map { UInt32(truncatingIfNeeded: $0) }
        )
    }
}

nonisolated struct EmptyRequestParams: Codable, Sendable {}

nonisolated struct SelectProjectParams: Codable, Sendable {
    let projectID: String
}

nonisolated struct ListWorktreesParams: Codable, Sendable {
    let projectID: String
}

nonisolated struct SelectWorktreeParams: Codable, Sendable {
    let projectID: String
    let worktreeID: String
}

nonisolated struct GetWorkspaceParams: Codable, Sendable {
    let projectID: String
}

nonisolated struct CreateTabParams: Codable, Sendable {
    let projectID: String
    let areaID: String?
    let kind: String?
}

nonisolated struct CloseTabParams: Codable, Sendable {
    let projectID: String
    let areaID: String
    let tabID: String
}

nonisolated struct SelectTabParams: Codable, Sendable {
    let projectID: String
    let areaID: String
    let tabID: String
}

nonisolated struct GetProjectLogoParams: Codable, Sendable {
    let projectID: String
}

nonisolated struct ProjectLogoResult: Codable, Sendable {
    let projectID: String
    let pngData: String
}

nonisolated struct TakeOverPaneParams: Codable, Sendable {
    let paneID: String
    let cols: Int
    let rows: Int
}

nonisolated struct ReleasePaneParams: Codable, Sendable {
    let paneID: String
}

nonisolated struct SetClientThemeParams: Codable, Sendable, Equatable {
    let theme: ClientTerminalTheme?
}

nonisolated struct ClientTerminalTheme: Codable, Sendable, Equatable {
    let fg: UInt32
    let bg: UInt32
    let palette: [UInt32]
    let cursorColor: UInt32?
    let cursorText: UInt32?
    let selectionBackground: UInt32?
    let selectionForeground: UInt32?
}

nonisolated struct TerminalInputParams: Codable, Sendable {
    let paneID: String
    let bytes: Data
}

nonisolated struct TerminalResizeParams: Codable, Sendable {
    let paneID: String
    let cols: Int
    let rows: Int
}

nonisolated struct TerminalScrollParams: Codable, Sendable {
    let paneID: String
    let deltaX: Double
    let deltaY: Double
    let precise: Bool
}

nonisolated struct VCSProjectParams: Codable, Sendable {
    let projectID: String
}

nonisolated struct VCSCommitParams: Codable, Sendable {
    let projectID: String
    let message: String
    let stageAll: Bool
}

nonisolated struct VCSBranchParams: Codable, Sendable {
    let projectID: String
    let branch: String
}

nonisolated struct VCSCreateBranchParams: Codable, Sendable {
    let projectID: String
    let name: String
}

nonisolated struct VCSCreatePRParams: Codable, Sendable {
    let projectID: String
    let title: String
    let body: String
    let baseBranch: String?
    let draft: Bool
}

nonisolated struct VCSMergePullRequestParams: Codable, Sendable {
    let projectID: String
    let number: Int
    let method: VCSMergeMethod
    let deleteBranch: Bool
}

nonisolated struct VCSAddWorktreeParams: Codable, Sendable {
    let projectID: String
    let name: String
    let branch: String
    let createBranch: Bool
}

nonisolated struct VCSRemoveWorktreeParams: Codable, Sendable {
    let projectID: String
    let worktreeID: String
}

nonisolated struct VCSGetDiffParams: Codable, Sendable {
    let projectID: String
    let filePath: String
    let forceFull: Bool
}

nonisolated struct TerminalBytesEvent: Codable, Sendable {
    let paneID: UUID
    let bytes: Data
}

nonisolated struct PaneOwnershipEvent: Codable, Sendable {
    let paneID: UUID
    let owner: PaneOwner
}

nonisolated enum PaneOwner: Codable, Sendable, Equatable {
    case mac(deviceName: String)
    case remote(deviceID: UUID, deviceName: String)

    private enum CodingKeys: String, CodingKey {
        case mac
        case remote
    }

    private struct MacOwner: Codable {
        let deviceName: String
    }

    private struct RemoteOwner: Codable {
        let deviceID: UUID
        let deviceName: String
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        if let mac = try container.decodeIfPresent(MacOwner.self, forKey: .mac) {
            self = .mac(deviceName: mac.deviceName)
            return
        }
        if let remote = try container.decodeIfPresent(RemoteOwner.self, forKey: .remote) {
            self = .remote(deviceID: remote.deviceID, deviceName: remote.deviceName)
            return
        }
        throw DecodingError.dataCorruptedError(
            forKey: .mac,
            in: container,
            debugDescription: "Unknown pane owner"
        )
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case let .mac(deviceName):
            try container.encode(MacOwner(deviceName: deviceName), forKey: .mac)
        case let .remote(deviceID, deviceName):
            try container.encode(RemoteOwner(deviceID: deviceID, deviceName: deviceName), forKey: .remote)
        }
    }
}

nonisolated struct DeviceThemeEvent: Codable, Sendable, Equatable {
    let fg: UInt32
    let bg: UInt32
    let palette: [UInt32]?
}

nonisolated struct ProjectsResult: Codable, Sendable {
    let projects: [Project]

    init(projects: [Project]) {
        self.projects = projects
    }

    init(from decoder: Decoder) throws {
        if let keyed = try? decoder.container(keyedBy: CodingKeys.self), keyed.contains(.projects) {
            projects = try keyed.decode([Project].self, forKey: .projects)
            return
        }
        projects = try [Project](from: decoder)
    }
}
