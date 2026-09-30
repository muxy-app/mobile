import Foundation

nonisolated struct RemoteFileEntry: Codable, Sendable, Identifiable, Equatable {
    let name: String
    let path: String
    let isDirectory: Bool
    let isIgnored: Bool

    var id: String { path }
}

nonisolated enum FileEncoding: String, Codable, Sendable {
    case utf8
    case base64
}

nonisolated struct RemoteFileContent: Codable, Sendable {
    let path: String
    let content: String
    let size: Int
    let encoding: FileEncoding
}

nonisolated struct RemoteFileStat: Codable, Sendable {
    let name: String
    let path: String
    let isDirectory: Bool
    let size: Int
}

nonisolated struct RemoteTextFile: Sendable, Equatable {
    let path: String
    let text: String
    let size: Int
}

nonisolated struct FileScope: Hashable, Sendable {
    static let project = FileScope(worktreeID: nil)

    let worktreeID: UUID?
}

nonisolated struct FileChange: Sendable, Equatable {
    let scope: FileScope
    let paths: [String]
    let requiresRescan: Bool
}

nonisolated enum FileLimits {
    static let maximumBytes = 5 * 1024 * 1024
}

nonisolated struct FileChangedEvent: Codable, Sendable {
    let projectID: UUID
    let worktreeID: UUID?
    let paths: [String]
    let truncated: Bool
}

nonisolated struct FilePathParams: Codable, Sendable {
    let projectID: String
    let path: String
}

nonisolated struct FileReadParams: Codable, Sendable {
    let projectID: String
    let path: String
    let encoding: FileEncoding
}

nonisolated struct FileWriteParams: Codable, Sendable {
    let projectID: String
    let path: String
    let contents: String
    let encoding: FileEncoding
}

nonisolated struct FileRenameParams: Codable, Sendable {
    let projectID: String
    let path: String
    let newName: String
}

nonisolated struct FileMoveParams: Codable, Sendable {
    let projectID: String
    let paths: [String]
    let into: String
}

nonisolated struct FileDeleteParams: Codable, Sendable {
    let projectID: String
    let paths: [String]
}
