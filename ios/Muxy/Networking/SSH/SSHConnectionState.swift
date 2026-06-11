import Foundation

nonisolated enum SSHConnectionState: Equatable, Sendable {
    case idle
    case connecting
    case connected
    case disconnected
    case failed(SSHError)
}
