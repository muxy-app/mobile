import Foundation
import MuxyMobile
import UIKit

nonisolated enum ServerProjectListing {
    static let missingPairingMessage = "This device's credentials are invalid. Remove it and add it again."

    static func item(for row: ProjectRow) -> ProjectListItem {
        let project = row.project
        return ProjectListItem(
            id: project.id,
            name: project.name,
            path: project.directory,
            icon: icon(for: project),
            iconColor: project.color,
            logo: project.logo,
            isNested: row.isNested
        )
    }

    static func status(
        phase: ServerController.Phase,
        hasLoadedProjects: Bool,
        projectsLoadFailed: Bool,
        serverName: String
    ) -> ProjectListStatus {
        switch phase {
        case .idle, .connecting, .reconnecting(.serverRestarting):
            return .loading
        case let .reconnecting(.lost(failure, _)), let .failed(failure):
            return .disconnected(message: failure.message(context: .connecting, serverName: serverName))
        case .connected where projectsLoadFailed:
            return .loadFailed
        case .connected where !hasLoadedProjects:
            return .loading
        case .connected:
            return .empty
        }
    }

    static func tabsStatus(phase: ServerController.Phase, hasLoadedSessions: Bool) -> ProjectTabsStatus {
        switch phase {
        case .idle, .connecting, .reconnecting(.serverRestarting):
            return .loading
        case .reconnecting(.lost), .failed:
            return .disconnected
        case .connected:
            return hasLoadedSessions ? .ready : .loading
        }
    }

    private static func icon(for project: ServerProject) -> ProjectListItem.Icon {
        guard let icon = project.icon?.trimmingCharacters(in: .whitespacesAndNewlines), !icon.isEmpty else {
            return .symbol(fallbackSymbol(for: project))
        }
        guard icon.hasPrefix(symbolPrefix) else { return .emoji(icon) }
        let name = String(icon.dropFirst(symbolPrefix.count))
        guard UIImage(systemName: name) != nil else { return .symbol(fallbackSymbol(for: project)) }
        return .symbol(name)
    }

    private static func fallbackSymbol(for project: ServerProject) -> String {
        if project.isHome { return "house" }
        if project.isWorktree { return "arrow.triangle.branch" }
        return "folder"
    }

    private static let symbolPrefix = "sf:"
}
