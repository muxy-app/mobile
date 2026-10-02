import Foundation

nonisolated struct FileLocation: Sendable, Equatable {
    let name: String
    let path: String
    let icon: ProjectListItem.Icon
    let host: FileHost
}

nonisolated enum FileHost: Sendable, Equatable {
    case mac
    case remoteHost
    case computer(String)
}

extension FileLocation {
    init(project: Project) {
        self.init(
            name: project.name,
            path: project.path,
            icon: .symbol(project.icon ?? "folder"),
            host: project.workspaceKind == "ssh" ? .remoteHost : .mac
        )
    }
}

nonisolated extension FileHost {
    var deletesPermanently: Bool {
        self == .remoteHost
    }

    var symbol: String {
        deletesPermanently ? "server.rack" : "desktopcomputer"
    }

    var label: String {
        switch self {
        case .mac: "Mac · Active worktree"
        case .remoteHost: "Remote host · Active worktree"
        case let .computer(name): name
        }
    }

    var reconnectMessage: String {
        "Reconnect to \(reconnectName) to browse files. Any unsaved edits are still here."
    }

    var selectionGuidance: String {
        "Touch and hold a file to select it. Changes are made on \(referenceName)."
    }

    var savedStatus: String {
        "Saved on \(shortName)"
    }

    var changedStatus: String {
        "Changed on \(shortName)"
    }

    var changedNoticeTitle: String {
        "Changed on \(possessiveName)"
    }

    var draftReplacementMessage: String {
        "Your edits are still here. Reload for the latest file. Saving this draft replaces the file on \(possessiveName)."
    }

    var deletionTitle: String {
        deletesPermanently ? "Delete permanently?" : "Move to Trash?"
    }

    var deletionMenuTitle: String {
        deletesPermanently ? "Delete permanently" : "Move to Trash"
    }

    var deletionConfirmationTitle: String {
        deletesPermanently ? "Delete" : "Move to Trash"
    }

    var deletionShortTitle: String {
        deletesPermanently ? "Delete" : "Trash"
    }

    func deletionMessage(for items: String) -> String {
        switch self {
        case .mac: "\(items) will be moved to Trash on the Mac."
        case .remoteHost: "\(items) will be permanently deleted from the remote host."
        case let .computer(name): "\(items) will be moved to Trash on \(name)."
        }
    }

    private var shortName: String {
        switch self {
        case .mac: "Mac"
        case .remoteHost: "remote host"
        case let .computer(name): name
        }
    }

    private var possessiveName: String {
        switch self {
        case .mac: "your Mac"
        case .remoteHost: "your remote host"
        case let .computer(name): name
        }
    }

    private var referenceName: String {
        switch self {
        case .mac: "your Mac"
        case .remoteHost: "the remote host"
        case let .computer(name): name
        }
    }

    private var reconnectName: String {
        guard case let .computer(name) = self else { return "your Mac" }
        return name
    }
}
