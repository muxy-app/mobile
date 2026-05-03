import Foundation

struct NotificationDTO: Identifiable, Codable {
    let id: UUID
    let paneID: UUID
    let projectID: UUID
    let worktreeID: UUID
    let areaID: UUID
    let tabID: UUID
    let source: SourceDTO
    let title: String
    let body: String
    let timestamp: Date
    var isRead: Bool

    enum SourceDTO: Codable, Equatable {
        case osc
        case aiProvider(String)
        case socket
    }
}
