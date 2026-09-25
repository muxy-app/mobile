import Foundation
import MuxyMobile

typealias ServerProject = MuxyMobile.Project

nonisolated struct TerminalGridSize: Hashable, Sendable {
    let columns: UInt16
    let rows: UInt16
}

nonisolated protocol ServerConnector: Sendable {
    func connect(
        to credential: ServerCredential,
        events: @escaping @Sendable (ConnectionEvent) -> Void
    ) async throws -> any ServerConnection
}

nonisolated protocol ServerConnection: AnyObject, Sendable {
    var serverVersion: String { get }
    func projects() async throws -> [ServerProject]
    func sessions(projectId: String) async throws -> [Session]
    func createSession(projectId: String, size: TerminalGridSize) async throws -> Session
    func endSession(_ sessionId: UInt64) async throws
    func attach(sessionId: UInt64, size: TerminalGridSize) async throws -> any ServerTerminalChannel
    func disconnect()
}

nonisolated protocol ServerTerminalChannel: AnyObject, Sendable {
    var sessionId: UInt64 { get }
    func screen() -> Screen
    func send(text: String)
    func send(key: Key, modifiers: Modifiers)
    func paste(_ text: String)
    func resize(to size: TerminalGridSize) async throws
    func scrollback(maxRows: UInt16) async throws -> any ScrollbackSnapshot
    func detach() async throws
}

nonisolated protocol ScrollbackSnapshot: AnyObject, Sendable {
    var historyRows: UInt64 { get }
    func lines() -> [Line]
    func loadOlder(maxRows: UInt16) async throws -> [Line]
}

nonisolated protocol ServerPairingService: Sendable {
    func parse(_ link: String) throws -> PairingLink
    func pair(link: String, deviceName: String) async throws -> ServerCredential
}
