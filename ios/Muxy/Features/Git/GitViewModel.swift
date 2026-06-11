import Foundation
import Observation
import OSLog

@MainActor
@Observable
final class GitViewModel {
    let project: Project

    private(set) var status: VCSStatus?
    private(set) var branches: VCSBranches?
    private(set) var worktrees: [Worktree]?
    private(set) var diffsByPath: [String: VCSDiff] = [:]
    private(set) var isLoadingStatus = false
    private(set) var isLoadingBranches = false
    private(set) var isLoadingWorktrees = false
    private(set) var loadingDiffPaths: Set<String> = []
    private(set) var activeWorktreeID: UUID?
    private(set) var errorMessage: String?

    private let connectionID: UUID?
    private let connectionManager: ConnectionManager
    private let worktreeCache: WorktreeCache

    init(
        project: Project,
        connectionManager: ConnectionManager,
        connectionID: UUID? = nil,
        worktreeCache: WorktreeCache? = nil
    ) {
        self.project = project
        self.connectionID = connectionID
        self.connectionManager = connectionManager
        let resolvedWorktreeCache = worktreeCache ?? UserDefaultsWorktreeCache()
        self.worktreeCache = resolvedWorktreeCache
        worktrees = resolvedWorktreeCache.load(connectionID: connectionID, projectID: project.id)
    }

    var totalChanges: Int {
        (status?.stagedFiles.count ?? 0) + (status?.changedFiles.count ?? 0)
    }

    func setActiveWorktreeID(_ worktreeID: UUID?) {
        activeWorktreeID = worktreeID
    }

    func refreshStatus() async {
        isLoadingStatus = true
        errorMessage = nil
        defer { isLoadingStatus = false }

        do {
            let result = try await connectionManager.request(.vcsRefresh, params: VCSProjectParams(projectID: project.id.uuidString))
            guard result.type == ResultType.vcsStatus else { return }
            status = try result.decode(VCSStatus.self)
        } catch {
            errorMessage = error.localizedDescription
            Log.client.error("Failed to refresh git status: \(error.localizedDescription, privacy: .public)")
        }
    }

    func refreshBranches() async {
        isLoadingBranches = true
        errorMessage = nil
        defer { isLoadingBranches = false }

        do {
            let result = try await connectionManager.request(.vcsListBranches, params: VCSProjectParams(projectID: project.id.uuidString))
            guard result.type == ResultType.vcsBranches else { return }
            branches = try result.decode(VCSBranches.self)
        } catch {
            errorMessage = error.localizedDescription
            Log.client.error("Failed to refresh git branches: \(error.localizedDescription, privacy: .public)")
        }
    }

    func refreshWorktrees() async {
        isLoadingWorktrees = true
        errorMessage = nil
        defer { isLoadingWorktrees = false }

        do {
            let result = try await connectionManager.request(.listWorktrees, params: ListWorktreesParams(projectID: project.id.uuidString))
            guard result.type == ResultType.worktrees else { return }
            let loaded = try result.decode([Worktree].self)
            worktrees = loaded
            worktreeCache.save(loaded, connectionID: connectionID, projectID: project.id)
        } catch {
            errorMessage = error.localizedDescription
            Log.client.error("Failed to refresh git worktrees: \(error.localizedDescription, privacy: .public)")
        }
    }

    func loadDiff(filePath: String, forceFull: Bool = false) async {
        loadingDiffPaths.insert(filePath)
        errorMessage = nil
        defer { loadingDiffPaths.remove(filePath) }

        do {
            let result = try await connectionManager.request(
                .vcsGetDiff,
                params: VCSGetDiffParams(projectID: project.id.uuidString, filePath: filePath, forceFull: forceFull)
            )
            guard result.type == ResultType.vcsDiff else { return }
            diffsByPath[filePath] = try result.decode(VCSDiff.self)
        } catch {
            errorMessage = error.localizedDescription
            Log.client.error("Failed to load git diff: \(error.localizedDescription, privacy: .public)")
        }
    }

    func commit(message: String, stageAll: Bool) async -> Bool {
        let trimmed = message.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return false }

