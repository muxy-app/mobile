import Foundation
import Testing
@testable import Muxy

struct EnvelopeTests {
    private func encodeToObject(_ value: some Encodable) throws -> [String: Any] {
        let data = try JSONEncoder().encode(value)
        let object = try JSONSerialization.jsonObject(with: data)
        return try #require(object as? [String: Any])
    }

    @Test func requestEncodesEnvelopeAndTaggedParams() throws {
        let params = AuthParams(deviceID: "dev-1", deviceName: "iPhone", token: "secret")
        let request = RequestEnvelope(
            id: "req-1",
            method: Method.authenticateDevice.rawValue,
            params: TaggedPayload(type: Method.authenticateDevice.rawValue, value: params)
        )

        let root = try encodeToObject(request)
        #expect(root["type"] as? String == "request")

        let payload = try #require(root["payload"] as? [String: Any])
        #expect(payload["id"] as? String == "req-1")
        #expect(payload["method"] as? String == "authenticateDevice")

        let paramsObject = try #require(payload["params"] as? [String: Any])
        #expect(paramsObject["type"] as? String == "authenticateDevice")

        let value = try #require(paramsObject["value"] as? [String: Any])
        #expect(value["deviceID"] as? String == "dev-1")
        #expect(value["deviceName"] as? String == "iPhone")
        #expect(value["token"] as? String == "secret")
    }

    @Test func requestWithoutParamsEncodesNull() throws {
        let request = RequestEnvelope<EmptyParams>(
            id: "req-2",
            method: Method.listProjects.rawValue,
            params: nil
        )

        let data = try JSONEncoder().encode(request)
        let string = try #require(String(data: data, encoding: .utf8))
        #expect(string.contains("\"params\":null"))

        let root = try encodeToObject(request)
        let payload = try #require(root["payload"] as? [String: Any])
        #expect(payload.keys.contains("params"))
        #expect(payload["params"] is NSNull)
    }

    @Test func responseWithResultDecodes() throws {
        let json = """
        {
          "type": "response",
          "payload": {
            "id": "req-1",
            "result": {
              "type": "pairing",
              "value": {
                "clientID": "client-9",
                "deviceName": "iPhone",
                "themeFg": 16777215,
                "themeBg": 197379,
                "themePalette": [0, 16711680, 65280]
              }
            }
          }
        }
        """.data(using: .utf8)!

        let frame = try IncomingFrame(json: json)
        guard case let .response(response) = frame else {
            Issue.record("Expected response frame")
            return
        }

        #expect(response.id == "req-1")
        #expect(response.error == nil)

        let result = try #require(response.result)
        #expect(result.type == ResultType.pairing)

        let pairing = try result.decode(PairingResult.self)
        #expect(pairing.clientID == "client-9")
        #expect(pairing.deviceName == "iPhone")
        #expect(pairing.themeFg == 16777215)
        #expect(pairing.themeBg == 197379)
        #expect(pairing.themePalette == [0, 16711680, 65280])
    }

    @Test func pairingResultWithoutThemeDecodes() throws {
        let json = """
        { "type": "response", "payload": { "id": "x", "result": { "type": "pairing",
          "value": { "clientID": "c", "deviceName": "d" } } } }
        """.data(using: .utf8)!

        let frame = try IncomingFrame(json: json)
        guard case let .response(response) = frame else {
            Issue.record("Expected response frame")
            return
        }
        let pairing = try #require(response.result).decode(PairingResult.self)
        #expect(pairing.themeFg == nil)
        #expect(pairing.themeBg == nil)
        #expect(pairing.themePalette == nil)
    }

    @Test func responseWithErrorDecodes() throws {
        let json = """
        {
          "type": "response",
          "payload": {
            "id": "req-3",
            "error": { "code": 401, "message": "Authentication required" }
          }
        }
        """.data(using: .utf8)!

        let frame = try IncomingFrame(json: json)
        guard case let .response(response) = frame else {
            Issue.record("Expected response frame")
            return
        }

        #expect(response.result == nil)
        let error = try #require(response.error)
        #expect(error.code == 401)
        #expect(error.message == "Authentication required")
        #expect(ProtocolError(error).code == .unauthorized)
    }

    @Test func eventFrameDecodes() throws {
        let json = """
        {
          "type": "event",
          "payload": {
            "event": "projectsChanged",
            "data": { "type": "projects", "value": { "projects": [] } }
          }
        }
        """.data(using: .utf8)!

        let frame = try IncomingFrame(json: json)
        guard case let .event(event) = frame else {
            Issue.record("Expected event frame")
            return
        }
        #expect(event.event == "projectsChanged")
        #expect(event.data?.type == "projects")
    }

    @Test func unknownFrameTypeThrows() {
        let json = """
        { "type": "telemetry", "payload": {} }
        """.data(using: .utf8)!

        #expect(throws: FrameError.unknownType("telemetry")) {
            try IncomingFrame(json: json)
        }
    }

    @Test func jsonValueRoundTrips() throws {
        let original: JSONValue = .object([
            "name": .string("muxy"),
            "count": .number(3),
            "enabled": .bool(true),
            "missing": .null,
            "tags": .array([.string("a"), .string("b")]),
            "nested": .object(["deep": .number(1)]),
        ])

        let data = try JSONEncoder().encode(original)
        let decoded = try JSONDecoder().decode(JSONValue.self, from: data)
        #expect(decoded == original)
    }
}

private struct EmptyParams: Codable, Sendable {}
