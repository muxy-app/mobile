import Foundation
import OSLog

protocol FileChannel: Sendable {
    func request<P: Codable & Sendable>(_ method: Method, params: P?) async throws -> RawTagged
    func events() async -> AsyncStream<EventEnvelope>
    func stateUpdates() async -> AsyncStream<ConnectionState>
}

extension ConnectionManager: FileChannel {}

nonisolated struct FileClient: Sendable {
    struct Directory: Sendable {
        let path: String
        let entries: [RemoteFileEntry]
    }

    static let maximumFileBytes = 5 * 1024 * 1024

    let projectID: UUID
    let channel: any FileChannel

    func activeWorktreeID() async throws -> UUID? {
        do {
            let workspace: Workspace = try await request(
                .getWorkspace,
                params: GetWorkspaceParams(projectID: projectID.uuidString),
                resultType: ResultType.workspace
            )
            guard workspace.projectID == projectID else { throw FileManagerError.unexpectedResponse }
            return workspace.worktreeID
        } catch let error as ProtocolError where error.code == .notFound {
            return nil
        }
    }

    func list(_ path: String) async throws -> Directory {
        try RemoteFilePath.validate(path, allowRoot: true)
        let directoryPath = try await resolvedDirectoryPath(path)
        let entries: [RemoteFileEntry] = try await request(.filesList, params: pathParams(directoryPath), resultType: ResultType.files)
        var seen: Set<String> = []
        for entry in entries {
            try RemoteFilePath.validate(entry.path)
            guard seen.insert(entry.path).inserted else {
                throw FileManagerError.unexpectedResponse
            }
        }
        let sorted = entries.sorted {
            if $0.isDirectory != $1.isDirectory { return $0.isDirectory }
            return $0.name.localizedStandardCompare($1.name) == .orderedAscending
        }
        return Directory(path: directoryPath, entries: sorted)
    }

    func stat(_ path: String) async throws -> RemoteFileStat {
        try RemoteFilePath.validate(path)
        let stat: RemoteFileStat = try await request(.filesStat, params: pathParams(path), resultType: ResultType.fileStat)
        try RemoteFilePath.validate(stat.path, allowRoot: stat.isDirectory)
        guard stat.size >= 0 else { throw FileManagerError.unexpectedResponse }
        return stat
    }

    func read(_ path: String, encoding: FileEncoding) async throws -> RemoteFileContent {
        try RemoteFilePath.validate(path)
        let content: RemoteFileContent = try await request(
            .filesRead,
            params: FileReadParams(projectID: projectID.uuidString, path: path, encoding: encoding),
            resultType: ResultType.fileContent
        )
        try RemoteFilePath.validate(content.path)
        guard content.encoding == encoding, content.size >= 0,
              content.size <= Self.maximumFileBytes else { throw FileManagerError.unexpectedResponse }
        return content
    }

    func write(_ path: String, contents: String, worktreeID: UUID?) async throws {
        try RemoteFilePath.validate(path)
        guard contents.utf8.count <= Self.maximumFileBytes else {
            throw FileManagerError.message("Files larger than 5 MiB cannot be saved from the app.")
        }
        try await requireWorktree(worktreeID)
        let _: [String] = try await request(
            .filesWrite,
            params: FileWriteParams(projectID: projectID.uuidString, path: path, contents: contents, encoding: .utf8),
            resultType: ResultType.filePaths
        )
    }

    func create(_ path: String, worktreeID: UUID?) async throws -> String {
        try RemoteFilePath.validate(path)
        let temporaryPath = RemoteFilePath.join(RemoteFilePath.parent(path), ".muxy-mobile-create-\(UUID().uuidString)")
        try await write(temporaryPath, contents: "", worktreeID: worktreeID)
        do {
            return try await rename(temporaryPath, name: RemoteFilePath.name(path), worktreeID: worktreeID)
        } catch {
            do {
                try await delete([temporaryPath], worktreeID: worktreeID)
            } catch {
                Log.files.error("Temporary file cleanup failed: \(FileManagerError.description(error), privacy: .private)")
            }
            throw error
        }
    }

    func mkdir(_ path: String, worktreeID: UUID?) async throws -> String {
        try RemoteFilePath.validate(path)
        try await requireWorktree(worktreeID)
        let paths: [String] = try await request(.filesMkdir, params: pathParams(path), resultType: ResultType.filePaths)
        return try singlePath(paths)
    }

    func rename(_ path: String, name: String, worktreeID: UUID?) async throws -> String {
        try RemoteFilePath.validate(path)
        let name = try RemoteFilePath.validatedName(name)
        try await requireWorktree(worktreeID)
        let paths: [String] = try await request(
            .filesRename,
            params: FileRenameParams(projectID: projectID.uuidString, path: path, newName: name),
            resultType: ResultType.filePaths
        )
        return try singlePath(paths)
    }

    func move(_ paths: [String], into destination: String, worktreeID: UUID?) async throws {
        try validateSources(paths)
        try RemoteFilePath.validate(destination, allowRoot: true)
        guard !paths.contains(where: { RemoteFilePath.contains(destination, in: $0) }) else {
            throw FileManagerError.message("An item cannot be moved into itself.")
        }
        try await requireWorktree(worktreeID)
        let _: [String] = try await request(
            .filesMove,
            params: FileMoveParams(projectID: projectID.uuidString, paths: paths, into: destination.isEmpty ? "." : destination),
            resultType: ResultType.filePaths
        )
    }

    func delete(_ paths: [String], worktreeID: UUID?) async throws {
        try validateSources(paths)
        try await requireWorktree(worktreeID)
        let result = try await channel.request(.filesDelete, params: FileDeleteParams(projectID: projectID.uuidString, paths: paths))
        guard result.type == ResultType.ok else { throw FileManagerError.unexpectedResponse }
    }

    private func requireWorktree(_ worktreeID: UUID?) async throws {
        try Task.checkCancellation()
        guard try await activeWorktreeID() == worktreeID else { throw FileManagerError.worktreeChanged }
        try Task.checkCancellation()
    }

    private func resolvedDirectoryPath(_ path: String) async throws -> String {
        guard !path.isEmpty else { return "" }
        let directory = try await stat(path)
        guard directory.isDirectory else {
            throw FileManagerError.message("This item is no longer a folder. Return to its parent folder to refresh it.")
        }
        return directory.path
    }

    private func pathParams(_ path: String) -> FilePathParams {
        FilePathParams(projectID: projectID.uuidString, path: path.isEmpty ? "." : path)
    }

    private func validateSources(_ paths: [String]) throws {
        guard !paths.isEmpty else { throw FileManagerError.message("Select at least one item.") }
        for path in paths { try RemoteFilePath.validate(path) }
    }

    private func singlePath(_ paths: [String]) throws -> String {
        guard paths.count == 1, let path = paths.first else { throw FileManagerError.unexpectedResponse }
        try RemoteFilePath.validate(path)
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
}
