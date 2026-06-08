import Foundation
import Testing
@testable import Muxy

struct ConnectionManagerRequestTests {
    private static func method(from frame: String) -> String {
        guard
            let data = frame.data(using: .utf8),
            let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let payload = object["payload"] as? [String: Any]
        else { return "" }
        return payload["method"] as? String ?? ""
    }

    private static func id(from frame: String) -> String {
        guard
            let data = frame.data(using: .utf8),
            let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let payload = object["payload"] as? [String: Any]
        else { return "" }
        return payload["id"] as? String ?? ""
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
                { "id": "11111111-1111-1111-1111-111111111111", "name": "muxy", "path": "/p", "sortOrder": 0, "createdAt": "2026-04-19T10:00:00Z" }
              ] } } } }
            """]
        default:
            return []
        }
    }

    private func device() -> Device {
        Device(
            id: UUID(),
            name: "Studio",
            host: "studio.local",
            port: 4865,
            pairingState: .paired,
            serviceName: nil,
            discoverySource: .manual
        )
    }

    @Test func forwardsRequestAndDecodesResult() async throws {
        let manager = ConnectionManager(makeTransport: { _ in MockTransport(autoReply: ConnectionManagerRequestTests.reply) })

        await manager.connect(to: device(), token: "t")
        let result = try await manager.request(.listProjects)

        #expect(result.type == ResultType.projects)
        let projects = try result.decode(ProjectsResult.self).projects
        #expect(projects.count == 1)
        #expect(projects[0].name == "muxy")
    }

    @Test func requestThrowsWhenNotConnected() async {
        let manager = ConnectionManager(makeTransport: { _ in MockTransport() })

        await #expect(throws: ConnectionError.notConnected) {
            _ = try await manager.request(.listProjects)
        }
    }
}
