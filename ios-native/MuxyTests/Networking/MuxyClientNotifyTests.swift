import Foundation
import Testing
@testable import Muxy

struct MuxyClientNotifyTests {
    private func payloadObject(from frame: String) -> [String: Any]? {
        guard
            let data = frame.data(using: .utf8),
            let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { return nil }
        return object["payload"] as? [String: Any]
    }

    @Test func notifySendsRequestShapedFrame() async throws {
        let transport = MockTransport()
        let client = MuxyClient(transport: transport)
        await client.start()

        let params = TerminalInputParams(paneID: "pane-1", bytes: Data([0x03]))
        try await client.notify(.terminalInput, params: params)

        let frames = await transport.sentFrames
        #expect(frames.count == 1)

        let payload = try #require(payloadObject(from: frames[0]))
        #expect(payload["method"] as? String == "terminalInput")
        let paramsObject = try #require(payload["params"] as? [String: Any])
        #expect(paramsObject["type"] as? String == "terminalInput")
        let value = try #require(paramsObject["value"] as? [String: Any])
        #expect(value["paneID"] as? String == "pane-1")
        #expect(value["bytes"] as? String == Data([0x03]).base64EncodedString())

        await client.stop()
    }

    @Test func notifyReturnsWithoutWaitingForResponse() async throws {
        let transport = MockTransport()
        let client = MuxyClient(transport: transport, requestTimeout: .seconds(30))
        await client.start()

        let params = TerminalInputParams(paneID: "pane-1", bytes: Data([0x61]))

        try await withThrowingTaskGroup(of: Bool.self) { group in
            group.addTask {
                try await client.notify(.terminalInput, params: params)
                return true
            }
            group.addTask {
                try await Task.sleep(for: .seconds(2))
                return false
            }
            let first = try #require(await group.next())
            #expect(first == true)
            group.cancelAll()
        }

        await client.stop()
    }

    @Test func notifyDoesNotConsumeResponseSlotForLaterRequest() async throws {
        let transport = MockTransport { frame in
            guard
                let data = frame.data(using: .utf8),
                let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                let payload = object["payload"] as? [String: Any],
                let method = payload["method"] as? String,
                method == "selectProject",
                let id = payload["id"] as? String
            else { return [] }
            return ["""
            { "type": "response", "payload": { "id": "\(id)", "result": { "type": "ok" } } }
            """]
        }
        let client = MuxyClient(transport: transport, requestTimeout: .seconds(2))
        await client.start()

        try await client.notify(.terminalInput, params: TerminalInputParams(paneID: "p", bytes: Data([0x61])))
        let result = try await client.request(.selectProject, params: SelectProjectParams(projectID: "p"))
        #expect(result.type == ResultType.ok)

        await client.stop()
    }
}
