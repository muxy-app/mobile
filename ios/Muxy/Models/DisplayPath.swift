import Foundation

nonisolated enum DisplayPath {
    static func abbreviated(_ path: String) -> String {
        let components = path.split(separator: "/", omittingEmptySubsequences: true)
        guard components.count >= 2, homeRoots.contains(String(components[0])) else { return path }
        let rest = components.dropFirst(2).joined(separator: "/")
        return rest.isEmpty ? "~" : "~/\(rest)"
    }

    static func folderName(_ path: String) -> String {
        (path as NSString).lastPathComponent
    }

    private static let homeRoots: Set<String> = ["Users", "home"]
}