        do {
            _ = try await connectionManager.request(
                .vcsCommit,
                params: VCSCommitParams(projectID: project.id.uuidString, message: trimmed, stageAll: stageAll)
            )
            diffsByPath.removeAll()
            await refreshStatus()
            return true
        } catch {
            errorMessage = error.localizedDescription
            Log.client.error("Failed to commit: \(error.localizedDescription, privacy: .public)")
            return false
        }
    }

    func pull() async {
        await runStatusMutation(.vcsPull)
    }

    func push() async {
        await runStatusMutation(.vcsPush)
    }

    func switchBranch(_ branch: String) async {
        do {
            _ = try await connectionManager.request(
                .vcsSwitchBranch,
                params: VCSBranchParams(projectID: project.id.uuidString, branch: branch)
            )
            diffsByPath.removeAll()
            await refreshStatus()
            await refreshBranches()
        } catch {
            errorMessage = error.localizedDescription
            Log.client.error("Failed to switch git branch: \(error.localizedDescription, privacy: .public)")
        }
    }

    func createBranch(_ name: String) async -> Bool {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return false }

        do {
            _ = try await connectionManager.request(
                .vcsCreateBranch,
                params: VCSCreateBranchParams(projectID: project.id.uuidString, name: trimmed)
            )
            await refreshStatus()
            await refreshBranches()
            return true
        } catch {
            errorMessage = error.localizedDescription
            Log.client.error("Failed to create git branch: \(error.localizedDescription, privacy: .public)")
            return false
        }
    }

    func createPullRequest(title: String, body: String, baseBranch: String?, draft: Bool) async -> VCSPRCreated? {
        let trimmedTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedTitle.isEmpty else { return nil }

        do {
            let result = try await connectionManager.request(
                .vcsCreatePR,
                params: VCSCreatePRParams(
                    projectID: project.id.uuidString,
                    title: trimmedTitle,
                    body: body.trimmingCharacters(in: .whitespacesAndNewlines),
                    baseBranch: baseBranch?.trimmingCharacters(in: .whitespacesAndNewlines),
                    draft: draft
                )
            )
            guard result.type == ResultType.vcsPRCreated else { return nil }
            let created = try result.decode(VCSPRCreated.self)
            await refreshStatus()
            return created
        } catch {
            errorMessage = error.localizedDescription
            Log.client.error("Failed to create pull request: \(error.localizedDescription, privacy: .public)")
            return nil
        }
    }

    func mergePullRequest(number: Int, method: VCSMergeMethod, deleteBranch: Bool) async -> Bool {
        do {
            _ = try await connectionManager.request(
                .vcsMergePullRequest,
                params: VCSMergePullRequestParams(
                    projectID: project.id.uuidString,
                    number: number,
                    method: method,
                    deleteBranch: deleteBranch
                )
            )
            await refreshStatus()
            return true
        } catch {
            errorMessage = error.localizedDescription
            Log.client.error("Failed to merge pull request: \(error.localizedDescription, privacy: .public)")
            return false
        }
    }

    func addWorktree(name: String, branch: String, createBranch: Bool) async -> Bool {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedBranch = branch.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty, !trimmedBranch.isEmpty else { return false }

        do {
            let result = try await connectionManager.request(
                .vcsAddWorktree,
                params: VCSAddWorktreeParams(
                    projectID: project.id.uuidString,
                    name: trimmedName,
                    branch: trimmedBranch,
                    createBranch: createBranch
                )
            )
            guard result.type == ResultType.worktrees else { return false }
            let loaded = try result.decode([Worktree].self)
            worktrees = loaded
            worktreeCache.save(loaded, connectionID: connectionID, projectID: project.id)
            await refreshStatus()
            await refreshBranches()
            return true
        } catch {
            errorMessage = error.localizedDescription
            Log.client.error("Failed to add worktree: \(error.localizedDescription, privacy: .public)")
            return false
        }
    }

    func removeWorktree(_ worktree: Worktree) async {
        do {
            _ = try await connectionManager.request(
                .vcsRemoveWorktree,
                params: VCSRemoveWorktreeParams(projectID: project.id.uuidString, worktreeID: worktree.id.uuidString)
            )
            await refreshWorktrees()
        } catch {
            errorMessage = error.localizedDescription
            Log.client.error("Failed to remove worktree: \(error.localizedDescription, privacy: .public)")
        }
    }

    func selectWorktree(_ worktree: Worktree) async {
        do {
            _ = try await connectionManager.request(
                .selectWorktree,
                params: SelectWorktreeParams(projectID: project.id.uuidString, worktreeID: worktree.id.uuidString)
            )
            activeWorktreeID = worktree.id
            diffsByPath.removeAll()
            await refreshStatus()
            await refreshWorktrees()
        } catch {
            errorMessage = error.localizedDescription
            Log.client.error("Failed to select worktree: \(error.localizedDescription, privacy: .public)")
        }
    }

    private func runStatusMutation(_ method: Method) async {
        do {
            _ = try await connectionManager.request(method, params: VCSProjectParams(projectID: project.id.uuidString))
            if method == .vcsPull {
                diffsByPath.removeAll()
            }
            await refreshStatus()
        } catch {
            errorMessage = error.localizedDescription
            Log.client.error("Failed to run git action: \(error.localizedDescription, privacy: .public)")
        }
    }
}
