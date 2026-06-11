import Foundation
import Testing
@testable import Muxy

@MainActor
struct GitViewModelTests {
    @Test func demoRefreshLoadsStatusBranchesWorktreesAndDiff() async throws {
        let manager = ConnectionManager(makeTransport: { _ in MockTransport() })
        await manager.connect(to: DemoConnection.connection, token: "demo")
        let viewModel = GitViewModel(project: muxyProject(), connectionManager: manager)

        await viewModel.refreshStatus()
        await viewModel.refreshBranches()
        await viewModel.refreshWorktrees()
        await viewModel.loadDiff(filePath: "ios/Muxy/Networking/Protocol/Methods.swift")

        #expect(viewModel.status?.branch == "main")
        #expect(viewModel.totalChanges == 2)
        #expect(viewModel.branches?.locals.contains("main") == true)
        #expect(viewModel.worktrees?.first?.name == "Muxy")
        #expect(viewModel.diffsByPath["ios/Muxy/Networking/Protocol/Methods.swift"]?.additions == 2)
    }

    @Test func commitClearsDemoChangesAndDiffCache() async throws {
        let manager = ConnectionManager(makeTransport: { _ in MockTransport() })
        await manager.connect(to: DemoConnection.connection, token: "demo")
        let viewModel = GitViewModel(project: muxyProject(), connectionManager: manager)

        await viewModel.refreshStatus()
        await viewModel.loadDiff(filePath: "ios/Muxy/Networking/Protocol/Methods.swift")
        let didCommit = await viewModel.commit(message: "Native git", stageAll: true)

        #expect(didCommit)
        #expect(viewModel.totalChanges == 0)
        #expect(viewModel.diffsByPath.isEmpty)
    }

    @Test func demoCreatesPullRequestAndWorktree() async throws {
        let manager = ConnectionManager(makeTransport: { _ in MockTransport() })
        await manager.connect(to: DemoConnection.connection, token: "demo")
        let viewModel = GitViewModel(project: muxyProject(), connectionManager: manager)

        let pullRequest = await viewModel.createPullRequest(title: "Native git", body: "", baseBranch: "main", draft: false)
        let didAddWorktree = await viewModel.addWorktree(name: "native-git", branch: "feature/native-git", createBranch: true)

        #expect(pullRequest?.number == 42)
        #expect(didAddWorktree)
        #expect(viewModel.worktrees?.first?.name == "Muxy")
    }

    private func muxyProject() -> Project {
        Project(
            id: UUID(uuidString: "00000000-0000-4000-8000-000000000201")!,
            name: "Muxy",
            path: "/Users/demo/Projects/muxy",
            sortOrder: 0,
            createdAt: "2026-06-08T00:00:00.000Z",
            icon: "terminal",
            logo: nil,
            iconColor: "#22c55e",
            preferredWorktreeParentPath: "/Users/demo/Projects"
        )
    }
}
