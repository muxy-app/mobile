import Foundation
import MuxyMobile

nonisolated final class SDKServerConnection: ServerConnection {
    private let connection: MuxyMobile.Connection
    private let lanes: SDKLanes
    private let onDisconnect: (@Sendable () -> Void)?

    init(connection: MuxyMobile.Connection, lanes: SDKLanes, onDisconnect: (@Sendable () -> Void)? = nil) {
        self.connection = connection
        self.lanes = lanes
        self.onDisconnect = onDisconnect
    }

    deinit {
        connection.disconnect()
        onDisconnect?()
    }

    func serverID() async throws -> String {
        try await lanes.request { [connection] in try connection.serverId() }
    }

    var serverVersion: String {
        connection.serverVersion()
    }

    func projects() async throws -> [ServerProject] {
        try await lanes.request { [connection] in
            try connection.projects()
        }
    }

    func sessions(projectId: String) async throws -> [Session] {
        try await lanes.request { [connection] in
            try connection.sessions(projectId: projectId)
        }
    }

    var supportsPushNotifications: Bool {
        connection.supportsPushNotifications()
    }

    func findSession(_ sessionId: UInt64) async throws -> Session? {
        try await lanes.request { [connection] in
            try connection.findSession(sessionId: sessionId)
        }
    }

    func registerPush(token: String, environment: PushEnvironment) async throws -> PushRegistration {
        try await lanes.pushRequest { [connection] in
            try connection.registerPush(platform: .apns, token: token, environment: environment)
        }
    }

    func unregisterPush() async throws {
        try await lanes.pushRequest { [connection] in
            try connection.unregisterPush()
        }
    }

    func createSession(projectId: String, size: TerminalGridSize) async throws -> Session {
        try await lanes.request { [connection] in
            try connection.createSession(projectId: projectId, columns: size.columns, rows: size.rows)
        }
    }

    func endSession(_ sessionId: UInt64) async throws {
        try await lanes.request { [connection] in
            try connection.endSession(sessionId: sessionId)
        }
    }

    func activity() async throws -> Activity {
        try await lanes.request { [connection] in
            try connection.activity()
        }
    }

    func acknowledgeActivity(eventIds: [UInt64]) async throws {
        try await lanes.request { [connection] in
            try connection.acknowledgeActivity(eventIds: eventIds)
        }
    }

    func attach(sessionId: UInt64, size: TerminalGridSize) async throws -> any ServerTerminalChannel {
        let terminal = try await lanes.request { [connection] in
            try connection.attach(sessionId: sessionId, columns: size.columns, rows: size.rows)
        }
        return SDKTerminalChannel(terminal: terminal, lanes: lanes)
    }

    func files(projectId: String) throws -> any ServerProjectFiles {
        SDKProjectFiles(files: try connection.files(projectId: projectId), lanes: lanes)
    }

    func git(projectId: String) throws -> any ServerGitRepository {
        SDKGitRepository(repository: try connection.git(projectId: projectId), lanes: lanes)
    }

    func disconnect() {
        connection.disconnect()
        onDisconnect?()
    }
}
