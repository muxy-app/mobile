import Foundation
import OSLog

struct FileClient {
    nonisolated struct Directory: Sendable {
        let path: String
        let entries: [RemoteFileEntry]
    }

    let backend: any FileBackend

    func currentScope() async throws -> FileScope {
        try await backend.currentScope()
    }

    func list(_ path: String) async throws -> Directory {
        try RemoteFilePath.validate(path, allowRoot: true)
        let directoryPath = try await resolvedDirectoryPath(path)
        let entries = try await backend.list(directoryPath)
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
        let stat = try await backend.stat(path)
        try RemoteFilePath.validate(stat.path, allowRoot: stat.isDirectory)
        guard stat.size >= 0 else { throw FileManagerError.unexpectedResponse }
        return stat
    }

    func readText(_ path: String) async throws -> RemoteTextFile {
        try RemoteFilePath.validate(path)
        let file = try await backend.readText(path)
        try RemoteFilePath.validate(file.path)
        guard file.size >= 0, file.size <= FileLimits.maximumBytes else { throw FileManagerError.unexpectedResponse }
        return file
    }

    func readData(_ path: String) async throws -> Data {
        try RemoteFilePath.validate(path)
        let data = try await backend.readData(path)
        guard data.count <= FileLimits.maximumBytes else { throw FileManagerError.unexpectedResponse }
        return data
    }

    func write(_ path: String, contents: String, in scope: FileScope) async throws {
        try RemoteFilePath.validate(path)
        guard contents.utf8.count <= FileLimits.maximumBytes else {
            throw FileManagerError.message("Files larger than 5 MiB cannot be saved from the app.")
        }
        try await backend.writeText(contents, to: path, in: scope)
    }

    func create(_ path: String, in scope: FileScope) async throws -> String {
        try RemoteFilePath.validate(path)
        let temporaryPath = RemoteFilePath.join(RemoteFilePath.parent(path), ".muxy-mobile-create-\(UUID().uuidString)")
        try await write(temporaryPath, contents: "", in: scope)
        do {
            return try await rename(temporaryPath, name: RemoteFilePath.name(path), in: scope)
        } catch {
            do {
                try await delete([temporaryPath], in: scope)
            } catch {
                Log.files.error("Temporary file cleanup failed: \(FileManagerError.description(error), privacy: .private)")
            }
            throw error
        }
    }

    func mkdir(_ path: String, in scope: FileScope) async throws -> String {
        try RemoteFilePath.validate(path)
        let created = try await backend.createDirectory(path, in: scope)
        try RemoteFilePath.validate(created)
        return created
    }

    func rename(_ path: String, name: String, in scope: FileScope) async throws -> String {
        try RemoteFilePath.validate(path)
        let name = try RemoteFilePath.validatedName(name)
        let renamed = try await backend.rename(path, to: name, in: scope)
        try RemoteFilePath.validate(renamed)
        return renamed
    }

    func move(_ paths: [String], into destination: String, in scope: FileScope) async throws {
        try validateSources(paths)
        try RemoteFilePath.validate(destination, allowRoot: true)
        guard !paths.contains(where: { RemoteFilePath.contains(destination, in: $0) }) else {
            throw FileManagerError.message("An item cannot be moved into itself.")
        }
        try await backend.move(paths, into: destination, in: scope)
    }

    func delete(_ paths: [String], in scope: FileScope) async throws {
        try validateSources(paths)
        try await backend.delete(paths, in: scope)
    }

    private func resolvedDirectoryPath(_ path: String) async throws -> String {
        guard !path.isEmpty else { return "" }
        let directory = try await stat(path)
        guard directory.isDirectory else {
            throw FileManagerError.message("This item is no longer a folder. Return to its parent folder to refresh it.")
        }
        return directory.path
    }

    private func validateSources(_ paths: [String]) throws {
        guard !paths.isEmpty else { throw FileManagerError.message("Select at least one item.") }
        for path in paths { try RemoteFilePath.validate(path) }
    }
}
