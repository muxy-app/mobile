import Foundation

nonisolated enum KeychainError: Error, Equatable, Sendable {
    case unexpectedStatus(OSStatus)
    case encodingFailed
}

protocol KeychainStore: Sendable {
    func setToken(_ token: String, for deviceID: Device.ID) throws
    func token(for deviceID: Device.ID) throws -> String?
    func deleteToken(for deviceID: Device.ID) throws
}
