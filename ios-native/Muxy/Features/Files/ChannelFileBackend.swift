import Foundation
import OSLog

protocol FileChannel: Sendable {
    func request<P: Codable & Sendable>(_ method: Method, params: P?) async throws -> RawTagged
    func events() async -> AsyncStream<EventEnvelope>
    func stateUpdates() async -> AsyncStream<ConnectionState>
}

extension ConnectionManager: FileChannel {}

struct ChannelFileBackend: FileBackend {
    let projectID: UUID
    let channel: any FileChannel

    func connectionStates() async -> AsyncStream<Bool> {
        .relaying(await channel.stateUpdates()) { $0 == .connected }
    }

    func events() async -> AsyncStream<FileBackendEvent> {
        let projectID = projectID
        return .relaying(await channel.events()) { Self.event(from: $0, projectID: projectID) }
    }

    func currentScope() async throws -> FileScope {
        do {
            let workspace: Workspace = try await request(
                .getWorkspace,
                params: GetWorkspaceParams(projectID: projectID.uuidString),
                resultType: ResultType.workspace
            )
            guard workspace.projectID == projectID else { throw FileManagerError.unexpectedResponse }
            return FileScope(worktreeID: workspace.worktreeID)
        } catch let error as ProtocolError where error.code == .notFound {
            return FileScope(worktreeID: nil)
        }
    }

    func list(_ path: String) async throws -> [RemoteFileEntry] {
        try await request(.filesList, params: pathParams(path), resultType: ResultType.files)
    }

    func stat(_ path: String) async throws -> RemoteFileStat {
        try await request(.filesStat, params: pathParams(path), resultType: ResultType.fileStat)
    }

    func readText(_ path: String) async throws -> RemoteTextFile {
        do {
            let content = try await read(path, encoding: .utf8)
            return RemoteTextFile(path: content.path, text: content.content, size: content.size)
        } catch where Self.isNotText(error) {
            throw FileManagerError.notText
        }
    }

    func readData(_ path: String) async throws -> Data {
        let content = try await read(path, encoding: .base64)
        guard let data = await Self.decodeBase64(content.content) else { throw FileManagerError.unexpectedResponse }
        return data
    }

    func writeText(_ text: String, to path: String, in scope: FileScope) async throws {
        try await requireScope(scope)
        let _: [String] = try await request(
            .filesWrite,
            params: FileWriteParams(projectID: projectID.uuidString, path: path, contents: text, encoding: .utf8),
            resultType: ResultType.filePaths
        )
    }

    func createDirectory(_ path: String, in scope: FileScope) async throws -> String {
        try await requireScope(scope)
        let paths: [String] = try await request(.filesMkdir, params: pathParams(path), resultType: ResultType.filePaths)
        return try singlePath(paths)
    }

    func rename(_ path: String, to name: String, in scope: FileScope) async throws -> String {
        try await requireScope(scope)
        let paths: [String] = try await request(
            .filesRename,
            params: FileRenameParams(projectID: projectID.uuidString, path: path, newName: name),
            resultType: ResultType.filePaths
        )
        return try singlePath(paths)
    }

    func move(_ paths: [String], into directory: String, in scope: FileScope) async throws {
        try await requireScope(scope)
        let _: [String] = try await request(
            .filesMove,
            params: FileMoveParams(projectID: projectID.uuidString, paths: paths, into: directory.isEmpty ? "." : directory),
            resultType: ResultType.filePaths
        )
    }

    func delete(_ paths: [String], in scope: FileScope) async throws {
        try await requireScope(scope)
        let result = try await channel.request(.filesDelete, params: FileDeleteParams(projectID: projectID.uuidString, paths: paths))
        guard result.type == ResultType.ok else { throw FileManagerError.unexpectedResponse }
    }

    private func read(_ path: String, encoding: FileEncoding) async throws -> RemoteFileContent {
        let content: RemoteFileContent = try await request(
            .filesRead,
            params: FileReadParams(projectID: projectID.uuidString, path: path, encoding: encoding),
            resultType: ResultType.fileContent
        )
        guard content.encoding == encoding else { throw FileManagerError.unexpectedResponse }
        return content
    }

    private func requireScope(_ scope: FileScope) async throws {
        try Task.checkCancellation()
        guard try await currentScope() == scope else { throw FileManagerError.worktreeChanged }
        try Task.checkCancellation()
    }

    private func pathParams(_ path: String) -> FilePathParams {
        FilePathParams(projectID: projectID.uuidString, path: path.isEmpty ? "." : path)
    }

    private func singlePath(_ paths: [String]) throws -> String {
        guard paths.count == 1, let path = paths.first else { throw FileManagerError.unexpectedResponse }
        return path
    }

    private func request<P: Codable & Sendable, R: Decodable & Sendable>(
        _ method: Method,
        params: P,
        resultType: String
    ) async throws -> R {
        try Task.checkCancellation()
        let result = try await channel.request(method, params: params)
        try Task.checkCancellation()
        guard result.type == resultType else { throw FileManagerError.unexpectedResponse }
        return try result.decode(R.self)
    }

    nonisolated private static func isNotText(_ error: any Error) -> Bool {
        let message = FileManagerError.description(error).lowercased()
        return message.contains("utf-8") || message.contains("utf8")
    }

    nonisolated private static func decodeBase64(_ content: String) async -> Data? {
        await Task.detached(priority: .userInitiated) { Data(base64Encoded: content) }.value
    }

    nonisolated private static func event(from envelope: EventEnvelope, projectID: UUID) -> FileBackendEvent? {
        guard let data = envelope.data else { return nil }
        do {
            if envelope.event == EventName.workspaceChanged, data.type == EventType.workspace {
                let workspace = try data.decode(Workspace.self)
                guard workspace.projectID == projectID else { return nil }
                return .scopeChanged(FileScope(worktreeID: workspace.worktreeID))
            }
            guard envelope.event == EventName.fileChanged, data.type == EventType.fileChanged else { return nil }
            let change = try data.decode(FileChangedEvent.self)
            guard change.projectID == projectID else { return nil }
            return .filesChanged(FileChange(
                scope: FileScope(worktreeID: change.worktreeID),
                paths: change.paths,
                requiresRescan: change.truncated
            ))
        } catch {
            Log.files.error("Invalid file event: \(FileManagerError.description(error), privacy: .private)")
            return nil
        }
    }
}
