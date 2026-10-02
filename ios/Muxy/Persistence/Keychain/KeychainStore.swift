import Foundation

nonisolated enum KeychainError: Error, Equatable, Sendable {
    case unexpectedStatus(OSStatus)
    case encodingFailed
}

nonisolated enum KeychainSecret: String, CaseIterable, Sendable {
    case token
    case sshPassword
    case sshPrivateKey
    case sshPassphrase
    case sshHostKey
}

protocol KeychainStore: Sendable {
    func setSecret(_ value: String, _ secret: KeychainSecret, for connectionID: Connection.ID) throws
    func secret(_ secret: KeychainSecret, for connectionID: Connection.ID) throws -> String?
    func deleteSecrets(for connectionID: Connection.ID) throws
}

extension KeychainStore {
    func setToken(_ token: String, for connectionID: Connection.ID) throws {
        try setSecret(token, .token, for: connectionID)
    }

    func token(for connectionID: Connection.ID) throws -> String? {
        try secret(.token, for: connectionID)
    }

    func deleteToken(for connectionID: Connection.ID) throws {
        try deleteSecrets(for: connectionID)
    }
}
