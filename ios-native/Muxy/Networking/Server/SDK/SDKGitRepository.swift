import Foundation
import MuxyMobile

nonisolated final class SDKGitRepository: ServerGitRepository {
    private let repository: MuxyMobile.GitRepository
    private let lanes: SDKLanes

    init(repository: MuxyMobile.GitRepository, lanes: SDKLanes) {
        self.repository = repository
        self.lanes = lanes
    }

    func summary() async throws -> GitSummary? {
        try await lanes.gitRequest { [repository] in
            try repository.summary()
        }
    }

    func status(includePullRequest: Bool) async throws -> GitStatus {
        try await lanes.gitRequest { [repository] in
            try repository.status(includePullRequest: includePullRequest)
        }
    }

    func branches() async throws -> [GitBranch] {
        try await lanes.gitRequest { [repository] in
            try repository.branches()
        }
    }

    func diff(path: String, staged: Bool, lineLimit: UInt32?) async throws -> GitDiff {
        try await lanes.gitRequest { [repository] in
            try repository.diff(path: path, staged: staged, lineLimit: lineLimit)
        }
    }

    func commit(message: String, stageAll: Bool) async throws {
        _ = try await lanes.gitRequest { [repository] in
            try repository.commit(message: message, stageAll: stageAll)
        }
    }

    func pull() async throws {
        try await lanes.gitRequest { [repository] in
            try repository.pull()
        }
    }

    func push(setUpstream: Bool) async throws {
        try await lanes.gitRequest { [repository] in
            try repository.push(setUpstream: setUpstream)
        }
    }

    func switchBranch(name: String) async throws {
        try await lanes.gitRequest { [repository] in
            try repository.switchBranch(name: name)
        }
    }

    func createBranch(name: String) async throws {
        try await lanes.gitRequest { [repository] in
            try repository.createBranch(name: name)
        }
    }

    func createPullRequest(title: String, body: String, baseBranch: String?, draft: Bool) async throws -> GitPullRequest {
        try await lanes.gitRequest { [repository] in
            try repository.createPullRequest(title: title, body: body, baseBranch: baseBranch, draft: draft)
        }
    }

    func mergePullRequest(number: UInt64, method: GitMergeMethod, deleteBranch: Bool, expectedHead: String?) async throws {
        try await lanes.gitRequest { [repository] in
            try repository.mergePullRequest(number: number, method: method, deleteBranch: deleteBranch, expectedHead: expectedHead)
        }
    }

    func worktrees() async throws -> [GitWorktree] {
        try await lanes.gitRequest { [repository] in
            try repository.worktrees()
        }
    }

    func createWorktree(branch: String, base: String?) async throws -> ServerProject {
        try await lanes.gitRequest { [repository] in
            try repository.createWorktree(branch: branch, base: base, directory: nil)
        }
    }

    func registerWorktree(directory: String) async throws -> ServerProject {
        try await lanes.gitRequest { [repository] in
            try repository.registerWorktree(directory: directory)
        }
    }

    func inspectWorktreeRemoval() async throws -> WorktreeRemoval {
        try await lanes.gitRequest { [repository] in
            try repository.inspectWorktreeRemoval()
        }
    }

    func removeWorktree(expected: WorktreeRemoval) async throws {
        try await lanes.gitRequest { [repository] in
            try repository.removeWorktree(expected: expected)
        }
    }
}
