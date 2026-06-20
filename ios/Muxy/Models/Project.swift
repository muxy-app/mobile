import Foundation

nonisolated struct Project: Codable, Sendable, Identifiable, Equatable, Hashable {
    let id: UUID
    let name: String
    let path: String
    let sortOrder: Double
    let createdAt: String
    let icon: String?
    let logo: String?
    let iconColor: String?
    let preferredWorktreeParentPath: String?
    let worktreesEnabled: Bool?
    let workspaceKind: String?
    let workspaceID: UUID?
    let workspaceName: String?
}
