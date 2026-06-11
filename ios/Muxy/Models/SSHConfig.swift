import Foundation

nonisolated enum SSHAuthMethod: String, Codable, Sendable {
    case password
    case privateKey
}

nonisolated struct SSHConfig: Codable, Sendable, Equatable, Hashable {
    var username: String
    var authMethod: SSHAuthMethod
}
