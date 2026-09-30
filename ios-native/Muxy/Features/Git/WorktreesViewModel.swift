import Foundation
import Observation
import OSLog

@MainActor
@Observable
final class WorktreesViewModel {
    private(set) var worktrees: [Worktree]?
    private(set) var isLoadingWorktrees = false
    private(set) var activeWorktreeID: UUID?
    private(set) var errorMessage: String?

    private let projectID: UUID
    private let connectionID: UUID?
    private let connectionManager: ConnectionManager
    private let worktreeCache: WorktreeCache
    private let git: GitViewModel

    init(
        projectID: UUID,
        connectionManager: ConnectionManager,
        git: GitViewModel,
        connectionID: UUID? = nil,
        worktreeCache: WorktreeCache? = nil
    ) {
        self.projectID = projectID
        self.connectionID = connectionID
        self.connectionManager = connectionManager
        self.git = git
        let resolvedWorktreeCache = worktreeCache ?? UserDefaultsWorktreeCache()
        self.worktreeCache = resolvedWorktreeCache
        worktrees = resolvedWorktreeCache.load(connectionID: connectionID, projectID: projectID)
    }

    func setActiveWorktreeID(_ worktreeID: UUID?) {
        activeWorktreeID = worktreeID
    }

    func refreshWorktrees() async {
        isLoadingWorktrees = true
        errorMessage = nil
        defer { isLoadingWorktrees = false }

        do {
            let result = try await connectionManager.request(.listWorktrees, params: ListWorktreesParams(projectID: projectID.uuidString))
            guard result.type == ResultType.worktrees else { return }
            let loaded = try result.decode([Worktree].self)
            worktrees = loaded
            worktreeCache.save(loaded, connectionID: connectionID, projectID: projectID)
        } catch {
            report(error, while: "refreshing worktrees")
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
                    projectID: projectID.uuidString,
                    name: trimmedName,
                    branch: trimmedBranch,
                    createBranch: createBranch
                )
            )
            guard result.type == ResultType.worktrees else { return false }
            let loaded = try result.decode([Worktree].self)
            worktrees = loaded
            worktreeCache.save(loaded, connectionID: connectionID, projectID: projectID)
            await git.refreshStatus()
            await git.refreshBranches()
            return true
        } catch {
            report(error, while: "adding a worktree")
            return false
        }
    }

    func removeWorktree(_ worktree: Worktree) async {
        do {
            _ = try await connectionManager.request(
                .vcsRemoveWorktree,
                params: VCSRemoveWorktreeParams(projectID: projectID.uuidString, worktreeID: worktree.id.uuidString)
            )
            await refreshWorktrees()
        } catch {
            report(error, while: "removing a worktree")
        }
    }

    func selectWorktree(_ worktree: Worktree) async {
        do {
            _ = try await connectionManager.request(
                .selectWorktree,
                params: SelectWorktreeParams(projectID: projectID.uuidString, worktreeID: worktree.id.uuidString)
            )
            activeWorktreeID = worktree.id
            git.invalidateDiffs()
            await git.refreshStatus()
            await refreshWorktrees()
        } catch {
            report(error, while: "selecting a worktree")
        }
    }

    private func report(_ error: any Error, while action: String) {
        errorMessage = error.localizedDescription
        Log.client.error("Worktrees failed while \(action, privacy: .public): \(error.localizedDescription, privacy: .private)")
    }
}
