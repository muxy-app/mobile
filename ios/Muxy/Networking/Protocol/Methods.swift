import Foundation

enum Method: String, Sendable {
    case authenticateDevice
    case pairDevice
    case listProjects
    case selectProject
    case getWorkspace
    case createTab
    case closeTab
    case selectTab
}

enum ResultType {
    static let pairing = "pairing"
    static let projects = "projects"
    static let workspace = "workspace"
    static let tab = "tab"
    static let ok = "ok"
}

enum EventName {
    static let workspaceChanged = "workspaceChanged"
    static let projectsChanged = "projectsChanged"
}

enum EventType {
    static let workspace = "workspace"
    static let projects = "projects"
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
}

nonisolated struct EmptyRequestParams: Codable, Sendable {}

nonisolated struct SelectProjectParams: Codable, Sendable {
    let projectID: String
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

nonisolated struct ProjectsResult: Codable, Sendable {
    let projects: [Project]

    init(projects: [Project]) {
        self.projects = projects
    }

    init(from decoder: Decoder) throws {
        if let array = try? [Project](from: decoder) {
            projects = array
            return
        }
        let container = try decoder.container(keyedBy: CodingKeys.self)
        projects = try container.decode([Project].self, forKey: .projects)
    }
}
