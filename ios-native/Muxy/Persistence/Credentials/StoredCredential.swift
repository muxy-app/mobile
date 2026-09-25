import Foundation
import MuxyMobile

nonisolated struct StoredCredential: Codable, Sendable {
    let serverId: String
    let serverName: String
    let hosts: [String]
    let port: UInt16
    let fingerprint: Data
    let deviceId: String
    let token: Data

    init(_ credential: ServerCredential) {
        serverId = credential.serverId
        serverName = credential.serverName
        hosts = credential.hosts
        port = credential.port
        fingerprint = credential.fingerprint
        deviceId = credential.deviceId
        token = credential.token
    }

    var credential: ServerCredential {
        ServerCredential(
            serverId: serverId,
            serverName: serverName,
            hosts: hosts,
            port: port,
            fingerprint: fingerprint,
            deviceId: deviceId,
            token: token
        )
    }
}
