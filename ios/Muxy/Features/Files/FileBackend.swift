import Foundation

protocol FileBackend {
    func connectionStates() async -> AsyncStream<Bool>
    func events() async -> AsyncStream<FileBackendEvent>
    func currentScope() async throws -> FileScope
    func list(_ path: String) async throws -> [RemoteFileEntry]
    func stat(_ path: String) async throws -> RemoteFileStat
    func readText(_ path: String) async throws -> RemoteTextFile
    func readData(_ path: String) async throws -> Data
    func writeText(_ text: String, to path: String, in scope: FileScope) async throws
    func createDirectory(_ path: String, in scope: FileScope) async throws -> String
    func rename(_ path: String, to name: String, in scope: FileScope) async throws -> String
    func move(_ paths: [String], into directory: String, in scope: FileScope) async throws
    func delete(_ paths: [String], in scope: FileScope) async throws
}

nonisolated enum FileBackendEvent: Sendable, Equatable {
    case scopeChanged(FileScope)
    case filesChanged(FileChange)
}
