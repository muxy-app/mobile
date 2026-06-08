import Foundation
import Testing
@testable import Muxy

@MainActor
struct TerminalSessionTests {
    @Test func bootstrapTakeoverSendsTakeOverPane() async throws {
        let channel = MockTerminalChannel()
        let paneID = UUID()
        let session = TerminalSession(paneID: paneID, channel: channel)

        session.activate()
        session.bootstrapTakeover(cols: 80, rows: 24)

        try await channel.waitForRequest(.takeOverPane)
        let recorded = await channel.requests
        let takeover = try #require(recorded.first { $0.method == .takeOverPane })
        let params = try #require(takeover.params as? TakeOverPaneParams)
        #expect(params.paneID == paneID.uuidString)
        #expect(params.cols == 80)
        #expect(params.rows == 24)
    }

    @Test func activateSendsClientTheme() async throws {
        let channel = MockTerminalChannel()
        let session = TerminalSession(paneID: UUID(), channel: channel)

        session.activate()

        try await channel.waitForRequest(.setClientTheme)
        let recorded = await channel.requests
        let themeCall = try #require(recorded.first { $0.method == .setClientTheme })
        let params = try #require(themeCall.params as? SetClientThemeParams)
        #expect(params.theme == .dark)
    }

    @Test func clientThemeFailureDoesNotBlockTakeover() async throws {
        let channel = MockTerminalChannel()
        await channel.setRequestError(
            ProtocolError(ProtocolErrorBody(code: 404, message: "Unknown method")),
            for: .setClientTheme
        )
        let session = TerminalSession(paneID: UUID(), channel: channel)

        session.activate()
        session.bootstrapTakeover(cols: 80, rows: 24)

        try await channel.waitForRequest(.setClientTheme)
        try await channel.waitForRequest(.takeOverPane)
    }

    @Test func reconnectResendsClientTheme() async throws {
        let channel = MockTerminalChannel()
        let session = TerminalSession(paneID: UUID(), channel: channel)

        session.activate()
        try await channel.waitForRequest(.setClientTheme)

        session.handleConnectionState(.disconnected)
        session.handleConnectionState(.connected)
        try await Task.sleep(for: .milliseconds(50))

        let themeRequests = await channel.requests.filter { $0.method == .setClientTheme }
        #expect(themeRequests.count == 2)
    }

    @Test func bootstrapHappensOnlyOnce() async throws {
        let channel = MockTerminalChannel()
        let session = TerminalSession(paneID: UUID(), channel: channel)

        session.activate()
        session.bootstrapTakeover(cols: 80, rows: 24)
        try await channel.waitForRequest(.takeOverPane)
        session.bootstrapTakeover(cols: 80, rows: 24)
        try await Task.sleep(for: .milliseconds(50))

        let takeovers = await channel.requests.filter { $0.method == .takeOverPane }
        #expect(takeovers.count == 1)
    }

    @Test func deactivateReleasesPane() async throws {
        let channel = MockTerminalChannel()
        let paneID = UUID()
        let session = TerminalSession(paneID: paneID, channel: channel)

        session.activate()
        session.deactivate()

        try await channel.waitForNotify(.releasePane)
        let releases = await channel.notifications.filter { $0.method == .releasePane }
        let params = try #require(releases.first?.params as? ReleasePaneParams)
        #expect(params.paneID == paneID.uuidString)
    }

    @Test func sendBytesAppliesControlChordOnce() async throws {
        let channel = MockTerminalChannel()
        let session = TerminalSession(paneID: UUID(), channel: channel)

        session.setModifierArmed(true)
        #expect(session.modifierArmed)
        session.sendBytes([0x63][...])

        try await channel.waitForNotify(.terminalInput)
        #expect(!session.modifierArmed)
        let inputs = await channel.notifications.filter { $0.method == .terminalInput }
        let params = try #require(inputs.first?.params as? TerminalInputParams)
        #expect(Array(params.bytes) == [0x03])
    }

    @Test func ownershipMatchingClientIDBecomesOwned() async {
        let clientID = UUID()
        let paneID = UUID()
        let channel = MockTerminalChannel()
        await channel.setClientID(clientID)
        let session = TerminalSession(paneID: paneID, channel: channel)
        session.activate()
        await waitForClientID(channel)

        await channel.emitOwnership(paneID: paneID, owner: .remote(deviceID: clientID, deviceName: "Me"))
        await waitUntil { session.ownership == .owned }
        #expect(session.ownership == .owned)
    }

    @Test func ownershipWithDifferentRemoteIDBecomesControlledElsewhere() async {
        let paneID = UUID()
        let channel = MockTerminalChannel()
        await channel.setClientID(UUID())
        let session = TerminalSession(paneID: paneID, channel: channel)
        session.activate()
        await waitForClientID(channel)

        await channel.emitOwnership(paneID: paneID, owner: .remote(deviceID: UUID(), deviceName: "iPad"))
        await waitUntil { session.ownership == .controlledElsewhere(deviceName: "iPad") }
        #expect(session.ownership == .controlledElsewhere(deviceName: "iPad"))
    }

    @Test func macOwnershipBecomesControlledElsewhereOutsideGrace() async {
        let paneID = UUID()
        let channel = MockTerminalChannel()
        await channel.setClientID(UUID())
        let session = TerminalSession(paneID: paneID, channel: channel)
        session.activate()
        await waitForClientID(channel)

        await channel.emitOwnership(paneID: paneID, owner: .mac(deviceName: "MacBook"))
        await waitUntil { session.ownership == .controlledElsewhere(deviceName: "MacBook") }
        #expect(session.ownership == .controlledElsewhere(deviceName: "MacBook"))
    }

    @Test func ownershipForOtherPaneIsIgnored() async {
        let paneID = UUID()
        let channel = MockTerminalChannel()
        await channel.setClientID(UUID())
        let session = TerminalSession(paneID: paneID, channel: channel)
        session.activate()
        await waitForClientID(channel)

        await channel.emitOwnership(paneID: UUID(), owner: .mac(deviceName: "Other"))
        try? await Task.sleep(for: .milliseconds(50))
        #expect(session.ownership == .takingOver)
    }

    @Test func userScrollUpStopsFollowing() {
        let session = TerminalSession(paneID: UUID(), channel: MockTerminalChannel())
        #expect(session.isFollowingBottom)
        session.userScrolled(toPosition: 0.4)
        #expect(!session.isFollowingBottom)
        session.userScrolled(toPosition: 1.0)
        #expect(session.isFollowingBottom)
    }

    @Test func disconnectMarksDisconnected() {
        let session = TerminalSession(paneID: UUID(), channel: MockTerminalChannel())
        session.activate()
        session.handleConnectionState(.disconnected)
        #expect(session.ownership == .disconnected)
    }

    private func waitForClientID(_ channel: MockTerminalChannel) async {
        try? await Task.sleep(for: .milliseconds(30))
    }

    private func waitUntil(_ condition: @escaping () -> Bool) async {
        for _ in 0..<100 {
            if condition() { return }
            try? await Task.sleep(for: .milliseconds(10))
        }
    }
}
