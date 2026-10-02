import Foundation
import Testing
@testable import Muxy

struct MuxyClientTests {
    private func requestID(from frame: String) -> String? {
        guard
            let data = frame.data(using: .utf8),
            let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let payload = object["payload"] as? [String: Any]
        else { return nil }
        return payload["id"] as? String
    }

    private func pairingResponse(id: String) -> String {
        """
        { "type": "response", "payload": { "id": "\(id)", "result": { "type": "pairing",
          "value": { "clientID": "c-1", "deviceName": "iPhone" } } } }
        """
    }

    private func errorResponse(id: String, code: Int, message: String) -> String {
        """
        { "type": "response", "payload": { "id": "\(id)", "error": { "code": \(code), "message": "\(message)" } } }
        """
    }

    @Test func resolvesMatchingResponse() async throws {
        let transport = MockTransport { [self] frame in
            guard let id = requestID(from: frame) else { return [] }
            return [pairingResponse(id: id)]
        }
        let client = MuxyClient(transport: transport)
        await client.start()

        let params = AuthParams(deviceID: "d", deviceName: "iPhone", token: "t")
        let result = try await client.request(.authenticateDevice, params: params)

        #expect(result.type == ResultType.pairing)
        let pairing = try result.decode(PairingResult.self)
        #expect(pairing.clientID == "c-1")
        await client.stop()
    }

    @Test func resolvesOkResponseWithoutValue() async throws {
        let transport = MockTransport { [self] frame in
            guard let id = requestID(from: frame) else { return [] }
            return ["""
            { "type": "response", "payload": { "id": "\(id)", "result": { "type": "ok" } } }
            """]
        }
        let client = MuxyClient(transport: transport, requestTimeout: .milliseconds(500))
        await client.start()

        let params = SelectProjectParams(projectID: "p")
        let result = try await client.request(.selectProject, params: params)

        #expect(result.type == ResultType.ok)
        await client.stop()
    }

    @Test func errorResponseThrowsProtocolError() async throws {
        let transport = MockTransport { [self] frame in
            guard let id = requestID(from: frame) else { return [] }
            return [errorResponse(id: id, code: 403, message: "Pairing denied")]
        }
        let client = MuxyClient(transport: transport)
        await client.start()

        let params = AuthParams(deviceID: "d", deviceName: "iPhone", token: "t")
        await #expect(throws: ProtocolError(ProtocolErrorBody(code: 403, message: "Pairing denied"))) {
            try await client.request(.authenticateDevice, params: params)
        }
        await client.stop()
    }

    @Test func mismatchedIDDoesNotResolveAndTimesOut() async throws {
        let transport = MockTransport { _ in
            ["""
            { "type": "response", "payload": { "id": "other", "result": { "type": "pairing",
              "value": { "clientID": "x", "deviceName": "y" } } } }
            """]
        }
        let client = MuxyClient(transport: transport, requestTimeout: .milliseconds(200))
        await client.start()

        let params = AuthParams(deviceID: "d", deviceName: "iPhone", token: "t")
        await #expect(throws: TransportError.closed) {
            try await client.request(.authenticateDevice, params: params)
        }
        await client.stop()
    }

    @Test func transportCloseFailsOutstandingRequest() async throws {
        let transport = MockTransport()
        let client = MuxyClient(transport: transport, requestTimeout: .seconds(10))
        await client.start()

        let pending = Task {
            try await client.request(.authenticateDevice, params: AuthParams(deviceID: "d", deviceName: "n", token: "t"))
        }

        try await Task.sleep(for: .milliseconds(50))
        await transport.failReaders()

        await #expect(throws: TransportError.closed) {
            _ = try await pending.value
        }
        await client.stop()
    }

    @Test func eventsAreSurfaced() async throws {
        let transport = MockTransport()
        let client = MuxyClient(transport: transport)
        await client.start()

        await transport.enqueue("""
        { "type": "event", "payload": { "event": "projectsChanged",
          "data": { "type": "projects", "value": {} } } }
        """)

        var iterator = client.events.makeAsyncIterator()
        let event = await iterator.next()
        #expect(event?.event == "projectsChanged")
        await client.stop()
    }
}
