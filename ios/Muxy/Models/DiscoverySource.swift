import Foundation

nonisolated enum DiscoverySource: String, Codable, Sendable {
    case manual
    case qr
    case bonjour
}
