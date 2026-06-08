import Foundation

nonisolated struct Device: Codable, Identifiable, Sendable, Equatable, Hashable {
    let id: UUID
    var name: String
    var host: String
    var port: Int
    var pairingState: PairingState
    var serviceName: String?
    var discoverySource: DiscoverySource

    var endpoint: Endpoint {
        Endpoint(host: host, port: port)
    }
}
