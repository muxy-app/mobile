import Foundation
import MuxyMobile

struct ServerFileBackend: FileBackend {
    let projectID: String
    let server: ServerController

    func connectionStates() async -> AsyncStream<Bool> {
        server.connectionStates()
    }

    func events() async -> AsyncStream<FileBackendEvent> {
        .relaying(server.fileChanges(in: projectID)) { paths in
            .filesChanged(FileChange(scope: .project, paths: paths, requiresRescan: paths.isEmpty))
        }
    }

    func currentScope() async throws -> FileScope {
        .project
    }

    func list(_ path: String) async throws -> [RemoteFileEntry] {
        try await request { try await $0.list(path: path) }.map(RemoteFileEntry.init)
    }

    func stat(_ path: String) async throws -> RemoteFileStat {
        RemoteFileStat(try await request { try await $0.stat(path: path) })
    }

    func readText(_ path: String) async throws -> RemoteTextFile {
        try await request { files in
            do {
                return Self.textFile(at: path, text: try await files.readText(path: path))
            } catch let error as MobileError {
                guard case .Server = error, let bytes = try? await files.readBytes(path: path) else { throw error }
                guard let text = String(data: bytes, encoding: .utf8) else { throw FileManagerError.notText }
                return Self.textFile(at: path, text: text)
            }
        }
    }

    func readData(_ path: String) async throws -> Data {
        try await request { try await $0.readBytes(path: path) }
    }

    func writeText(_ text: String, to path: String, in scope: FileScope) async throws {
        _ = try await request { try await $0.writeText(path: path, text: text) }
    }

    func createDirectory(_ path: String, in scope: FileScope) async throws -> String {
        try await request { try await $0.createDirectory(path: path) }
    }

    func rename(_ path: String, to name: String, in scope: FileScope) async throws -> String {
        try await request { try await $0.rename(path: path, name: name) }
    }

    func move(_ paths: [String], into directory: String, in scope: FileScope) async throws {
        _ = try await request { try await $0.moveFiles(paths: paths, into: directory) }
    }

    func delete(_ paths: [String], in scope: FileScope) async throws {
        try await request { try await $0.deleteFiles(paths: paths) }
    }

    private func request<Value>(_ call: (any ServerProjectFiles) async throws -> Value) async throws -> Value {
        do {
            return try await call(try server.files(for: projectID))
        } catch {
            throw ServerRequestError.wrapping(error, serverName: server.serverName)
        }
    }

    private static func textFile(at path: String, text: String) -> RemoteTextFile {
        RemoteTextFile(path: path, text: text, size: text.utf8.count)
    }
}
