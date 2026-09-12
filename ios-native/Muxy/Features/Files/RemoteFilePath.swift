import Foundation

nonisolated enum RemoteFilePath {
    static func join(_ parent: String, _ name: String) -> String {
        parent.isEmpty ? name : "\(parent)/\(name)"
    }

    static func name(_ path: String) -> String {
        path.components(separatedBy: "/").last ?? path
    }

    static func parent(_ path: String) -> String {
        path.components(separatedBy: "/").dropLast().joined(separator: "/")
    }

    static func validatedName(_ value: String) throws -> String {
        let name = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { throw FileManagerError.message("Enter a name.") }
        guard name != ".", name != ".." else { throw FileManagerError.message("Choose another name.") }
        guard !name.contains("/"), !name.contains("\\"), !name.contains("\0") else {
            throw FileManagerError.message("Names cannot contain path separators.")
        }
        return name
    }

    static func validate(_ path: String, allowRoot: Bool = false) throws {
        if path.isEmpty, allowRoot { return }
        let components = path.components(separatedBy: "/")
        guard !path.contains("\0"),
              components.allSatisfy({ !$0.isEmpty && $0 != "." && $0 != ".." }) else {
            throw FileManagerError.message("Choose a path within this project.")
        }
    }

    static func contains(_ path: String, in directory: String) -> Bool {
        directory.isEmpty || path == directory || path.hasPrefix(directory + "/")
    }

    static func affectsDirectory(_ path: String, directory: String) -> Bool {
        path.isEmpty || parent(path) == directory || contains(directory, in: path)
    }

    static func fileExtension(_ path: String) -> String {
        let name = name(path)
        guard let dot = name.lastIndex(of: "."), dot != name.startIndex else { return "" }
        return String(name[name.index(after: dot)...]).lowercased()
    }

    static func isImage(_ path: String) -> Bool {
        ["png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif"].contains(fileExtension(path))
    }
}

nonisolated enum FileManagerError: LocalizedError {
    case worktreeChanged
    case unexpectedResponse
    case message(String)

    var errorDescription: String? {
        switch self {
        case .worktreeChanged:
            "The active worktree changed. Return to Files before continuing. Your draft is preserved."
        case .unexpectedResponse:
            "The server returned an unexpected file response."
        case let .message(message):
            message
        }
    }

    static func description(_ error: Error) -> String {
        (error as? ProtocolError)?.body.message ?? error.localizedDescription
    }
}
