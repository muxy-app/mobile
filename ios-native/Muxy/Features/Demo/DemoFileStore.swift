import Foundation

nonisolated struct DemoFileStore: Sendable {
    private struct Node: Sendable {
        let isDirectory: Bool
        var data = Data()
        var isIgnored = false
    }

    private var nodes: [String: Node]
    private(set) var changedPaths: [String] = []

    init(projectName: String) {
        nodes = [
            "": Node(isDirectory: true),
            "Sources": Node(isDirectory: true),
            "Sources/App.swift": Node(isDirectory: false, data: Data("import SwiftUI\n\nstruct HomeView: View {\n    var body: some View {\n        Text(\"Welcome to \(projectName)\")\n    }\n}\n".utf8)),
            "TerminalKeyboardVisibilityConfiguration.swift": Node(isDirectory: false, data: Data("""
            import Foundation

            struct TerminalKeyboardVisibilityConfiguration {
                let preservesTerminalGrid: Bool
                let followsCursorWhenKeyboardOpens: Bool
                let allowsManualViewportScrolling: Bool
            }

            let configuration = TerminalKeyboardVisibilityConfiguration(preservesTerminalGrid: true, followsCursorWhenKeyboardOpens: true, allowsManualViewportScrolling: true)

            """.utf8)),
            "assets": Node(isDirectory: true),
            "assets/icon.png": Node(isDirectory: false, data: Data(base64Encoded: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=") ?? Data()),
            "README.md": Node(isDirectory: false, data: Data("""
            # \(projectName)

            Browse project files, make a quick edit, and return to your terminal. Files are read from the active worktree, so you can keep working wherever you are.

            ## Working with files

            Tap a folder to open it. Touch and hold a file to select it, then use the actions below to rename, move, or delete it.

            ## Reading and editing

            Long lines wrap to fit the screen. Use the wrapping control to keep code on one line, or tap Edit file to make changes. Your edits are saved only when you choose Save changes.

            """.utf8)),
            ".build": Node(isDirectory: true, isIgnored: true),
            ".build/state.json": Node(isDirectory: false, data: Data("{}\n".utf8), isIgnored: true),
            "archive.bin": Node(isDirectory: false, data: Data([0xFF, 0xFE, 0x00, 0x01])),
        ]
    }

    static func handles(_ method: Method) -> Bool {
        switch method {
        case .filesList, .filesRead, .filesStat, .filesWrite, .filesMkdir, .filesRename, .filesMove, .filesDelete: true
        default: false
        }
    }

    mutating func request<P: Codable & Sendable>(_ method: Method, params: P?) throws -> RawTagged {
        changedPaths = []
        switch method {
        case .filesList:
            let params = try decode(FilePathParams.self, params)
            let path = try normalized(params.path, allowRoot: true)
            try requireDirectory(path)
            let entries = nodes.compactMap { key, node -> RemoteFileEntry? in
                guard !key.isEmpty, RemoteFilePath.parent(key) == path, RemoteFilePath.name(key) != ".git" else { return nil }
                return RemoteFileEntry(name: RemoteFilePath.name(key), path: key, isDirectory: node.isDirectory, isIgnored: node.isIgnored)
            }
            return try RawTagged(type: ResultType.files, value: entries)
        case .filesStat:
            let params = try decode(FilePathParams.self, params)
            let path = try normalized(params.path, allowRoot: true)
            let node = try node(at: path)
            return try RawTagged(type: ResultType.fileStat, value: RemoteFileStat(
                name: RemoteFilePath.name(path), path: path, isDirectory: node.isDirectory, size: node.data.count
            ))
        case .filesRead:
            let params = try decode(FileReadParams.self, params)
            let path = try normalized(params.path)
            let node = try node(at: path)
            guard !node.isDirectory else { throw failure("This item is a folder.") }
            let content: String
            if params.encoding == .base64 {
                content = node.data.base64EncodedString()
            } else {
                guard let text = String(data: node.data, encoding: .utf8) else { throw failure("This file is not valid UTF-8.") }
                content = text
            }
            return try RawTagged(type: ResultType.fileContent, value: RemoteFileContent(
                path: path, content: content, size: node.data.count, encoding: params.encoding
            ))
        case .filesWrite:
            let params = try decode(FileWriteParams.self, params)
            let path = try normalized(params.path)
            try requireDirectory(RemoteFilePath.parent(path))
            guard nodes[path]?.isDirectory != true else { throw failure("This item is a folder.") }
            let data: Data
            if params.encoding == .utf8 {
                data = Data(params.contents.utf8)
            } else {
                guard let decoded = Data(base64Encoded: params.contents) else { throw failure("Invalid Base64 contents.") }
                data = decoded
            }
            guard data.count <= FileClient.maximumFileBytes else { throw failure("File exceeds the 5 MiB write limit.") }
            nodes[path] = Node(isDirectory: false, data: data, isIgnored: nodes[path]?.isIgnored ?? false)
            changedPaths = [path]
            return try pathsResult([path])
        case .filesMkdir:
            let params = try decode(FilePathParams.self, params)
            let requested = try normalized(params.path)
            try requireDirectory(RemoteFilePath.parent(requested))
            let path = uniquePath(requested)
            nodes[path] = Node(isDirectory: true)
            changedPaths = [path]
            return try pathsResult([path])
        case .filesRename:
            let params = try decode(FileRenameParams.self, params)
            let source = try normalized(params.path)
            let name = try RemoteFilePath.validatedName(params.newName)
            let destination = RemoteFilePath.join(RemoteFilePath.parent(source), name)
            _ = try node(at: source)
            if source == destination { return try pathsResult([source]) }
            guard nodes[destination] == nil else { throw failure("“\(name)” already exists in this folder.") }
            relocate(source, to: destination)
            changedPaths = [source, destination]
            return try pathsResult([destination])
        case .filesMove:
            let params = try decode(FileMoveParams.self, params)
            let directory = try normalized(params.into, allowRoot: true)
            try requireDirectory(directory)
            var destinations: [String] = []
            for path in params.paths {
                let source = try normalized(path)
                _ = try node(at: source)
                guard !RemoteFilePath.contains(directory, in: source) else { throw failure("An item cannot be moved into itself.") }
                if RemoteFilePath.parent(source) == directory {
                    destinations.append(source)
                    continue
                }
                let destination = uniquePath(RemoteFilePath.join(directory, RemoteFilePath.name(source)))
                relocate(source, to: destination)
                destinations.append(destination)
                changedPaths += [source, destination]
            }
            return try pathsResult(destinations)
        case .filesDelete:
            let params = try decode(FileDeleteParams.self, params)
            let paths = try params.paths.map { try normalized($0) }
            for path in paths {
                _ = try node(at: path)
                nodes = nodes.filter { !RemoteFilePath.contains($0.key, in: path) }
            }
            changedPaths = paths
            return try RawTagged(type: ResultType.ok, value: EmptyRequestParams())
        default:
            throw DemoError.notFound
        }
    }

    private func node(at path: String) throws -> Node {
        guard let node = nodes[path] else { throw failure("“\(RemoteFilePath.name(path))” was not found.") }
        return node
    }

    private func requireDirectory(_ path: String) throws {
        guard nodes[path]?.isDirectory == true else { throw failure("The folder was not found.") }
    }

    private func normalized(_ path: String, allowRoot: Bool = false) throws -> String {
        let path = path == "." ? "" : path
        try RemoteFilePath.validate(path, allowRoot: allowRoot)
        return path
    }

    private func uniquePath(_ requested: String) -> String {
        let name = RemoteFilePath.name(requested) as NSString
        let parent = RemoteFilePath.parent(requested)
        let ext = name.pathExtension
        var index = 2
        var candidate = requested
        while nodes[candidate] != nil {
            let suffix = ext.isEmpty ? "" : ".\(ext)"
            candidate = RemoteFilePath.join(parent, "\(name.deletingPathExtension) \(index)\(suffix)")
            index += 1
        }
        return candidate
    }

    private mutating func relocate(_ source: String, to destination: String) {
        let affected = nodes.filter { RemoteFilePath.contains($0.key, in: source) }
        for (path, node) in affected {
            nodes[destination + path.dropFirst(source.count)] = node
            nodes[path] = nil
        }
    }

    private func pathsResult(_ paths: [String]) throws -> RawTagged {
        try RawTagged(type: ResultType.filePaths, value: paths)
    }

    private func failure(_ message: String) -> ProtocolError {
        ProtocolError(ProtocolErrorBody(code: ErrorCode.internalError.rawValue, message: message))
    }

    private func decode<T: Decodable, P: Encodable>(_ type: T.Type, _ params: P?) throws -> T {
        guard let params else { throw DemoError.invalidParams }
        return try JSONDecoder().decode(type, from: JSONEncoder().encode(params))
    }
}
