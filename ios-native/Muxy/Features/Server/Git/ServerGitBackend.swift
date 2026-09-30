import Foundation
import MuxyMobile

struct ServerGitBackend: GitBackend {
    static let diffLineLimit: UInt32 = 800

    let projectID: String
    let server: ServerController
    let canPublishBranch = true

    func status() async throws -> VCSStatus {
        try await request { repository in
            do {
                return VCSStatus(try await repository.status(includePullRequest: true))
            } catch let error as MobileError {
                guard case .Server = error, await Self.isMissingRepository(repository) else { throw error }
                throw GitBackendError.notRepository
            }
        }
    }

    func branches() async throws -> VCSBranches {
        VCSBranches(try await request { try await $0.branches() })
    }

    func diff(for key: GitDiffKey, full: Bool) async throws -> VCSDiff {
        let diff = try await request {
            try await $0.diff(path: key.path, staged: key.isStaged, lineLimit: full ? nil : Self.diffLineLimit)
        }
        return VCSDiff(diff, path: key.path)
    }

    func commit(message: String, stageAll: Bool) async throws {
        try await request { try await $0.commit(message: message, stageAll: stageAll) }
    }

    func pull() async throws {
        try await request { try await $0.pull() }
    }

    func push() async throws {
        try await request { try await $0.push(setUpstream: false) }
    }

    func switchBranch(_ branch: String) async throws {
        try await request { try await $0.switchBranch(name: branch) }
    }

    func createBranch(_ name: String) async throws {
        try await request { try await $0.createBranch(name: name) }
    }

    func createPullRequest(title: String, body: String, baseBranch: String?, draft: Bool) async throws -> VCSPRCreated {
        VCSPRCreated(try await request {
            try await $0.createPullRequest(title: title, body: body, baseBranch: baseBranch, draft: draft)
        })
    }

    func mergePullRequest(_ pullRequest: VCSPullRequest, method: VCSMergeMethod, deleteBranch: Bool) async throws {
        try await request {
            try await $0.mergePullRequest(
                number: UInt64(clamping: pullRequest.number),
                method: GitMergeMethod(method),
                deleteBranch: deleteBranch,
                expectedHead: pullRequest.headOid
            )
        }
    }

    private func request<Value>(_ call: (any ServerGitRepository) async throws -> Value) async throws -> Value {
        do {
            return try await call(try server.git(for: projectID))
        } catch {
            throw ServerRequestError.wrapping(error, serverName: server.serverName)
        }
    }

    private static func isMissingRepository(_ repository: any ServerGitRepository) async -> Bool {
        do {
            return try await repository.summary() == nil
        } catch {
            return false
        }
    }
}
