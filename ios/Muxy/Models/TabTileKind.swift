import Foundation

nonisolated enum TabTileKind: Equatable, Sendable {
    case agent
    case shell
    case git
    case other
}
