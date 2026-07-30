import Foundation
import Testing
@testable import Muxy

struct TerminalProtocolTests {
    private func event(from json: String) throws -> EventEnvelope {
        let frame = try IncomingFrame(json: Data(json.utf8))
        guard case let .event(event) = frame else {
            throw TerminalProtocolTestError.notEvent
        }
        return event
    }

    @Test func terminalOutputEventDecodesBase64Bytes() throws {
        let paneID = UUID()
        let bytes: [UInt8] = [0x1b, 0x5b, 0x33, 0x31, 0x6d]
        let base64 = Data(bytes).base64EncodedString()
        let event = try event(from: """
        { "type": "event", "payload": { "event": "terminalOutput",
          "data": { "type": "terminalOutput", "value": { "paneID": "\(paneID.uuidString)", "bytes": "\(base64)" } } } }
        """)

        #expect(event.event == EventName.terminalOutput)
        let data = try #require(event.data)
        #expect(data.type == EventType.terminalOutput)
        let payload = try data.decode(TerminalBytesEvent.self)
        #expect(payload.paneID == paneID)
        #expect(Array(payload.bytes) == bytes)
    }

    @Test func snapshotSharesBytesShape() throws {
        let paneID = UUID()
        let base64 = Data([0x41, 0x42]).base64EncodedString()
        let event = try event(from: """
        { "type": "event", "payload": { "event": "terminalSnapshot",
          "data": { "type": "terminalSnapshot", "value": { "paneID": "\(paneID.uuidString)", "bytes": "\(base64)" } } } }
        """)
        let payload = try #require(event.data).decode(TerminalBytesEvent.self)
        #expect(payload.paneID == paneID)
        #expect(Array(payload.bytes) == [0x41, 0x42])
    }

    @Test func paneOwnershipRemoteDecodes() throws {
        let paneID = UUID()
        let deviceID = UUID()
        let event = try event(from: """
        { "type": "event", "payload": { "event": "paneOwnershipChanged",
          "data": { "type": "paneOwnership", "value": { "paneID": "\(paneID.uuidString)",
          "owner": { "remote": { "deviceID": "\(deviceID.uuidString)", "deviceName": "iPad" } } } } } }
        """)

        let payload = try #require(event.data).decode(PaneOwnershipEvent.self)
        #expect(payload.paneID == paneID)
        #expect(payload.owner == .remote(deviceID: deviceID, deviceName: "iPad"))
    }

    @Test func paneOwnershipMacDecodes() throws {
        let paneID = UUID()
        let event = try event(from: """
        { "type": "event", "payload": { "event": "paneOwnershipChanged",
          "data": { "type": "paneOwnership", "value": { "paneID": "\(paneID.uuidString)",
          "owner": { "mac": { "deviceName": "MacBook" } } } } } }
        """)

        let payload = try #require(event.data).decode(PaneOwnershipEvent.self)
        #expect(payload.owner == .mac(deviceName: "MacBook"))
    }

    @Test func deviceThemeDecodes() throws {
        let event = try event(from: """
        { "type": "event", "payload": { "event": "themeChanged",
          "data": { "type": "deviceTheme", "value": { "fg": 16777215, "bg": 197379, "palette": [0, 16711680] } } } }
        """)

        #expect(event.data?.type == EventType.deviceTheme)
        let payload = try #require(event.data).decode(DeviceThemeEvent.self)
        #expect(payload.fg == 16777215)
        #expect(payload.bg == 197379)
        #expect(payload.palette == [0, 16711680])
    }

    @Test func terminalInputParamsEncodeBytesAsBase64() throws {
        let params = TerminalInputParams(paneID: "pane-1", bytes: Data([0x03]))
        let object = try JSONSerialization.jsonObject(with: JSONEncoder().encode(params)) as? [String: Any]
        #expect(object?["paneID"] as? String == "pane-1")
        #expect(object?["bytes"] as? String == Data([0x03]).base64EncodedString())
    }

    @Test func takeOverPaneParamsEncodeColsRows() throws {
        let params = TakeOverPaneParams(paneID: "pane-1", cols: 120, rows: 40)
        let object = try JSONSerialization.jsonObject(with: JSONEncoder().encode(params)) as? [String: Any]
        #expect(object?["cols"] as? Int == 120)
        #expect(object?["rows"] as? Int == 40)
    }

    @Test func setClientThemeParamsEncodeTheme() throws {
        let params = SetClientThemeParams(theme: .dark)
        let object = try JSONSerialization.jsonObject(with: JSONEncoder().encode(params)) as? [String: Any]
        let theme = try #require(object?["theme"] as? [String: Any])
        let palette = try #require(theme["palette"] as? [Int])
        #expect(theme["fg"] as? Int == 0xF2F2F7)
        #expect(theme["bg"] as? Int == 0x000000)
        #expect(palette.count == 16)
        #expect(theme["cursorColor"] as? Int == 0xF2F2F7)
        #expect(theme["selectionForeground"] as? Int == 0xFFFFFF)
    }
}

private enum TerminalProtocolTestError: Error {
    case notEvent
}
