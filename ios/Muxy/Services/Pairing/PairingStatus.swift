import Foundation

nonisolated enum PairingStatus: Equatable, Sendable {
    case idle
    case connecting
    case authenticating
    case awaitingApproval
    case paired(Pairing)
    case failed(PairingError)
}
