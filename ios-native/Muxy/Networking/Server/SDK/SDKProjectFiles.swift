import Foundation
import MuxyMobile

nonisolated final class SDKProjectFiles: ServerProjectFiles {
    private let files: MuxyMobile.ProjectFiles
    private let lanes: SDKLanes

    init(files: MuxyMobile.ProjectFiles, lanes: SDKLanes) {
        self.files = files
        self.lanes = lanes
    }

    func list(path: String) async throws -> [FileEntry] {
        try await lanes.fileRequest { [files] in
            try files.list(path: path)
        }
    }

    func stat(path: String) async throws -> FileInfo {
        try await lanes.fileRequest { [files] in
            try files.stat(path: path)
        }
    }

    func readText(path: String) async throws -> String {
        try await lanes.fileRequest { [files] in
            try files.readText(path: path)
        }
    }

    func readBytes(path: String) async throws -> Data {
        try await lanes.fileRequest { [files] in
            try files.readBytes(path: path)
        }
    }

    func writeText(path: String, text: String) async throws -> String {
        try await lanes.fileRequest { [files] in
            try files.writeText(path: path, text: text)
        }
    }

    func createDirectory(path: String) async throws -> String {
        try await lanes.fileRequest { [files] in
            try files.createDirectory(path: path)
        }
    }

    func rename(path: String, name: String) async throws -> String {
        try await lanes.fileRequest { [files] in
            try files.rename(path: path, name: name)
        }
    }

    func moveFiles(paths: [String], into directory: String) async throws -> [String] {
        try await lanes.fileRequest { [files] in
            try files.moveFiles(paths: paths, into: directory)
        }
    }

    func deleteFiles(paths: [String]) async throws {
        try await lanes.fileRequest { [files] in
            try files.deleteFiles(paths: paths)
        }
    }

    func watch() {
        lanes.enqueueFileWork { [files] in
            try files.watch()
        }
    }

    func unwatch() {
        lanes.enqueueFileWork { [files] in
            try files.unwatch()
        }
    }
}
