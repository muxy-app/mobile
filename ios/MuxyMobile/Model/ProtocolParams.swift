import Foundation

struct SelectProjectParams: Codable {
    let projectID: UUID
}

struct ListWorktreesParams: Codable {
    let projectID: UUID
}

struct SelectWorktreeParams: Codable {
    let projectID: UUID
    let worktreeID: UUID
}

struct GetWorkspaceParams: Codable {
    let projectID: UUID
}

struct CreateTabParams: Codable {
    let projectID: UUID
    let areaID: UUID?
    let kind: TabKindDTO
    init(projectID: UUID, areaID: UUID? = nil, kind: TabKindDTO = .terminal) {
        self.projectID = projectID
        self.areaID = areaID
        self.kind = kind
    }
}

struct CloseTabParams: Codable {
    let projectID: UUID
    let areaID: UUID
    let tabID: UUID
}

struct SelectTabParams: Codable {
    let projectID: UUID
    let areaID: UUID
    let tabID: UUID
}

struct SplitAreaParams: Codable {
    let projectID: UUID
    let areaID: UUID
    let direction: SplitDirectionDTO
    let position: SplitPositionDTO
}

enum SplitPositionDTO: String, Codable {
    case first
    case second
}

struct CloseAreaParams: Codable {
    let projectID: UUID
    let areaID: UUID
}

struct FocusAreaParams: Codable {
    let projectID: UUID
    let areaID: UUID
}

struct TerminalInputParams: Codable {
    let paneID: UUID
    let bytes: Data
}

struct TerminalResizeParams: Codable {
    let paneID: UUID
    let cols: UInt32
    let rows: UInt32
}

struct RegisterDeviceParams: Codable {
    let deviceName: String
}

struct PairDeviceParams: Codable {
    let deviceID: UUID
    let deviceName: String
    let token: String
}

struct AuthenticateDeviceParams: Codable {
    let deviceID: UUID
    let deviceName: String
    let token: String
}

struct PairingResultDTO: Codable {
    let clientID: UUID
    let deviceName: String
    let themeFg: UInt32?
    let themeBg: UInt32?
    let themePalette: [UInt32]?
    init(clientID: UUID, deviceName: String, themeFg: UInt32? = nil, themeBg: UInt32? = nil, themePalette: [UInt32]? = nil) {
        self.clientID = clientID
        self.deviceName = deviceName
        self.themeFg = themeFg
        self.themeBg = themeBg
        self.themePalette = themePalette
    }
}

struct DeviceInfoDTO: Codable {
    let clientID: UUID
    let deviceName: String
    let themeFg: UInt32?
    let themeBg: UInt32?
    let themePalette: [UInt32]?
    init(clientID: UUID, deviceName: String, themeFg: UInt32? = nil, themeBg: UInt32? = nil, themePalette: [UInt32]? = nil) {
        self.clientID = clientID
        self.deviceName = deviceName
        self.themeFg = themeFg
        self.themeBg = themeBg
        self.themePalette = themePalette
    }
}

enum PaneOwnerDTO: Codable, Equatable {
    case mac(deviceName: String)
    case remote(deviceID: UUID, deviceName: String)

    var displayName: String {
        switch self {
        case let .mac(name): name
        case let .remote(_, name): name
        }
    }
}

struct TakeOverPaneParams: Codable {
    let paneID: UUID
    let cols: UInt32
    let rows: UInt32
}

struct ReleasePaneParams: Codable {
    let paneID: UUID
}

struct PaneOwnershipEventDTO: Codable {
    let paneID: UUID
    let owner: PaneOwnerDTO
}

struct DeviceThemeEventDTO: Codable {
    let fg: UInt32
    let bg: UInt32
    let palette: [UInt32]?
    init(fg: UInt32, bg: UInt32, palette: [UInt32]? = nil) {
        self.fg = fg
        self.bg = bg
        self.palette = palette
    }
}

struct TerminalScrollParams: Codable {
    let paneID: UUID
    let deltaX: Double
    let deltaY: Double
    let precise: Bool
}

struct GetTerminalContentParams: Codable {
    let paneID: UUID
}

struct TerminalContentDTO: Codable {
    let paneID: UUID
    let content: String
    let cols: UInt32
    let rows: UInt32
}

struct TerminalCellDTO: Codable {
    let codepoint: UInt32
    let fg: UInt32
    let bg: UInt32
    let flags: UInt16
}

