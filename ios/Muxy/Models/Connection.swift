import Foundation

nonisolated enum ConnectionKind: String, Codable, Sendable {
    case device
    case server
    case ssh
}

nonisolated enum ServerTransport: String, Codable, Sendable {
    case paired
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
    var serverTransport: ServerTransport?
    var serverID: String?
    var authenticationDeviceID: String?

    var usesSSH: Bool {
        kind == .ssh || (kind == .server && serverTransport == .ssh)
    }

    var serverRouteID: String? {
        guard kind == .server else { return nil }
        return serverTransport == .ssh ? "ssh:\(id.uuidString)" : serverID
    }

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
        sshConfig: SSHConfig? = nil,
        serverID: String? = nil,
        serverTransport: ServerTransport? = nil,
        authenticationDeviceID: String? = nil
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
        self.serverTransport = serverTransport
        self.serverID = serverID
        self.authenticationDeviceID = authenticationDeviceID
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
        serverTransport = try container.decodeIfPresent(ServerTransport.self, forKey: .serverTransport)
        serverID = try container.decodeIfPresent(String.self, forKey: .serverID)
        authenticationDeviceID = try container.decodeIfPresent(String.self, forKey: .authenticationDeviceID)
    }
}
