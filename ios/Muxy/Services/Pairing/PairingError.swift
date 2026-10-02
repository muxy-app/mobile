import Foundation

nonisolated enum PairingError: Error, Equatable, Sendable {
    case connectionFailed
    case approvalDenied
    case approvalTimedOut
    case wrongToken
    case invalidResponse
    case server(code: Int, message: String)
}
