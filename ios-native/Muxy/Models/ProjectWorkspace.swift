import Foundation

nonisolated struct ProjectWorkspace: Identifiable, Sendable, Equatable, Hashable {
    let id: UUID
    let name: String
}
