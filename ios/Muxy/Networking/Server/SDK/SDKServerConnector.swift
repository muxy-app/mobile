import Foundation
import MuxyMobile

nonisolated struct SDKServerConnector: ServerConnector {
    func connect(
        to credential: ServerCredential,
        events: @escaping @Sendable (ConnectionEvent) -> Void
    ) async throws -> any ServerConnection {
        let lanes = SDKLanes(label: "app.muxy.sdk.\(credential.serverId)")
        let listener = ConnectionEventRelay(handler: events)
        let connection = try await lanes.request {
            try MuxyMobile.Connection.connect(credential: credential, listener: listener)
        }
        return SDKServerConnection(connection: connection, lanes: lanes)
    }
}
