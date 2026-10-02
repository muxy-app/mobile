import Foundation
import Testing
@testable import Muxy

@MainActor
struct GitViewModelTests {
    private let projectID = UUID(uuidString: "00000000-0000-4000-8000-000000000201")!
    private let changedFile = GitDiffKey(path: "ios/Muxy/Networking/Protocol/Methods.swift", isStaged: false)

    @Test func demoRefreshLoadsStatusBranchesWorktreesAndDiff() async throws {
        let manager = await demoConnectionManager()
        let viewModel = GitViewModel(backend: ChannelGitBackend(projectID: projectID, connectionManager: manager))
        let worktrees = WorktreesViewModel(projectID: projectID, connectionManager: manager, git: viewModel)

        await viewModel.refreshStatus()
        await viewModel.refreshBranches()
        await worktrees.refreshWorktrees()
        await viewModel.loadDiff(changedFile)

        #expect(viewModel.status?.branch == "main")
        #expect(viewModel.totalChanges == 2)
        #expect(viewModel.branches?.locals.contains("main") == true)
        #expect(worktrees.worktrees?.first?.name == "Muxy")
        #expect(viewModel.diffs[changedFile]?.additions == 2)
    }

    @Test func commitClearsDemoChangesAndDiffCache() async throws {
        let manager = await demoConnectionManager()
        let viewModel = GitViewModel(backend: ChannelGitBackend(projectID: projectID, connectionManager: manager))

        await viewModel.refreshStatus()
        await viewModel.loadDiff(changedFile)
        let didCommit = await viewModel.commit(message: "Native git", stageAll: true)

        #expect(didCommit)
        #expect(viewModel.totalChanges == 0)
        #expect(viewModel.diffs.isEmpty)
    }

    @Test func demoCreatesPullRequestAndWorktree() async throws {
        let manager = await demoConnectionManager()
        let viewModel = GitViewModel(backend: ChannelGitBackend(projectID: projectID, connectionManager: manager))
        let worktrees = WorktreesViewModel(projectID: projectID, connectionManager: manager, git: viewModel)

        let pullRequest = await viewModel.createPullRequest(title: "Native git", body: "", baseBranch: "main", draft: false)
        let didAddWorktree = await worktrees.addWorktree(name: "native-git", branch: "feature/native-git", createBranch: true)

        #expect(pullRequest?.number == 42)
        #expect(didAddWorktree)
        #expect(worktrees.worktrees?.first?.name == "Muxy")
    }

    private func demoConnectionManager() async -> ConnectionManager {
        let manager = ConnectionManager(makeTransport: { _ in MockTransport() })
        await manager.connect(to: DemoConnection.connection, token: "demo")
        return manager
    }
}
