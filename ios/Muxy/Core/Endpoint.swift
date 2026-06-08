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
        components.host = trimmedHost
        components.port = port
        return components.url
    }
}
