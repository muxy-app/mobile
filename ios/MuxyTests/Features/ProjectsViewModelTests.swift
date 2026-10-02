import Foundation
import Testing
@testable import Muxy

@MainActor
struct ProjectsViewModelTests {
    private static func id(from frame: String) -> String {
        guard
            let data = frame.data(using: .utf8),
            let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let payload = object["payload"] as? [String: Any]
        else { return "" }
        return payload["id"] as? String ?? ""
    }

    private static func method(from frame: String) -> String {
        guard
            let data = frame.data(using: .utf8),
            let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let payload = object["payload"] as? [String: Any]
        else { return "" }
        return payload["method"] as? String ?? ""
    }

    private static func reply(_ frame: String) -> [String] {
        let id = id(from: frame)
        switch method(from: frame) {
        case Method.authenticateDevice.rawValue:
            return ["""
            { "type": "response", "payload": { "id": "\(id)", "result": { "type": "pairing",
              "value": { "clientID": "c", "deviceName": "iPhone" } } } }
            """]
        case Method.listProjects.rawValue:
            return ["""
            { "type": "response", "payload": { "id": "\(id)", "result": { "type": "projects",
              "value": { "projects": [
                { "id": "22222222-2222-2222-2222-222222222222", "name": "beta", "path": "/b", "sortOrder": 1, "createdAt": "2026-04-19T10:00:00Z" },
                { "id": "11111111-1111-1111-1111-111111111111", "name": "alpha", "path": "/a", "sortOrder": 0, "createdAt": "2026-04-19T10:00:00Z" }
              ] } } } }
            """]
        default:
            return []
        }
    }

    private func device() -> Connection {
        Connection(
            id: UUID(),
            name: "Studio",
            host: "studio.local",
            port: 4865,
            pairingState: .paired,
            serviceName: nil,
            discoverySource: .manual
        )
    }

    @Test func loadsProjectsSortedAfterConnect() async throws {
        let device = device()
        let keychain = InMemoryKeychainStore()
        try keychain.setToken("token", for: device.id)
        let manager = ConnectionManager(makeTransport: { _ in MockTransport(autoReply: ProjectsViewModelTests.reply) })
        let viewModel = ProjectsViewModel(connection: device, keychain: keychain, connectionManager: manager)

        await viewModel.connect()
        try await waitUntil { !viewModel.projects.isEmpty }

        #expect(viewModel.projects.map(\.name) == ["alpha", "beta"])
        await viewModel.disconnect()
    }

    @Test func reportsMissingTokenWithoutCredentials() async {
        let device = device()
        let manager = ConnectionManager(makeTransport: { _ in MockTransport() })
        let viewModel = ProjectsViewModel(connection: device, keychain: InMemoryKeychainStore(), connectionManager: manager)

        await viewModel.connect()

        #expect(viewModel.state == .failed(.missingToken))
        await viewModel.disconnect()
    }

    private func waitUntil(timeout: Duration = .seconds(2), _ condition: @MainActor () -> Bool) async throws {
        let deadline = ContinuousClock.now + timeout
        while ContinuousClock.now < deadline {
            if condition() { return }
            try await Task.sleep(for: .milliseconds(20))
        }
        Issue.record("Condition not met within \(timeout)")
    }
}
