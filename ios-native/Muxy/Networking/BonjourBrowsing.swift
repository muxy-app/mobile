import Foundation

nonisolated struct DiscoveredService: Identifiable, Equatable, Sendable {
    let name: String
    let host: String
    let port: Int

    var id: String { name }
}

@MainActor
protocol BonjourBrowsing: AnyObject {
    var services: [DiscoveredService] { get }
    func start()
    func stop()
}

enum BonjourMapping {
    static let serviceType = "_muxy._tcp"

    private static let unreachableHosts: Set<String> = ["localhost", "::1", "127.0.0.1", "0.0.0.0"]

    static func service(name: String, hostName: String?, port: Int) -> DiscoveredService? {
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanName.isEmpty else { return nil }

        guard let host = normalizedHost(hostName) else { return nil }
        guard (1...65535).contains(port) else { return nil }

        return DiscoveredService(name: cleanName, host: host, port: port)
    }

    static func normalizedHost(_ hostName: String?) -> String? {
        guard let hostName else { return nil }
        var host = hostName.trimmingCharacters(in: .whitespacesAndNewlines)
        if host.hasSuffix(".") {
            host.removeLast()
        }
        guard !host.isEmpty else { return nil }
        guard !unreachableHosts.contains(host.lowercased()) else { return nil }
        return host
    }
}
