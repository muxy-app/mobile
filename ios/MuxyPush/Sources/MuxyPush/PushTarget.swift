import Foundation

public struct PushTarget: Hashable, Sendable {
    public let serverId: String
    public let sessionId: UInt64
    public let projectId: String
    public let eventId: UInt64

    public init(serverId: String, sessionId: UInt64, projectId: String, eventId: UInt64) {
        self.serverId = serverId
        self.sessionId = sessionId
        self.projectId = projectId
        self.eventId = eventId
    }
}
