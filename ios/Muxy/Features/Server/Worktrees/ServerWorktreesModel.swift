import Foundation
import MuxyMobile
import Observation
import OSLog

@MainActor
@Observable
final class ServerWorktreesModel {
    struct Row: Identifiable, Equatable {
        let id: String
        let name: String
        let branch: String?
        let projectID: String?
        let isCurrent: Bool
        let isOpenable: Bool
        let isRemovable: Bool
    }

    struct PendingRemoval: Identifiable {
        let row: Row
        let removal: WorktreeRemoval

        var id: String { row.id }
        var hasUncommittedChanges: Bool { removal.dirty }
    }

    let projectID: String
    private(set) var worktrees: [GitWorktree]?
    private(set) var isLoading = false
    private(set) var busyRowID: String?
    private(set) var pendingRemoval: PendingRemoval?
    private(set) var errorMessage: String?

    private let server: ServerController

    init(projectID: String, server: ServerController) {
        self.projectID = projectID
        self.server = server
    }

    var rows: [Row]? {
        worktrees?.map(row(for:))
    }

    var isBusy: Bool {
        busyRowID != nil
    }

    func refresh() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            worktrees = try await server.git(for: projectID).worktrees()
        } catch {
            report(error, while: "loading worktrees")
        }
    }

    func projectToOpen(_ row: Row) async -> String? {
        if let projectID = row.projectID { return projectID }
        busyRowID = row.id
        errorMessage = nil
        defer { busyRowID = nil }

        do {
            let project = try await server.git(for: projectID).registerWorktree(directory: row.id)
            server.reloadProjects()
            return project.id
        } catch {
            report(error, while: "adding a worktree to Muxy")
            return nil
        }
    }

    func create(branch: String, createsBranch: Bool) async -> Bool {
        let trimmed = branch.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return false }
        errorMessage = nil

        do {
            _ = try await server.git(for: projectID).createWorktree(branch: trimmed, base: createsBranch ? "HEAD" : nil)
            server.reloadProjects()
            await refresh()
            return true
        } catch {
            report(error, while: "creating a worktree")
            return false
        }
    }

    func prepareRemoval(of row: Row) async {
        guard row.isRemovable, let worktreeProjectID = row.projectID else { return }
        busyRowID = row.id
        errorMessage = nil
        defer { busyRowID = nil }

        do {
            let removal = try await server.git(for: worktreeProjectID).inspectWorktreeRemoval()
            pendingRemoval = PendingRemoval(row: row, removal: removal)
        } catch {
            report(error, while: "checking a worktree before removal")
        }
    }

    func remove(_ pending: PendingRemoval) async {
        guard let worktreeProjectID = pending.row.projectID else { return }
        pendingRemoval = nil
        busyRowID = pending.row.id
        defer { busyRowID = nil }

        do {
            try await server.git(for: worktreeProjectID).removeWorktree(expected: pending.removal)
            server.reloadProjects()
            await refresh()
        } catch {
            report(error, while: "removing a worktree")
        }
    }

    func cancelRemoval() {
        pendingRemoval = nil
    }

    private var rootProjectID: String {
        server.project(for: projectID)?.parentId ?? projectID
    }

    private func row(for worktree: GitWorktree) -> Row {
        let worktreeProjectID = worktree.primary ? rootProjectID : worktree.registered
        let isCurrent = worktreeProjectID == projectID
        return Row(
            id: worktree.directory,
            name: worktreeProjectID.flatMap(server.project(for:))?.name
                ?? URL(fileURLWithPath: worktree.directory).lastPathComponent,
            branch: worktree.branch,
            projectID: worktreeProjectID,
            isCurrent: isCurrent,
            isOpenable: !worktree.prunable && !worktree.bare,
            isRemovable: !worktree.primary && worktree.registered != nil && !worktree.locked && !isCurrent
        )
    }

    private func report(_ error: any Error, while action: String) {
        let failure = ServerRequestError.wrapping(error, serverName: server.serverName)
        errorMessage = failure.localizedDescription
        Log.client.error("Worktrees failed while \(action, privacy: .public): \(failure.localizedDescription, privacy: .private)")
    }
}
