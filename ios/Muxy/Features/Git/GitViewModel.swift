import Foundation
import Observation
import OSLog

@MainActor
@Observable
final class GitViewModel {
    private(set) var status: VCSStatus?
    private(set) var branches: VCSBranches?
    private(set) var diffs: [GitDiffKey: VCSDiff] = [:]
    private(set) var isLoadingStatus = false
    private(set) var isLoadingBranches = false
    private(set) var loadingDiffs: Set<GitDiffKey> = []
    private(set) var errorMessage: String?

    private let backend: any GitBackend

    init(backend: any GitBackend) {
        self.backend = backend
    }

    var totalChanges: Int {
        guard let status else { return 0 }
        return Set((status.stagedFiles + status.changedFiles).map(\.path)).count
    }

    func canPush(_ status: VCSStatus) -> Bool {
        status.aheadCount > 0 || publishes(status)
    }

    func pushTitle(for status: VCSStatus) -> String {
        if publishes(status) { return "Publish Branch" }
        return status.aheadCount > 0 ? "Push \(status.aheadCount)" : "Push"
    }

    func refreshStatus() async {
        isLoadingStatus = true
        errorMessage = nil
        defer { isLoadingStatus = false }

        do {
            status = try await backend.status()
        } catch {
            report(error, while: "refreshing git status")
        }
    }

    func refreshBranches() async {
        isLoadingBranches = true
        errorMessage = nil
        defer { isLoadingBranches = false }

        do {
            branches = try await backend.branches()
        } catch {
            report(error, while: "refreshing git branches")
        }
    }

    func loadDiff(_ key: GitDiffKey, full: Bool = false) async {
        loadingDiffs.insert(key)
        errorMessage = nil
        defer { loadingDiffs.remove(key) }

        do {
            diffs[key] = try await backend.diff(for: key, full: full)
        } catch {
            report(error, while: "loading a git diff")
        }
    }

    func invalidateDiffs() {
        diffs.removeAll()
    }

    func commit(message: String, stageAll: Bool) async -> Bool {
        let trimmed = message.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return false }

        do {
            try await backend.commit(message: trimmed, stageAll: stageAll)
            invalidateDiffs()
            await refreshStatus()
            return true
        } catch {
            report(error, while: "committing")
            return false
        }
    }

    func pull() async {
        do {
            try await backend.pull()
            invalidateDiffs()
            await refreshStatus()
        } catch {
            report(error, while: "pulling")
        }
    }

    func push() async {
        do {
            try await backend.push()
            await refreshStatus()
        } catch {
            report(error, while: "pushing")
        }
    }

    func switchBranch(_ branch: String) async {
        do {
            try await backend.switchBranch(branch)
            invalidateDiffs()
            await refreshStatus()
            await refreshBranches()
        } catch {
            report(error, while: "switching git branch")
        }
    }

    func createBranch(_ name: String) async -> Bool {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return false }

        do {
            try await backend.createBranch(trimmed)
            await refreshStatus()
            await refreshBranches()
            return true
        } catch {
            report(error, while: "creating a git branch")
            return false
        }
    }

    func createPullRequest(title: String, body: String, baseBranch: String?, draft: Bool) async -> VCSPRCreated? {
        let trimmedTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedTitle.isEmpty else { return nil }

        do {
            let created = try await backend.createPullRequest(
                title: trimmedTitle,
                body: body.trimmingCharacters(in: .whitespacesAndNewlines),
                baseBranch: baseBranch?.trimmingCharacters(in: .whitespacesAndNewlines),
                draft: draft
            )
            await refreshStatus()
            return created
        } catch {
            report(error, while: "creating a pull request")
            return nil
        }
    }

    func mergePullRequest(_ pullRequest: VCSPullRequest, method: VCSMergeMethod, deleteBranch: Bool) async -> Bool {
        do {
            try await backend.mergePullRequest(pullRequest, method: method, deleteBranch: deleteBranch)
            await refreshStatus()
            return true
        } catch {
            report(error, while: "merging a pull request")
            return false
        }
    }

    private func publishes(_ status: VCSStatus) -> Bool {
        backend.canPublishBranch && !status.hasUpstream
    }

    private func report(_ error: any Error, while action: String) {
        errorMessage = error.localizedDescription
        Log.client.error("Git failed while \(action, privacy: .public): \(error.localizedDescription, privacy: .private)")
    }
}
