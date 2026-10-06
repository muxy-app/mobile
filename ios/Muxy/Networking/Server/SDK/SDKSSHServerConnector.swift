import Foundation
import MuxyMobile

nonisolated struct SDKSSHServerConnector: Sendable {
    let keychain: KeychainStore

    func connect(
        to connection: Connection,
        events: @escaping @Sendable (ConnectionEvent) -> Void
    ) async throws -> SDKServerConnection {
        let session = SSHBridgeSession()
        return try await withTaskCancellationHandler {
            do {
                let channel = try await session.open(connection: connection, keychain: keychain)
                let lanes = SDKLanes(label: "app.muxy.sdk.ssh.\(connection.id)")
                let listener = ConnectionEventRelay(handler: events)
                let host = "\(connection.sshConfig?.username ?? "")@\(connection.host)"
                let sdk = try await lanes.request {
                    try MuxyMobile.Connection.connectChannel(channel: channel, host: host, listener: listener)
                }
                guard !Task.isCancelled else {
                    sdk.disconnect()
                    throw CancellationError()
                }
                return SDKServerConnection(connection: sdk, lanes: lanes, onDisconnect: { session.close() })
            } catch {
                session.close()
                if let error = error as? MobileError, case let .Unreachable(reason) = error {
                    throw SSHBridgeFailure(reason: reason)
                }
                throw error
            }
        } onCancel: {
            session.close()
        }
    }
}

nonisolated struct SSHBridgeFailure: Error, Sendable {
    let reason: String
}
