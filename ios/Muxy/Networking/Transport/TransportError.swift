import Foundation

nonisolated enum TransportError: Error, Equatable, Sendable {
    case invalidURL
    case notConnected
    case closed
    case unsupportedFrame
}
