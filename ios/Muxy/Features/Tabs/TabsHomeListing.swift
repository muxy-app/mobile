import Foundation

nonisolated enum TabsHomeStatus: Equatable {
    case loading
    case connected
    case offline
    case needsPairing
    case ssh

    var label: String {
        switch self {
        case .loading: "Loading"
        case .connected: "Connected"
        case .offline: "Offline"
        case .needsPairing: "Pairing needed"
        case .ssh: "SSH"
        }
    }
}

nonisolated struct TabsHomeDevice: Identifiable {
    let id: UUID
    let name: String
    let systemImage: String
    let status: TabsHomeStatus
    let projects: [TabsHomeProject]
    let canEdit: Bool

    var tabCount: Int {
        projects.reduce(0) { $0 + $1.tabs.count }
    }

    var visibleProjects: [TabsHomeProject] {
        projects.filter { !$0.tabs.isEmpty }
    }

    var idleProjects: [TabsHomeProject] {
        projects.filter(\.tabs.isEmpty)
    }

    func matching(_ query: String) -> TabsHomeDevice? {
        let needle = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !needle.isEmpty else { return self }
        if name.localizedStandardContains(needle) { return self }
        let matched = projects.compactMap { $0.matching(needle) }
        guard !matched.isEmpty else { return nil }
        return TabsHomeDevice(
            id: id,
            name: name,
            systemImage: systemImage,
            status: status,
            projects: matched,
            canEdit: canEdit
        )
    }
}

nonisolated struct TabsHomeProject: Identifiable {
    let id: String
    let name: String
    let path: String
    let tabs: [TabsHomeTab]
    let canCreate: Bool

    func matching(_ needle: String) -> TabsHomeProject? {
        if name.localizedStandardContains(needle) { return self }
        let matched = tabs.filter { $0.title.localizedStandardContains(needle) || $0.detail.localizedStandardContains(needle) }
        guard !matched.isEmpty else { return nil }
        return TabsHomeProject(id: id, name: name, path: path, tabs: matched, canCreate: canCreate)
    }
}

nonisolated struct TabsHomeTab: Identifiable {
    let id: UUID
    let title: String
    let detail: String
    let kind: TabTileKind
    let activity: TabActivity
}

nonisolated struct TabsHomeAttention: Identifiable {
    let deviceID: UUID
    let deviceName: String
    let projectID: String
    let projectName: String
    let tab: TabsHomeTab

    var id: UUID { tab.id }

    static func collect(from devices: [TabsHomeDevice]) -> [TabsHomeAttention] {
        devices
            .flatMap { device in
                device.projects.flatMap { project in
                    project.tabs
                        .filter(\.activity.needsAttention)
                        .map { TabsHomeAttention(deviceID: device.id, deviceName: device.name, projectID: project.id, projectName: project.name, tab: $0) }
                }
            }
            .sorted { ($0.tab.activity.since ?? .distantPast) > ($1.tab.activity.since ?? .distantPast) }
    }
}
