import Foundation

struct ChannelGitBackend: GitBackend {
    let projectID: UUID
    let connectionManager: ConnectionManager
    let canPublishBranch = false

    func status() async throws -> VCSStatus {
        try await request(.vcsRefresh, params: projectParams, resultType: ResultType.vcsStatus)
    }

    func branches() async throws -> VCSBranches {
        try await request(.vcsListBranches, params: projectParams, resultType: ResultType.vcsBranches)
    }

    func diff(for key: GitDiffKey, full: Bool) async throws -> VCSDiff {
        try await request(
            .vcsGetDiff,
            params: VCSGetDiffParams(projectID: projectID.uuidString, filePath: key.path, forceFull: full),
            resultType: ResultType.vcsDiff
        )
    }

    func commit(message: String, stageAll: Bool) async throws {
        _ = try await connectionManager.request(
            .vcsCommit,
            params: VCSCommitParams(projectID: projectID.uuidString, message: message, stageAll: stageAll)
        )
    }

    func pull() async throws {
        _ = try await connectionManager.request(.vcsPull, params: projectParams)
    }

    func push() async throws {
        _ = try await connectionManager.request(.vcsPush, params: projectParams)
    }

    func switchBranch(_ branch: String) async throws {
        _ = try await connectionManager.request(
            .vcsSwitchBranch,
            params: VCSBranchParams(projectID: projectID.uuidString, branch: branch)
        )
    }

    func createBranch(_ name: String) async throws {
        _ = try await connectionManager.request(
            .vcsCreateBranch,
            params: VCSCreateBranchParams(projectID: projectID.uuidString, name: name)
        )
    }

    func createPullRequest(title: String, body: String, baseBranch: String?, draft: Bool) async throws -> VCSPRCreated {
        try await request(
            .vcsCreatePR,
            params: VCSCreatePRParams(projectID: projectID.uuidString, title: title, body: body, baseBranch: baseBranch, draft: draft),
            resultType: ResultType.vcsPRCreated
        )
    }

    func mergePullRequest(_ pullRequest: VCSPullRequest, method: VCSMergeMethod, deleteBranch: Bool) async throws {
        _ = try await connectionManager.request(
            .vcsMergePullRequest,
            params: VCSMergePullRequestParams(
                projectID: projectID.uuidString,
                number: pullRequest.number,
                method: method,
                deleteBranch: deleteBranch
            )
        )
    }

    private var projectParams: VCSProjectParams {
        VCSProjectParams(projectID: projectID.uuidString)
    }

    private func request<P: Codable & Sendable, R: Decodable & Sendable>(
        _ method: Method,
        params: P,
        resultType: String
    ) async throws -> R {
        let result = try await connectionManager.request(method, params: params)
        guard result.type == resultType else { throw GitBackendError.unexpectedResponse }
        return try result.decode(R.self)
    }
}
