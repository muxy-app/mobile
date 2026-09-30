import Foundation

protocol GitBackend {
    var canPublishBranch: Bool { get }
    func status() async throws -> VCSStatus
    func branches() async throws -> VCSBranches
    func diff(for key: GitDiffKey, full: Bool) async throws -> VCSDiff
    func commit(message: String, stageAll: Bool) async throws
    func pull() async throws
    func push() async throws
    func switchBranch(_ branch: String) async throws
    func createBranch(_ name: String) async throws
    func createPullRequest(title: String, body: String, baseBranch: String?, draft: Bool) async throws -> VCSPRCreated
    func mergePullRequest(_ pullRequest: VCSPullRequest, method: VCSMergeMethod, deleteBranch: Bool) async throws
}

nonisolated enum GitBackendError: LocalizedError, Equatable {
    case unexpectedResponse
    case notRepository

    var errorDescription: String? {
        switch self {
        case .unexpectedResponse:
            "The server returned an unexpected Git response."
        case .notRepository:
            "This project isn't a Git repository."
        }
    }
}
