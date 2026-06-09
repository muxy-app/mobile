import Foundation

nonisolated enum GitFileStatus: String, Codable, Sendable, Equatable {
    case added
    case modified
    case deleted
    case renamed
    case copied
    case untracked
    case unmerged
}

nonisolated struct GitFile: Codable, Sendable, Identifiable, Equatable, Hashable {
    let path: String
    let status: GitFileStatus
    let isUntracked: Bool

    var id: String { path }
}

nonisolated enum VCSPRChecksStatus: String, Codable, Sendable, Equatable {
    case none
    case pending
    case success
    case failure
}

nonisolated struct VCSPRChecks: Codable, Sendable, Equatable {
    let status: VCSPRChecksStatus
    let passing: Int
    let failing: Int
    let pending: Int
    let total: Int
}

nonisolated enum VCSPRMergeStateStatus: String, Codable, Sendable, Equatable {
    case clean = "CLEAN"
    case hasHooks = "HAS_HOOKS"
    case unstable = "UNSTABLE"
    case behind = "BEHIND"
    case blocked = "BLOCKED"
    case dirty = "DIRTY"
    case draft = "DRAFT"
    case unknown = "UNKNOWN"
}

nonisolated struct VCSPullRequest: Codable, Sendable, Equatable {
    let url: String
    let number: Int
    let state: String
    let isDraft: Bool
    let baseBranch: String
    let mergeable: Bool?
    let mergeStateStatus: VCSPRMergeStateStatus?
    let checks: VCSPRChecks?
}

nonisolated enum VCSMergeMethod: String, Codable, Sendable, CaseIterable, Equatable {
    case merge
    case squash
    case rebase
}

nonisolated struct VCSStatus: Codable, Sendable, Equatable {
    let branch: String
    let aheadCount: Int
    let behindCount: Int
    let hasUpstream: Bool
    let stagedFiles: [GitFile]
    let changedFiles: [GitFile]
    let defaultBranch: String?
    let pullRequest: VCSPullRequest?
}

nonisolated struct VCSBranches: Codable, Sendable, Equatable {
    let current: String
    let locals: [String]
    let defaultBranch: String?
}

nonisolated struct VCSPRCreated: Codable, Sendable, Equatable {
    let url: String
    let number: Int
}

nonisolated enum VCSDiffRowKind: String, Codable, Sendable, Equatable {
    case hunk
    case context
    case addition
    case deletion
    case collapsed
}

nonisolated struct VCSDiffRow: Codable, Sendable, Identifiable, Equatable {
    let kind: VCSDiffRowKind
    let oldLineNumber: Int?
    let newLineNumber: Int?
    let text: String

    var id: String {
        "\(kind.rawValue):\(oldLineNumber.map(String.init) ?? ""):\(newLineNumber.map(String.init) ?? ""):\(text)"
    }
}

nonisolated struct VCSDiff: Codable, Sendable, Equatable {
    let filePath: String
    let rows: [VCSDiffRow]
    let additions: Int
    let deletions: Int
    let truncated: Bool
    let isBinary: Bool
}

nonisolated struct Worktree: Codable, Sendable, Identifiable, Equatable, Hashable {
    let id: UUID
    let name: String
    let path: String
    let branch: String
    let isPrimary: Bool
    let canBeRemoved: Bool
    let createdAt: String
}
