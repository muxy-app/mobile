import Foundation

nonisolated struct Endpoint: Equatable, Sendable {
    static let defaultPort = 4865

    let host: String
    let port: Int

    var webSocketURL: URL? {
        let trimmedHost = host.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedHost.isEmpty else { return nil }
        var components = URLComponents()
        components.scheme = "ws"
        components.host = normalizedHost(trimmedHost)
        components.port = port
        return components.url
    }

    private func normalizedHost(_ host: String) -> String {
        guard host.contains(":") else { return host }
        guard !host.hasPrefix("[") else { return host }
        return "[\(host)]"
    }
}