struct TerminalCellsDTO: Codable {
    let paneID: UUID
    let cols: UInt32
    let rows: UInt32
    let cursorX: UInt32
    let cursorY: UInt32
    let cursorVisible: Bool
    let defaultFg: UInt32
    let defaultBg: UInt32
    let cells: [TerminalCellDTO]
    let altScreen: Bool
    let cursorKeys: Bool
    let bracketedPaste: Bool
    let focusEvent: Bool
    let mouseEvent: UInt16
    let mouseFormat: UInt16

    init(
        paneID: UUID,
        cols: UInt32,
        rows: UInt32,
        cursorX: UInt32,
        cursorY: UInt32,
        cursorVisible: Bool,
        defaultFg: UInt32,
        defaultBg: UInt32,
        cells: [TerminalCellDTO],
        altScreen: Bool = false,
        cursorKeys: Bool = false,
        bracketedPaste: Bool = false,
        focusEvent: Bool = false,
        mouseEvent: UInt16 = 0,
        mouseFormat: UInt16 = 0
    ) {
        self.paneID = paneID
        self.cols = cols
        self.rows = rows
        self.cursorX = cursorX
        self.cursorY = cursorY
        self.cursorVisible = cursorVisible
        self.defaultFg = defaultFg
        self.defaultBg = defaultBg
        self.cells = cells
        self.altScreen = altScreen
        self.cursorKeys = cursorKeys
        self.bracketedPaste = bracketedPaste
        self.focusEvent = focusEvent
        self.mouseEvent = mouseEvent
        self.mouseFormat = mouseFormat
    }
}

enum TerminalCellFlag {
    static let bold: UInt16 = 1 << 0
    static let italic: UInt16 = 1 << 1
    static let faint: UInt16 = 1 << 2
    static let blink: UInt16 = 1 << 3
    static let inverse: UInt16 = 1 << 4
    static let invisible: UInt16 = 1 << 5
    static let strike: UInt16 = 1 << 6
    static let underline: UInt16 = 1 << 7
    static let overline: UInt16 = 1 << 8
    static let wide: UInt16 = 1 << 9
    static let spacer: UInt16 = 1 << 10
}

struct TerminalOutputEventDTO: Codable {
    let paneID: UUID
    let bytes: Data
}

struct TabChangeEventDTO: Codable {
    let projectID: UUID
    let areaID: UUID
    let tab: TabDTO
    let changeKind: TabChangeKind
    enum TabChangeKind: String, Codable {
        case created
        case closed
        case selected
        case titleChanged
    }
}

struct GetVCSStatusParams: Codable {
    let projectID: UUID
}

struct VCSCommitParams: Codable {
    let projectID: UUID
    let message: String
    let stageAll: Bool
    init(projectID: UUID, message: String, stageAll: Bool = false) {
        self.projectID = projectID
        self.message = message
        self.stageAll = stageAll
    }
}

struct VCSPushParams: Codable {
    let projectID: UUID
}

struct VCSPullParams: Codable {
    let projectID: UUID
}

struct VCSStageFilesParams: Codable {
    let projectID: UUID
    let paths: [String]
}

struct VCSUnstageFilesParams: Codable {
    let projectID: UUID
    let paths: [String]
}

struct VCSDiscardFilesParams: Codable {
    let projectID: UUID
    let paths: [String]
    let untrackedPaths: [String]
}

struct VCSListBranchesParams: Codable {
    let projectID: UUID
}

struct VCSSwitchBranchParams: Codable {
    let projectID: UUID
    let branch: String
}

struct VCSCreateBranchParams: Codable {
    let projectID: UUID
    let name: String
}

struct VCSCreatePRParams: Codable {
    let projectID: UUID
    let title: String
    let body: String
    let baseBranch: String?
    let draft: Bool
}

struct VCSAddWorktreeParams: Codable {
    let projectID: UUID
    let name: String
    let branch: String
    let createBranch: Bool
}

struct VCSRemoveWorktreeParams: Codable {
    let projectID: UUID
    let worktreeID: UUID
}

struct GetProjectLogoParams: Codable {
    let projectID: UUID
}

struct ProjectLogoDTO: Codable {
    let projectID: UUID
    let pngData: String
}

struct MarkNotificationReadParams: Codable {
    let notificationID: UUID
}

struct SubscribeParams: Codable {
    let events: [MuxyEventKind]
}

struct UnsubscribeParams: Codable {
    let events: [MuxyEventKind]
}
