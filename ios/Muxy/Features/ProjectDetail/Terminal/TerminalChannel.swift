import Foundation

protocol TerminalChannel: Sendable {
    func request<P: Codable & Sendable>(_ method: Method, params: P?) async throws -> RawTagged
    func notify<P: Codable & Sendable>(_ method: Method, params: P?) async throws
    func events() async -> AsyncStream<EventEnvelope>
    var currentTheme: DeviceThemeEvent? { get async }
    var currentClientID: UUID? { get async }
}

extension ConnectionManager: TerminalChannel {}
