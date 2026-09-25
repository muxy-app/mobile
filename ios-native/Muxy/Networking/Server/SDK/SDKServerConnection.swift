import Foundation
import MuxyMobile

nonisolated final class SDKServerConnection: ServerConnection {
    private let connection: MuxyMobile.Connection
    private let lanes: SDKLanes

    init(connection: MuxyMobile.Connection, lanes: SDKLanes) {
        self.connection = connection
        self.lanes = lanes
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

    func attach(sessionId: UInt64, size: TerminalGridSize) async throws -> any ServerTerminalChannel {
        let terminal = try await lanes.request { [connection] in
            try connection.attach(sessionId: sessionId, columns: size.columns, rows: size.rows)
        }
        return SDKTerminalChannel(terminal: terminal, lanes: lanes)
    }

    func disconnect() {
        connection.disconnect()
    }
}
