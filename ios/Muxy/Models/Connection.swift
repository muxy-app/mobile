import Foundation

nonisolated enum ConnectionKind: String, Codable, Sendable {
    case device
    case ssh
}

nonisolated struct Connection: Codable, Identifiable, Sendable, Equatable, Hashable {
    let id: UUID
    var name: String
    var host: String
    var port: Int
    var kind: ConnectionKind
    var pairingState: PairingState
    var serviceName: String?
    var discoverySource: DiscoverySource
    var sshConfig: SSHConfig?

    var endpoint: Endpoint {
        Endpoint(host: host, port: port)
    }

    init(
        id: UUID,
        name: String,
        host: String,
        port: Int,
        kind: ConnectionKind = .device,
        pairingState: PairingState = .notPaired,
        serviceName: String? = nil,
        discoverySource: DiscoverySource = .manual,
        sshConfig: SSHConfig? = nil
    ) {
        self.id = id
        self.name = name
        self.host = host
        self.port = port
        self.kind = kind
        self.pairingState = pairingState
        self.serviceName = serviceName
        self.discoverySource = discoverySource
        self.sshConfig = sshConfig
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(UUID.self, forKey: .id)
        name = try container.decode(String.self, forKey: .name)
        host = try container.decode(String.self, forKey: .host)
        port = try container.decode(Int.self, forKey: .port)
        kind = try container.decodeIfPresent(ConnectionKind.self, forKey: .kind) ?? .device
        pairingState = try container.decode(PairingState.self, forKey: .pairingState)
        serviceName = try container.decodeIfPresent(String.self, forKey: .serviceName)
        discoverySource = try container.decode(DiscoverySource.self, forKey: .discoverySource)
        sshConfig = try container.decodeIfPresent(SSHConfig.self, forKey: .sshConfig)
    }
}
