import Foundation

nonisolated enum ConnectionError: Error, Equatable, Sendable {
    case connectionFailed
    case authenticationFailed
    case invalidEndpoint
    case missingToken
    case notConnected
}

nonisolated enum ConnectionState: Equatable, Sendable {
    case idle
    case connecting
    case authenticating
    case connected
    case disconnected
    case failed(ConnectionError)
}
