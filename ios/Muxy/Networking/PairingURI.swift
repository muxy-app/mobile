import Foundation

nonisolated enum PairingURIError: Error, Equatable, Sendable {
    case notMuxyScheme
    case missingHost
    case invalidPort
    case malformed
}

nonisolated struct PairingURI: Equatable, Sendable {
    let host: String
    let port: Int
    let serviceName: String?
    let label: String?

    static func parse(_ string: String) throws(PairingURIError) -> PairingURI {
        guard let components = URLComponents(string: string) else { throw .malformed }
        guard components.scheme == "muxy", components.host == "pair" else { throw .notMuxyScheme }

        let items = components.queryItems ?? []

        guard let host = value(for: "host", in: items), !host.isEmpty else { throw .missingHost }

        let port = try resolvePort(from: items)
        let serviceName = value(for: "service", in: items)
        let label = value(for: "label", in: items)

        return PairingURI(host: host, port: port, serviceName: serviceName, label: label)
    }

    private static func resolvePort(from items: [URLQueryItem]) throws(PairingURIError) -> Int {
        guard let raw = value(for: "port", in: items) else { return Endpoint.defaultPort }
        guard let port = Int(raw), (1...65535).contains(port) else { throw .invalidPort }
        return port
    }

    private static func value(for name: String, in items: [URLQueryItem]) -> String? {
        guard let raw = items.first(where: { $0.name == name })?.value else { return nil }
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
