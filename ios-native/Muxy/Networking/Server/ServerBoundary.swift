import Foundation
import MuxyMobile

typealias ServerProject = MuxyMobile.Project

nonisolated struct TerminalGridSize: Hashable, Sendable {
    let columns: UInt16
    let rows: UInt16
}

nonisolated protocol ServerConnector: Sendable {
    func connect(
        to credential: ServerCredential,
        events: @escaping @Sendable (ConnectionEvent) -> Void
    ) async throws -> any ServerConnection
}

nonisolated protocol ServerConnection: AnyObject, Sendable {
    var serverVersion: String { get }
    func projects() async throws -> [ServerProject]
    func sessions(projectId: String) async throws -> [Session]
    func createSession(projectId: String, size: TerminalGridSize) async throws -> Session
    func endSession(_ sessionId: UInt64) async throws
    func attach(sessionId: UInt64, size: TerminalGridSize) async throws -> any ServerTerminalChannel
    func files(projectId: String) throws -> any ServerProjectFiles
    func git(projectId: String) throws -> any ServerGitRepository
    func disconnect()
}

nonisolated protocol ServerProjectFiles: AnyObject, Sendable {
    func list(path: String) async throws -> [FileEntry]
    func stat(path: String) async throws -> FileInfo
    func readText(path: String) async throws -> String
    func readBytes(path: String) async throws -> Data
    func writeText(path: String, text: String) async throws -> String
    func createDirectory(path: String) async throws -> String
    func rename(path: String, name: String) async throws -> String
    func moveFiles(paths: [String], into directory: String) async throws -> [String]
    func deleteFiles(paths: [String]) async throws
    func watch()
    func unwatch()
}

nonisolated protocol ServerGitRepository: AnyObject, Sendable {
    func summary() async throws -> GitSummary?
    func status(includePullRequest: Bool) async throws -> GitStatus
    func branches() async throws -> [GitBranch]
    func diff(path: String, staged: Bool, lineLimit: UInt32?) async throws -> GitDiff
    func commit(message: String, stageAll: Bool) async throws
    func pull() async throws
    func push(setUpstream: Bool) async throws
    func switchBranch(name: String) async throws
    func createBranch(name: String) async throws
    func createPullRequest(title: String, body: String, baseBranch: String?, draft: Bool) async throws -> GitPullRequest
    func mergePullRequest(number: UInt64, method: GitMergeMethod, deleteBranch: Bool, expectedHead: String?) async throws
    func worktrees() async throws -> [GitWorktree]
    func createWorktree(branch: String, base: String?) async throws -> ServerProject
    func registerWorktree(directory: String) async throws -> ServerProject
    func inspectWorktreeRemoval() async throws -> WorktreeRemoval
    func removeWorktree(expected: WorktreeRemoval) async throws
}

nonisolated protocol ServerTerminalChannel: AnyObject, Sendable {
    var sessionId: UInt64 { get }
    func screen() -> Screen
    func send(text: String)
    func send(key: Key, modifiers: Modifiers)
    func paste(_ text: String)
    func resize(to size: TerminalGridSize) async throws
    func scrollback(maxRows: UInt16) async throws -> any ScrollbackSnapshot
    func detach() async throws
}

nonisolated protocol ScrollbackSnapshot: AnyObject, Sendable {
    var historyRows: UInt64 { get }
    func lines() -> [Line]
    func loadOlder(maxRows: UInt16) async throws -> [Line]
}

nonisolated protocol ServerPairingService: Sendable {
    func parse(_ link: String) throws -> PairingLink
    func pair(link: String, deviceName: String) async throws -> ServerCredential
}
