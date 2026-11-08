import Foundation

public struct PushMessage: Decodable, Sendable, Equatable {
    public static let supportedVersion = 1

    public let version: Int
    public let serverId: String
    public let projectId: String
    public let sessionId: UInt64
    public let eventId: UInt64
    public let kind: String
    public let title: String
    public let body: String
    public let timestamp: UInt64

    private enum CodingKeys: String, CodingKey {
        case version = "v"
        case serverId = "server_id"
        case projectId = "project_id"
        case sessionId = "session_id"
        case eventId = "event_id"
        case kind
        case title
        case body
        case timestamp = "ts"
    }

    public var target: PushTarget {
        PushTarget(serverId: serverId, sessionId: sessionId, projectId: projectId, eventId: eventId)
    }
}
